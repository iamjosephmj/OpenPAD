#!/usr/bin/env bash
# Run OpenPAD C++ unit tests on host.
# Requires: cmake, gcc/g++, make or ninja

set -e
cd "$(dirname "$0")/../pad-core/src/test/cpp"
cmake -B build -S .
cmake --build build
ctest --test-dir build --output-on-failure
