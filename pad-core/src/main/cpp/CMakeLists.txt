# ===========================================================================
# OpenPAD Native Library — CMake Build
# ===========================================================================
#
# Directory layout:
#   include/openpad/   — Public headers (types, module APIs, umbrella header)
#   src/jni/           — JNI bridge (Android-specific)
#   src/core/          — Config parsing and pipeline orchestrator
#   src/image/         — Frame similarity and face sharpness
#   src/frequency/     — FFT moiré detection and LBP screen analysis
#   src/photometric/   — Four photometric sub-analyzers + combiner
#   src/temporal/      — Ring buffer and temporal feature tracker
#   src/decision/      — Classifier, stabilizer, challenge state machine
#
# Produces: libopenpad.so (shared library loaded via System.loadLibrary)
# ===========================================================================

cmake_minimum_required(VERSION 3.22)
project(openpad C)

# --- Source files ---

set(OPENPAD_SOURCES
    # JNI bridge
    src/jni/openpad_jni.c

    # Core
    src/core/config.c
    src/core/pipeline.c

    # Image analysis
    src/image/similarity.c
    src/image/sharpness.c

    # Frequency analysis
    src/frequency/fft.c
    src/frequency/moire.c
    src/frequency/lbp.c

    # Photometric analysis
    src/photometric/photometric.c
    src/photometric/specular.c
    src/photometric/chrominance.c
    src/photometric/edge_dof.c
    src/photometric/lighting.c

    # Temporal tracking
    src/temporal/ringbuf.c
    src/temporal/tracker.c

    # Decision logic
    src/decision/classifier.c
    src/decision/stabilizer.c
    src/decision/challenge.c
)

add_library(openpad SHARED ${OPENPAD_SOURCES})

# --- Include paths ---
# Public headers in include/ are accessible via <openpad/types.h> etc.
# Private headers within src/ subdirectories use relative includes.
target_include_directories(openpad
    PUBLIC  ${CMAKE_CURRENT_SOURCE_DIR}/include
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/frequency
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/photometric
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src/temporal
)

# --- Linked libraries ---
find_library(log-lib log)
target_link_libraries(openpad ${log-lib} m)

# --- 16KB page alignment for Android 15+ compliance ---
target_link_options(openpad PRIVATE "-Wl,-z,max-page-size=16384")

# --- Compiler flags ---
target_compile_options(openpad PRIVATE -std=c99 -Wall -Wextra -O2)
