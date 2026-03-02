/**
 * @file types.h
 * @brief Core type definitions for the OpenPAD native layer.
 *
 * This header defines every struct, enum, and constant shared across the
 * OpenPAD C library. All public symbols use the `Opad` prefix for structs
 * and `OPAD_` prefix for enums and macros to avoid symbol collisions.
 *
 * Memory layout:
 *   - All structs use natural alignment (no __packed__).
 *   - Booleans are C99 `_Bool` (stdbool.h).
 *   - Pixel buffers use ARGB byte order matching Android Bitmap_getPixels().
 *
 * @note This file is intentionally header-only with no implementation.
 */

#ifndef OPENPAD_TYPES_H
#define OPENPAD_TYPES_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

/* =========================================================================
 * Constants
 * ========================================================================= */

/** Side length of the downsampled grayscale frame used for similarity. */
#define OPAD_SIMILARITY_SIZE   32

/** Side length of the face crop used for sharpness (Laplacian). */
#define OPAD_SHARPNESS_SIZE    64

/** Side length of the face crop used for FFT moiré detection. */
#define OPAD_FFT_SIZE          64

/** Side length of the face crop used for LBP screen detection. */
#define OPAD_LBP_CROP_SIZE     64

/** Side length of the face crop used for photometric analysis. */
#define OPAD_PHOTO_CROP_SIZE   80

/** Maximum capacity for the temporal ring buffer. */
#define OPAD_MAX_WINDOW_SIZE   64

/* =========================================================================
 * Enums
 * ========================================================================= */

/**
 * Pipeline-level PAD status emitted per frame after stabilization.
 *
 * ANALYZING → LIVE or SPOOF_SUSPECTED, with NO_FACE as a transient state.
 * COMPLETED is set externally after the full challenge-response flow finishes.
 */
typedef enum {
    OPAD_STATUS_ANALYZING       = 0,
    OPAD_STATUS_NO_FACE         = 1,
    OPAD_STATUS_LIVE            = 2,
    OPAD_STATUS_SPOOF_SUSPECTED = 3,
    OPAD_STATUS_COMPLETED       = 4
} OpadPadStatus;

/**
 * Challenge-response state machine phases.
 *
 * IDLE → ANALYZING → CHALLENGE_CLOSER → EVALUATING → LIVE → DONE
 * POSITIONING is reserved for future guided-alignment flows.
 */
typedef enum {
    OPAD_PHASE_IDLE             = 0,
    OPAD_PHASE_ANALYZING        = 1,
    OPAD_PHASE_POSITIONING      = 2,
    OPAD_PHASE_CHALLENGE_CLOSER = 3,
    OPAD_PHASE_EVALUATING       = 4,
    OPAD_PHASE_LIVE             = 5,
    OPAD_PHASE_DONE             = 6
} OpadChallengePhase;

/* =========================================================================
 * Configuration
 * ========================================================================= */

/**
 * Full pipeline configuration. Deserialized from a 172-byte little-endian
 * buffer sent from the Kotlin layer via JNI.
 *
 * Wire layout: 21 x float32 (bytes 0-83), 44 bytes padding (84-127),
 *              11 x int32   (bytes 128-171).
 *
 * @see opad_config_parse(), opad_config_default()
 */
typedef struct {
    /* — Float thresholds (bytes 0-83 in wire format) — */
    float min_face_confidence;
    float texture_genuine_threshold;
    float positioning_min_face_area;
    float positioning_max_face_area;
    float positioning_center_tolerance;
    float challenge_closer_min_increase;
    float challenge_center_tolerance;
    float mn3_gate_threshold;
    float depth_flatness_threshold;
    float device_confidence_threshold;
    float moire_threshold;
    float lbp_screen_threshold;
    float photometric_min_score;
    float texture_weight;
    float mn3_weight;
    float cdcn_weight;
    float device_weight;
    float genuine_probability_threshold;
    float spoof_attempt_penalty_per_count;
    float max_genuine_probability_threshold;
    float face_consistency_threshold;

    /* — Integer parameters (bytes 128-171 in wire format) — */
    int32_t min_frames_for_decision;
    int32_t sliding_window_size;
    int32_t min_consecutive_face_frames;
    int32_t enter_consecutive;
    int32_t exit_consecutive;
    int32_t positioning_stable_frames;
    int32_t challenge_stable_frames;
    int32_t challenge_min_frames;
    int32_t analyzing_stable_frames;
    int32_t max_spoof_attempts;
    int32_t max_fps;
} OpadConfig;

/* =========================================================================
 * Face detection (input from Kotlin)
 * ========================================================================= */

/** Normalized face bounding-box center + area, received from MediaPipe. */
typedef struct {
    float center_x;   /**< Horizontal center [0,1], 0=left. */
    float center_y;   /**< Vertical center [0,1], 0=top.    */
    float area;        /**< Fraction of frame area [0,1].    */
    float confidence;  /**< Detection confidence [0,1].      */
} OpadFaceDetection;

/* =========================================================================
 * Per-module result structs
 * ========================================================================= */

/** Temporal tracking features accumulated over a sliding window. */
typedef struct {
    bool    face_detected;
    float   face_confidence;
    float   face_bbox_center_x;
    float   face_bbox_center_y;
    float   face_bbox_area;
    float   head_movement_variance;
    float   face_size_stability;
    bool    blink_detected;
    int32_t frames_collected;
    float   frame_similarity;
    int32_t consecutive_face_frames;
    float   movement_smoothness;
} OpadTemporalFeatures;

/** FFT-based moiré detection result. */
typedef struct {
    float moire_score;       /**< Mid-band energy ratio [0,1]; high = screen. */
    float peak_frequency;    /**< Dominant radial frequency bin index.         */
    float spectral_flatness; /**< Geometric/arithmetic mean ratio [0,1].      */
} OpadFrequencyResult;

/** LBP-derived screen artifact detection result. */
typedef struct {
    float screen_score;        /**< Composite screen score [0,1]; high = screen. */
    float uniformity;          /**< Focus uniformity across quadrants [0,1].     */
    float entropy;             /**< Color banding score [0,1].                   */
    float channel_correlation; /**< Skin chrominance tightness [0,1].            */
} OpadLbpResult;

/** Photometric analysis result from four sub-analyzers. */
typedef struct {
    float specular_score;    /**< Specular highlight naturalness [0,1].        */
    float chrominance_score; /**< Skin chrominance variance score [0,1].       */
    float edge_dof_score;    /**< Edge depth-of-field variation [0,1].         */
    float lighting_score;    /**< Color-temperature gradient score [0,1].      */
    float combined_score;    /**< Weighted combination of all four [0,1].      */
} OpadPhotometricResult;

/** Challenge state machine output. */
typedef struct {
    OpadChallengePhase phase;
    bool    capture_checkpoint_1; /**< True on the frame that completes baseline. */
    bool    capture_checkpoint_2; /**< True on the frame that completes hold.     */
    float   baseline_area;
    int32_t total_frames;
    int32_t hold_frames;
    float   max_area_increase;
    bool    completed;
} OpadChallengeOutput;

/* =========================================================================
 * Aggregation input structs (passed from Kotlin ML models)
 * ========================================================================= */

/** MiniFASNet texture classifier output. */
typedef struct {
    float genuine_score; /**< Genuine probability [0,1]. */
} OpadTextureInput;

/** MN3 + CDCN depth model outputs. */
typedef struct {
    bool  has_mn3;
    float mn3_real_score;   /**< MN3 real-face probability [0,1]. */
    bool  has_cdcn;
    float cdcn_depth_score; /**< CDCN depth score [0,1]; low = flat/spoof. */
} OpadDepthInput;

/** SSD MobileNet device detector output. */
typedef struct {
    bool  device_detected;
    bool  overlap_with_face;
    float max_confidence;
    float spoof_score;
} OpadDeviceInput;

/** Frequency gate inputs (from FFT + LBP modules). */
typedef struct {
    float moire_score;
    float lbp_screen_score;
} OpadFrequencyInput;

/** Photometric gate input. */
typedef struct {
    float combined_score;
} OpadPhotometricInput;

/* =========================================================================
 * Pipeline I/O (JNI wire format)
 * ========================================================================= */

/**
 * Per-frame input assembled by Kotlin and deserialized from a 62510-byte
 * little-endian buffer. Contains face geometry, downsampled frames, face
 * crops, and ML model scores.
 */
typedef struct {
    bool    has_face;
    float   center_x;
    float   center_y;
    float   area;
    float   confidence;
    float   frame_downsampled[OPAD_SIMILARITY_SIZE * OPAD_SIMILARITY_SIZE];
    float   face_crop_64_gray[OPAD_FFT_SIZE * OPAD_FFT_SIZE];
    uint8_t face_crop_64_argb[OPAD_LBP_CROP_SIZE * OPAD_LBP_CROP_SIZE * 4];
    uint8_t face_crop_80_argb[OPAD_PHOTO_CROP_SIZE * OPAD_PHOTO_CROP_SIZE * 4];
    float   texture_genuine;
    bool    has_mn3;
    float   mn3_real;
    bool    has_cdcn;
    float   cdcn;
    bool    device_detected;
    bool    device_overlap;
    float   device_max_conf;
    float   device_spoof;
} OpadFrameInput;

/** Per-frame output serialized to a 128-byte little-endian buffer for Kotlin. */
typedef struct {
    OpadPadStatus         pad_status;
    float                 aggregated_score;
    float                 frame_similarity;
    float                 face_sharpness;
    OpadChallengeOutput   challenge;
    OpadFrequencyResult   frequency;
    OpadLbpResult         lbp;
    OpadPhotometricResult photometric;
    OpadTemporalFeatures  temporal;
} OpadFrameOutput;

#endif /* OPENPAD_TYPES_H */
