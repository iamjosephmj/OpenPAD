# ===========================================================================
# OpenPAD C++ Unit Tests (host build)
# ===========================================================================
# Builds and runs on host (Linux/macOS) for fast iteration.
# Excludes openpad_jni.c (Android JNI) from the test library.
# ===========================================================================

cmake_minimum_required(VERSION 3.22)
project(openpad_test C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 14)

# Path to main openpad sources
set(OPENPAD_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp)

# Openpad C sources (exclude JNI)
set(OPENPAD_SOURCES
    ${OPENPAD_ROOT}/src/core/config.c
    ${OPENPAD_ROOT}/src/core/pipeline.c
    ${OPENPAD_ROOT}/src/image/similarity.c
    ${OPENPAD_ROOT}/src/image/sharpness.c
    ${OPENPAD_ROOT}/src/frequency/fft.c
    ${OPENPAD_ROOT}/src/frequency/moire.c
    ${OPENPAD_ROOT}/src/frequency/lbp.c
    ${OPENPAD_ROOT}/src/photometric/photometric.c
    ${OPENPAD_ROOT}/src/photometric/specular.c
    ${OPENPAD_ROOT}/src/photometric/chrominance.c
    ${OPENPAD_ROOT}/src/photometric/edge_dof.c
    ${OPENPAD_ROOT}/src/photometric/lighting.c
    ${OPENPAD_ROOT}/src/temporal/ringbuf.c
    ${OPENPAD_ROOT}/src/temporal/tracker.c
    ${OPENPAD_ROOT}/src/decision/classifier.c
    ${OPENPAD_ROOT}/src/decision/stabilizer.c
    ${OPENPAD_ROOT}/src/decision/challenge.c
)

# Static library for host (no Android liblog)
add_library(openpad_test_lib STATIC ${OPENPAD_SOURCES})
target_include_directories(openpad_test_lib
    PUBLIC  ${OPENPAD_ROOT}/include
    PRIVATE ${OPENPAD_ROOT}/src/frequency
    PRIVATE ${OPENPAD_ROOT}/src/photometric
    PRIVATE ${OPENPAD_ROOT}/src/temporal
)
target_compile_definitions(openpad_test_lib PRIVATE _GNU_SOURCE)
target_compile_options(openpad_test_lib PRIVATE -std=c99 -Wall -Wextra -O2)

# Pipeline and tracker may need math lib
target_link_libraries(openpad_test_lib m)

# GoogleTest
include(FetchContent)
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/03597a01ee50ed33e9dfd640b249b4be3799d395.zip
)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# Test executable
add_executable(openpad_test
    test_config.cpp
    test_ringbuf.cpp
    test_aggregation.cpp
)
target_link_libraries(openpad_test
    openpad_test_lib
    GTest::gtest_main
)
target_include_directories(openpad_test PRIVATE
    ${OPENPAD_ROOT}/include
    ${OPENPAD_ROOT}/src/temporal
)

enable_testing()
include(GoogleTest)
gtest_discover_tests(openpad_test)
