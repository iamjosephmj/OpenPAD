/**
 * @file openpad_jni.c
 * @brief JNI bridge between Kotlin (com.openpad.core.ndk.OpenPadNative) and C.
 *
 * This file contains all JNIEXPORT functions and manages the global pipeline
 * instance. Access to the pipeline is serialized via a pthread mutex because
 * CameraX analysis callbacks may arrive from different threads.
 *
 * ### Wire formats
 * - **Input**  (62510 bytes, little-endian): face geometry + downsampled
 *   frame + face crops + ML model scores. See OpadFrameInput in types.h.
 * - **Output** (128 bytes, little-endian): stabilized status + aggregate
 *   score + challenge state + all per-module results.
 * - **Config** (172 bytes, little-endian): 21 floats + padding + 11 ints.
 *
 * @see OpenPadNative.kt for the Kotlin-side serialization.
 */

#include <jni.h>
#include <pthread.h>
#include <string.h>
#include <android/log.h>
#include <openpad/pipeline.h>
#include <openpad/config.h>

#define TAG "PAD-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static OpadPipeline*  g_pipeline = NULL;
static pthread_mutex_t g_mutex   = PTHREAD_MUTEX_INITIALIZER;

#define INPUT_SIZE  62510
#define OUTPUT_SIZE 128

/* ---- Little-endian read/write helpers ---- */

static float read_f32(const uint8_t* p) {
    float v;
    memcpy(&v, p, 4);
    return v;
}

static void write_f32(uint8_t* buf, size_t off, float v) {
    memcpy(buf + off, &v, 4);
}

static void write_i32(uint8_t* buf, size_t off, int32_t v) {
    memcpy(buf + off, &v, 4);
}

/* ---- Input deserialization ---- */

static void parse_input(const uint8_t* data, OpadFrameInput* in) {
    memset(in, 0, sizeof(*in));
    in->has_face    = data[0] != 0;
    in->center_x    = read_f32(data + 2);
    in->center_y    = read_f32(data + 6);
    in->area        = read_f32(data + 10);
    in->confidence  = read_f32(data + 14);

    for (int i = 0; i < OPAD_SIMILARITY_SIZE * OPAD_SIMILARITY_SIZE; i++)
        in->frame_downsampled[i] = read_f32(data + 18 + i * 4);

    for (int i = 0; i < OPAD_FFT_SIZE * OPAD_FFT_SIZE; i++)
        in->face_crop_64_gray[i] = read_f32(data + 4114 + i * 4);

    memcpy(in->face_crop_64_argb, data + 20498, 64 * 64 * 4);
    memcpy(in->face_crop_80_argb, data + 36882, 80 * 80 * 4);

    in->texture_genuine = read_f32(data + 62482);
    in->has_mn3         = true;
    in->mn3_real        = read_f32(data + 62486);
    in->has_cdcn        = data[62490] != 0;
    in->cdcn            = read_f32(data + 62491);
    in->device_detected = data[62495] != 0;
    in->device_overlap  = data[62496] != 0;
    in->device_max_conf = read_f32(data + 62497);
    in->device_spoof    = read_f32(data + 62501);
}

/* ---- Output serialization ---- */

static void serialize_output(const OpadFrameOutput* out, uint8_t* buf) {
    memset(buf, 0, OUTPUT_SIZE);
    write_i32(buf,   0, (int32_t)out->pad_status);
    write_f32(buf,   4, out->aggregated_score);
    write_f32(buf,   8, out->frame_similarity);
    write_f32(buf,  12, out->face_sharpness);
    write_i32(buf,  16, (int32_t)out->challenge.phase);
    buf[20] = out->challenge.capture_checkpoint_1 ? 1 : 0;
    buf[21] = out->challenge.capture_checkpoint_2 ? 1 : 0;
    write_f32(buf,  22, out->frequency.moire_score);
    write_f32(buf,  26, out->frequency.peak_frequency);
    write_f32(buf,  30, out->frequency.spectral_flatness);
    write_f32(buf,  34, out->lbp.screen_score);
    write_f32(buf,  38, out->lbp.uniformity);
    write_f32(buf,  42, out->lbp.entropy);
    write_f32(buf,  46, out->lbp.channel_correlation);
    write_f32(buf,  50, out->photometric.specular_score);
    write_f32(buf,  54, out->photometric.chrominance_score);
    write_f32(buf,  58, out->photometric.edge_dof_score);
    write_f32(buf,  62, out->photometric.lighting_score);
    write_f32(buf,  66, out->photometric.combined_score);
    buf[70] = out->temporal.face_detected ? 1 : 0;
    write_f32(buf,  71, out->temporal.face_confidence);
    write_f32(buf,  75, out->temporal.face_bbox_center_x);
    write_f32(buf,  79, out->temporal.face_bbox_center_y);
    write_f32(buf,  83, out->temporal.face_bbox_area);
    write_f32(buf,  87, out->temporal.head_movement_variance);
    write_f32(buf,  91, out->temporal.face_size_stability);
    buf[95] = out->temporal.blink_detected ? 1 : 0;
    write_i32(buf,  96, out->temporal.frames_collected);
    write_f32(buf, 100, out->temporal.frame_similarity);
    write_i32(buf, 104, out->temporal.consecutive_face_frames);
    write_f32(buf, 108, out->temporal.movement_smoothness);
    write_f32(buf, 112, out->challenge.baseline_area);
    write_i32(buf, 116, out->challenge.total_frames);
    write_i32(buf, 120, out->challenge.hold_frames);
    write_f32(buf, 124, out->challenge.max_area_increase);
}

/* ---- JNI exports ---- */

JNIEXPORT void JNICALL
Java_com_openpad_core_ndk_OpenPadNative_nativeInit(
    JNIEnv* env, jclass clazz, jbyteArray configBytes) {
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, configBytes);
    jbyte* buf = (*env)->GetByteArrayElements(env, configBytes, NULL);
    if (!buf) return;

    OpadConfig config;
    opad_config_parse((const uint8_t*)buf, (size_t)len, &config);
    (*env)->ReleaseByteArrayElements(env, configBytes, buf, JNI_ABORT);

    pthread_mutex_lock(&g_mutex);
    if (g_pipeline) opad_pipeline_destroy(g_pipeline);
    g_pipeline = opad_pipeline_create(&config);
    pthread_mutex_unlock(&g_mutex);
    LOGD("Pipeline initialized");
}

JNIEXPORT void JNICALL
Java_com_openpad_core_ndk_OpenPadNative_nativeReset(
    JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    pthread_mutex_lock(&g_mutex);
    if (g_pipeline) opad_pipeline_reset(g_pipeline);
    pthread_mutex_unlock(&g_mutex);
}

JNIEXPORT void JNICALL
Java_com_openpad_core_ndk_OpenPadNative_nativeChallengeAdvanceToLive(
    JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    pthread_mutex_lock(&g_mutex);
    if (g_pipeline) opad_pipeline_challenge_advance_to_live(g_pipeline);
    pthread_mutex_unlock(&g_mutex);
}

JNIEXPORT jboolean JNICALL
Java_com_openpad_core_ndk_OpenPadNative_nativeChallengeHandleSpoof(
    JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    jboolean result = JNI_FALSE;
    pthread_mutex_lock(&g_mutex);
    if (g_pipeline && opad_pipeline_challenge_handle_spoof(g_pipeline))
        result = JNI_TRUE;
    pthread_mutex_unlock(&g_mutex);
    return result;
}

JNIEXPORT void JNICALL
Java_com_openpad_core_ndk_OpenPadNative_nativeChallengeAdvanceToDone(
    JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    pthread_mutex_lock(&g_mutex);
    if (g_pipeline) opad_pipeline_challenge_advance_to_done(g_pipeline);
    pthread_mutex_unlock(&g_mutex);
}

JNIEXPORT jbyteArray JNICALL
Java_com_openpad_core_ndk_OpenPadNative_nativeAnalyzeFrame(
    JNIEnv* env, jclass clazz, jbyteArray inputBytes) {
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, inputBytes);
    if (len < INPUT_SIZE) return NULL;

    jbyte* raw = (*env)->GetByteArrayElements(env, inputBytes, NULL);
    if (!raw) return NULL;

    OpadFrameInput frame_in;
    parse_input((const uint8_t*)raw, &frame_in);
    (*env)->ReleaseByteArrayElements(env, inputBytes, raw, JNI_ABORT);

    OpadFrameOutput frame_out;
    pthread_mutex_lock(&g_mutex);
    if (g_pipeline) {
        opad_pipeline_analyze_frame(g_pipeline, &frame_in, &frame_out);
    } else {
        memset(&frame_out, 0, sizeof(frame_out));
    }
    pthread_mutex_unlock(&g_mutex);

    uint8_t out_buf[OUTPUT_SIZE];
    serialize_output(&frame_out, out_buf);

    jbyteArray result = (*env)->NewByteArray(env, OUTPUT_SIZE);
    if (result)
        (*env)->SetByteArrayRegion(env, result, 0, OUTPUT_SIZE, (const jbyte*)out_buf);
    return result;
}
