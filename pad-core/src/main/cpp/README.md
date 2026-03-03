# OpenPAD Native Layer (C)

Pure C implementation of the OpenPAD signal-processing pipeline: FFT moiré detection, LBP screen analysis, photometric analysis, temporal tracking, classification, and challenge-response state machine. Built with the Android NDK, 16KB page-aligned for Android 15+.

Frame enhancement (ESPCN x2 super-resolution) runs in the Kotlin/TFLite layer, not in the native layer. The native pipeline receives already-enhanced bitmaps transparently.

---

## Architecture Overview

```mermaid
flowchart TB
    subgraph JNI["JNI Bridge"]
        JNI_IN[Kotlin ByteArray]
        JNI_OUT[ByteArray Output]
    end

    subgraph Pipeline["Pipeline Orchestrator"]
        PIPE[opad_pipeline_analyze_frame]
    end

    subgraph Analysis["Analysis Modules"]
        SIM[Similarity]
        SHP[Sharpness]
        FFT[FFT Moiré]
        LBP[LBP Screen]
        PHOTO[Photometric]
        TEMP[Temporal]
    end

    subgraph Decision["Decision Logic"]
        CLS[Classifier]
        STAB[Stabilizer]
        CHAL[Challenge FSM]
    end

    JNI_IN --> PIPE
    PIPE --> SIM
    PIPE --> SHP
    PIPE --> FFT
    PIPE --> LBP
    PIPE --> PHOTO
    PIPE --> TEMP
    SIM --> CLS
    SHP --> CLS
    FFT --> CLS
    LBP --> CLS
    PHOTO --> CLS
    TEMP --> CLS
    CLS --> STAB
    STAB --> CHAL
    CHAL --> JNI_OUT
```

---

## Per-Frame Data Flow

```mermaid
flowchart LR
    subgraph Input["Input (from Kotlin)"]
        I1[Face geometry]
        I2[32×32 grayscale]
        I3[64×64 face crops]
        I4[80×80 face crop]
        I5[ML scores]
    end

    subgraph Stage1["Stage 1: Image"]
        A1[Frame similarity]
        A2[Face sharpness]
    end

    subgraph Stage2["Stage 2: Frequency"]
        B1[FFT moiré]
        B2[LBP screen]
    end

    subgraph Stage3["Stage 3: Photometric"]
        C1[Specular]
        C2[Chrominance]
        C3[Edge DOF]
        C4[Lighting]
    end

    subgraph Stage4["Stage 4: Temporal"]
        D1[Ring buffers]
        D2[Movement variance]
        D3[Blink detection]
    end

    subgraph Stage5["Stage 5: Decision"]
        E1[Classifier gates]
        E2[Stabilizer]
        E3[Challenge FSM]
    end

    I1 --> D1
    I2 --> A1
    I3 --> A2
    I3 --> B1
    I3 --> B2
    I4 --> C1
    I5 --> E1

    A1 --> D1
    A2 --> E1
    B1 --> E1
    B2 --> E1
    C1 --> C2
    C2 --> C3
    C3 --> C4
    C4 --> E1
    D1 --> D2
    D2 --> D3
    D3 --> E1
    E1 --> E2
    E2 --> E3
```

---

## Module Dependency Graph

```mermaid
flowchart TD
    subgraph Public["Public API (include/openpad/)"]
        TYPES[types.h]
        CONFIG[config.h]
        IMAGE[image.h]
        FREQ[frequency.h]
        PHOTO[photometric.h]
        TEMP[temporal.h]
        AGG[aggregation.h]
        CHAL[challenge.h]
        PIPE[pipeline.h]
    end

    subgraph Internal["Internal Modules"]
        RING[ringbuf]
        FFT_CORE[fft]
    end

    PIPE --> CONFIG
    PIPE --> IMAGE
    PIPE --> FREQ
    PIPE --> PHOTO
    PIPE --> TEMP
    PIPE --> AGG
    PIPE --> CHAL

    FREQ --> FFT_CORE
    TEMP --> RING

    CONFIG --> TYPES
    IMAGE --> TYPES
    FREQ --> TYPES
    PHOTO --> TYPES
    TEMP --> TYPES
    AGG --> TYPES
    CHAL --> TYPES
    PIPE --> TYPES
```

---

## Classification Gate Flow

Per-frame classification uses sequential decision gates. The first gate that fires determines the status.

```mermaid
flowchart TD
    START([Frame input]) --> G1{Enough frames?}
    G1 -->|No| ANALYZING[ANALYZING]
    G1 -->|Yes| G2{Face detected?}
    G2 -->|No| NO_FACE[NO_FACE]
    G2 -->|Yes| G3{Device overlap?}
    G3 -->|Yes| SPOOF1[SPOOF_SUSPECTED]
    G3 -->|No| G4{Moiré + LBP both high?}
    G4 -->|Yes| SPOOF2[SPOOF_SUSPECTED]
    G4 -->|No| G5{Texture passes?}
    G5 -->|No| SPOOF3[SPOOF_SUSPECTED]
    G5 -->|Yes| G6{CDCN depth OK?}
    G6 -->|No| SPOOF4[SPOOF_SUSPECTED]
    G6 -->|Yes| G7{Photometric OK?}
    G7 -->|No| SPOOF5[SPOOF_SUSPECTED]
    G7 -->|Yes| LIVE[LIVE]
```

---

## Challenge State Machine

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> ANALYZING: First frame
    ANALYZING --> ANALYZING: No face / reset
    ANALYZING --> CHALLENGE_CLOSER: Stable face frames
    CHALLENGE_CLOSER --> CHALLENGE_CLOSER: Hold or jitter
    CHALLENGE_CLOSER --> EVALUATING: Hold complete
    EVALUATING --> LIVE: Pass (Kotlin)
    EVALUATING --> ANALYZING: Spoof retry
    LIVE --> DONE: Sustain timer
    DONE --> [*]
```

---

## Directory Structure

```mermaid
flowchart LR
    subgraph cpp["pad-core/src/main/cpp/"]
        subgraph include["include/openpad/"]
            H1[types.h]
            H2[config.h]
            H3[image.h]
            H4[frequency.h]
            H5[photometric.h]
            H6[temporal.h]
            H7[aggregation.h]
            H8[challenge.h]
            H9[pipeline.h]
            H10[openpad.h]
        end

        subgraph src["src/"]
            subgraph jni["jni/"]
                J1[openpad_jni.c]
            end
            subgraph core["core/"]
                C1[config.c]
                C2[pipeline.c]
            end
            subgraph image["image/"]
                I1[similarity.c]
                I2[sharpness.c]
            end
            subgraph freq["frequency/"]
                F1[fft.c]
                F2[moire.c]
                F3[lbp.c]
            end
            subgraph photo["photometric/"]
                P1[photometric.c]
                P2[specular.c]
                P3[chrominance.c]
                P4[edge_dof.c]
                P5[lighting.c]
            end
            subgraph temp["temporal/"]
                T1[ringbuf.c]
                T2[tracker.c]
            end
            subgraph dec["decision/"]
                D1[classifier.c]
                D2[stabilizer.c]
                D3[challenge.c]
            end
        end
    end
```

| Directory | Purpose |
|-----------|---------|
| `include/openpad/` | Public API headers. Include via `#include <openpad/types.h>`. |
| `src/jni/` | JNI bridge — serializes Kotlin ↔ C wire format. |
| `src/core/` | Config parsing and pipeline orchestrator. |
| `src/image/` | Frame similarity (MAD) and face sharpness (Laplacian). |
| `src/frequency/` | FFT moiré (2D Cooley-Tukey) and LBP screen detection. |
| `src/photometric/` | Specular, chrominance, edge DOF, lighting — four sub-analyzers. |
| `src/temporal/` | Ring buffer and temporal feature tracker. |
| `src/decision/` | Classifier, stabilizer, challenge state machine. |

---

## Wire Format (JNI)

```mermaid
flowchart LR
    subgraph Input["Input: 62,510 bytes"]
        I1["0-1: has_face"]
        I2["2-17: face geometry"]
        I3["18-4113: 32×32 float"]
        I4["4114-20497: 64×64 gray"]
        I5["20498-36881: 64×64 ARGB"]
        I6["36882-62481: 80×80 ARGB"]
        I7["62482-end: ML scores"]
    end

    subgraph Output["Output: 128 bytes"]
        O1["0-3: pad_status"]
        O2["4-15: score, similarity, sharpness"]
        O3["16-21: challenge phase + checkpoints"]
        O4["22-109: frequency, photometric, temporal"]
        O5["112-127: challenge metrics"]
    end
```

| Buffer | Size | Layout |
|--------|------|--------|
| **Config** | 172 bytes | 21×float (0-83), padding (84-127), 11×int32 (128-171) |
| **Input** | 62,510 bytes | Face + downsampled frame + face crops + ML scores |
| **Output** | 128 bytes | Status, scores, challenge state, all module results |

---

## Build

```bash
# From project root — CMake is invoked by Gradle
./gradlew :pad-core:assembleDebug

# Or build the full app
./gradlew :app:assembleDebug
```

The native library is built via `externalNativeBuild` in `pad-core/build.gradle.kts`. CMake produces `libopenpad.so` for `arm64-v8a` and `x86_64`.

### Build Requirements

- Android NDK (r27+ recommended)
- CMake 3.22+
- C99 compiler (Clang)

### 16KB Page Alignment

For Android 15+ compatibility, the library is linked with:

```
-Wl,-z,max-page-size=16384
```

---

## Public API Summary

| Module | Key Functions |
|--------|---------------|
| **Config** | `opad_config_default()`, `opad_config_parse()` |
| **Image** | `opad_compute_frame_similarity()`, `opad_compute_face_sharpness()` |
| **Frequency** | `opad_fft_moire()`, `opad_lbp_screen()` |
| **Photometric** | `opad_photometric_analyze()` |
| **Temporal** | `opad_temporal_tracker_create()`, `opad_temporal_tracker_update()`, `opad_temporal_tracker_reset()` |
| **Aggregation** | `opad_classify()`, `opad_compute_aggregate_score()`, `opad_stabilizer_*()` |
| **Challenge** | `opad_challenge_create()`, `opad_challenge_on_frame()`, `opad_challenge_advance_to_live()` |
| **Pipeline** | `opad_pipeline_create()`, `opad_pipeline_analyze_frame()`, `opad_pipeline_reset()` |

All symbols use the `opad_` prefix (functions) or `Opad` / `OPAD_` prefix (types) to avoid collisions.

---

## Kotlin Integration

The Kotlin layer (`com.openpad.core.ndk.OpenPadNative`) loads the library and calls JNI functions:

```mermaid
sequenceDiagram
    participant K as Kotlin (OpenPadNative)
    participant J as JNI (openpad_jni.c)
    participant P as Pipeline (C)

    K->>J: nativeInit(configBytes)
    J->>P: opad_pipeline_create()
    
    loop Per frame
        K->>J: nativeAnalyzeFrame(inputBytes)
        J->>P: opad_pipeline_analyze_frame()
        P-->>J: OpadFrameOutput
        J-->>K: outputBytes (128)
    end

    K->>J: nativeChallengeAdvanceToLive()
    J->>P: opad_pipeline_challenge_advance_to_live()
```

---

## Contributing

When modifying the native layer:

1. **Keep C99** — no C11+ features; ensures broad NDK compatibility.
2. **Preserve `opad_` prefix** — all public symbols must be prefixed.
3. **Update wire format docs** — if changing input/output layout, document it here and in `OpenPadNative.kt`.
4. **Run the build** — `./gradlew :pad-core:assembleDebug` must succeed for `arm64-v8a` and `x86_64`.

---

## License

Same as the parent OpenPAD project.
