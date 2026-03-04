# OpenPAD SDK

On-device Presentation Attack Detection (face liveness) for Android. Detects face spoofing -- photos, screens, video replays, face swaps -- using a multi-layer ML pipeline. All processing runs locally. No server dependency, no cloud calls, no data leaves the device.

**Requirements**: Android 8.0+ (API 26), front-facing camera.

<p align="center">
  <img src="resources/pad.gif" width="300" alt="OpenPAD demo"/>
</p>

---

## Integration Guide

### 1. Add the dependency

Add the JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency in your app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.iamjosephmj:OpenPAD:1.0.4")
}
```

### 2. Initialize

Call `initialize()` once before starting verification (e.g. in `Activity.onCreate()`). This loads all ML models asynchronously.

```kotlin
OpenPad.initialize(context) {
    // SDK is ready
}
```

With a preset configuration:

```kotlin
OpenPad.initialize(context, config = OpenPadConfig.Banking)
```

With custom parameters:

```kotlin
OpenPad.initialize(
    context = this,
    config = OpenPadConfig(
        livenessThreshold = 0.70f,
        faceMatchThreshold = 0.70f
    ),
    onReady = { /* ready */ },
    onError = { error -> Log.e("PAD", error.toString()) }
)
```

### 3a. UI Mode -- Camera Verification Flow

Launch the built-in camera UI. The SDK handles camera, challenge-response, and delivers the result via callback. There is no intro or verdict screen -- the camera starts immediately and the activity finishes as soon as a verdict is reached. Your app decides how to present the result.

```kotlin
OpenPad.analyze(activity, object : OpenPadListener {
    override fun onLiveConfirmed(result: OpenPadResult) {
        // Verified live: result.confidence, result.durationMs
    }

    override fun onSpoofDetected(result: OpenPadResult) {
        // Spoof detected: result.spoofAttempts
    }

    override fun onError(error: OpenPadError) {
        // Initialization, camera, or permission error
    }

    override fun onCancelled() {
        // User closed the flow
    }
})
```

### 3b. Headless Mode -- Bring Your Own Camera

For integrators who want to use their own camera UI. The SDK provides a frame analyzer and reactive state flows.

```kotlin
val session = OpenPad.createSession(listener) ?: return

// Plug into CameraX
val imageAnalysis = ImageAnalysis.Builder().build()
imageAnalysis.setAnalyzer(executor, session.frameAnalyzer)

// Observe state to drive your own UI
lifecycleScope.launch {
    session.status.collect { status -> /* ANALYZING, LIVE, SPOOF_SUSPECTED */ }
}
lifecycleScope.launch {
    session.phase.collect { phase -> /* CHALLENGE_CLOSER, EVALUATING, etc. */ }
}
lifecycleScope.launch {
    session.instruction.collect { text -> /* "Move closer", "Hold still" */ }
}
lifecycleScope.launch {
    session.challengeProgress.collect { progress -> /* 0.0 to 1.0 */ }
}

// When done
session.release()
```

### 4. Theme Customization (UI Mode)

The SDK camera screen uses a customizable color theme. Set it before calling `analyze()`.

```kotlin
OpenPad.theme = OpenPadThemeConfig(
    primary = 0xFF1565C0,        // Buttons, progress arcs
    success = 0xFF2E7D32,        // Live-confirmed accent
    error = 0xFFD32F2F,          // Spoof-detected accent
    surface = 0xFF121212,        // Background
    surfaceVariant = 0xFF1E1E1E, // Elevated panels
    onSurface = 0xFFE0E0E0,     // Text color
    onSurfaceHigh = 0xFFFFFFFF,  // High-emphasis text
    scrim = 0xFF121212,          // Overlay outside face oval
    frostGlass = 0xFF1E1E1E,    // Frosted panel backgrounds
    ovalIdle = 0xFFE0E0E0,      // Face oval border (idle)
    divider = 0xFF333333         // Separator lines
)
```

Colors are ARGB hex `Long` values to keep the public API free of Compose dependencies.

### 5. Cleanup

```kotlin
OpenPad.release()
```

---

## Configuration Reference

All parameters are optional. Defaults are tuned for balanced security/usability.

```kotlin
OpenPadConfig(
    // --- Verdict ---
    livenessThreshold = 0.70f,           // Minimum overall confidence to accept as live
    faceMatchThreshold = 0.70f,          // Minimum face similarity between checkpoints (face swap detection)
    spoofAttemptPenalty = 0.08f,          // Extra threshold per consecutive failed attempt

    // --- Detection ---
    faceDetectionConfidence = 0.55f,     // Minimum face detection confidence

    // --- Scoring Weights (should sum to ~1.0) ---
    textureAnalysisWeight = 0.15f,       // Surface texture patterns (print/screen artifacts)
    depthGateWeight = 0.20f,             // Fast depth pre-filter
    depthAnalysisWeight = 0.55f,         // Full 3D depth map analysis (primary discriminator)
    screenDetectionWeight = 0.10f,       // Phone/laptop/tablet screen in frame

    // --- Model Thresholds ---
    depthGateMinScore = 0.20f,           // Score to trigger full depth analysis (lower = more permissive)
    depthFlatnessMinScore = 0.40f,       // Hard cutoff: faces flatter than this are rejected
    screenDetectionMinConfidence = 0.50f, // Screen detection confidence to count as a signal

    // --- Classical Signal Thresholds ---
    moireDetectionThreshold = 0.60f,     // Moire score above this flags screen artifacts
    screenPatternThreshold = 0.70f,      // LBP screen pattern score above this flags screen
    photometricMinScore = 0.30f,         // Combined photometric score below this flags spoof

    // --- Performance ---
    maxFramesPerSecond = 8,              // Frame processing rate
    enableDebugOverlay = false,          // Show real-time debug metrics

    // --- Frame Enhancement ---
    enableFrameEnhancement = true        // ESPCN x2 super-resolution during challenge (ML quality gate)
)
```

---

## Configuration Presets

The SDK ships 10 named presets for common scenarios. Use them directly or as a starting point for fine-tuning.

| Preset | Security | Speed | Use Case |
|--------|----------|-------|----------|
| `Default` | Balanced | Balanced | General-purpose apps |
| `HighSecurity` | High | Standard | Identity verification, high-value transactions |
| `FastPass` | Low | Fast | Check-in, attendance, low-risk access |
| `Banking` | Very High | Standard | Financial services, regulatory compliance |
| `Onboarding` | Moderate | Standard | First-time user registration (minimize drop-off) |
| `Kiosk` | High | Standard | Fixed-mount terminals with controlled lighting |
| `LowEndDevice` | Moderate | Fast | Budget Android phones (disables frame enhancement) |
| `Development` | Minimal | Standard | Integration testing (debug overlay enabled) |
| `HighThroughput` | Moderate | Very Fast | Queues, turnstiles, batch processing (15 FPS) |
| `MaxAccuracy` | Maximum | Standard | Security-critical flows (highest false-rejection rate) |

### Usage

```kotlin
// Use a preset directly
OpenPad.initialize(context, config = OpenPadConfig.Banking)

// Use a preset as a starting point, then override specific parameters
OpenPad.initialize(
    context = this,
    config = OpenPadConfig.HighSecurity.copy(
        enableDebugOverlay = true,
        maxFramesPerSecond = 10
    )
)
```

### Key parameter differences from Default

| Parameter | Default | HighSecurity | FastPass | Banking | MaxAccuracy |
|-----------|---------|-------------|----------|---------|-------------|
| `livenessThreshold` | 0.70 | 0.85 | 0.55 | 0.85 | 0.90 |
| `faceMatchThreshold` | 0.70 | 0.80 | 0.60 | 0.82 | 0.85 |
| `depthFlatnessMinScore` | 0.40 | 0.50 | 0.35 | 0.50 | 0.55 |
| `photometricMinScore` | 0.30 | 0.35 | 0.25 | 0.40 | 0.45 |
| `spoofAttemptPenalty` | 0.08 | 0.10 | 0.05 | 0.12 | 0.12 |
| `maxFramesPerSecond` | 8 | 8 | 12 | 8 | 8 |
| `enableFrameEnhancement` | true | true | true | true | true |
| `enableDebugOverlay` | false | false | false | false | false |

---

## Public API

| Class | Purpose |
|-------|---------|
| `OpenPad` | Singleton entry point. `initialize()`, `analyze()`, `createSession()`, `release()`. |
| `OpenPadConfig` | All tunable thresholds, weights, and limits. |
| `OpenPadThemeConfig` | UI color customization (ARGB hex longs). |
| `OpenPadListener` | Callback interface: `onLiveConfirmed`, `onSpoofDetected`, `onError`, `onCancelled`. |
| `OpenPadResult` | Verdict: `isLive`, `confidence`, `durationMs`, `spoofAttempts`, `depthCharacteristics`, `faceAtNormalDistance`, `faceAtCloseDistance`. |
| `DepthCharacteristics` | 3D depth statistics from the CDCN 32x32 depth map: `mean`, `standardDeviation`, `quadrantVariance`, `minDepth`, `maxDepth`. |
| `OpenPadError` | Sealed class: `NotInitialized`, `InitializationFailed`, `CameraUnavailable`, `PermissionDenied`, `AlreadyRunning`. |
| `OpenPadSession` | Headless session: `frameAnalyzer`, `status`, `phase`, `challengeProgress`, `instruction`, `release()`. |

---

## Architecture

### Pipeline Overview

```mermaid
flowchart TB
    CAM[Camera Frame] --> FD[1. Face Detection]
    FD --> TEX[2. Texture]
    FD --> DEP[3. Depth]
    FD --> FREQ[4. Frequency]
    FD --> DEV[5. Device]
    FD --> PHOTO[6. Photometric]
    FD --> TEMP[7. Temporal]
    FD --> EMB[8. Face Embedding]
    FD --> ENH[9. Frame Enhancement]
    TEX --> AGG[10. Aggregation]
    DEP --> AGG
    FREQ --> AGG
    DEV --> AGG
    PHOTO --> AGG
    TEMP --> AGG
    ENH --> AGG
    EMB --> CHAL[11. Challenge]
    AGG --> CHAL
    CHAL --> VERDICT[Verdict]
```

| Layer | Component | Type |
|-------|-----------|------|
| 1 | Face Detection (BlazeFace) | ML |
| 2 | Texture (MiniFASNet V2 + V1SE) | ML |
| 3 | Depth (MN3 gate → CDCN) | ML |
| 4 | Frequency (FFT moiré + LBP) | Native C |
| 5 | Device (SSD MobileNet) | ML |
| 6 | Photometric (specular, chrominance, DOF, lighting) | Native C |
| 7 | Temporal (movement, blink, similarity) | Native C |
| 8 | Face Embedding (MobileFaceNet) | ML |
| 9 | Frame Enhancement (ESPCN x2 super-resolution) | ML |
| 10 | Aggregation (gates + stabilizer) | Native C |
| 11 | Challenge-Response ("move closer") | Native C |

### Detection Layers

| Layer | What It Detects | Type | Role in Pipeline |
|-------|----------------|------|------------------|
| Texture | Paper grain, screen sub-pixels, skin micro-texture | Learned (CNN) | Gate + ML score (15%) |
| Depth | Flat surfaces vs 3D face geometry | Learned (CNN) | Gate + ML score (20% + 55%) |
| Frequency | Moire patterns, screen pixel grids | Classical (FFT + LBP) | Gate (moire + LBP both flagged) |
| Device | Phone, laptop, TV, monitor in frame | Learned (object detection) | Gate + ML score (10%) |
| Photometric | Specular reflections, color temperature, uniform DOF | Classical | Gate (combined score too low) |
| Temporal | Static images, missing blinks, replay patterns | Classical | Pre-classification (frames, face presence) |
| Face Match | Face swap mid-challenge | Learned (embedding) | Separate check at challenge evaluation |
| Frame Enhancement | Defocus blur on face regions | Learned (ESPCN SR) | Auto-enhances during challenge; ML quality gate decides keep/discard |

### Classification Gates

Per-frame classification uses sequential decision gates (first match wins):

```mermaid
flowchart TD
    A[Frame] --> B{Enough frames?}
    B -->|No| ANALYZING[ANALYZING]
    B -->|Yes| C{Face OK?}
    C -->|No| NO_FACE[NO_FACE]
    C -->|Yes| D{Device overlap?}
    D -->|Yes| SPOOF[SPOOF_SUSPECTED]
    D -->|No| E{Moiré + LBP?}
    E -->|Yes| SPOOF
    E -->|No| F{Texture pass?}
    F -->|No| SPOOF
    F -->|Yes| G{CDCN depth?}
    G -->|No| SPOOF
    G -->|Yes| H{Photometric?}
    H -->|No| SPOOF
    H -->|Yes| LIVE[LIVE]
```

1. Not enough frames → ANALYZING  
2. No face / low confidence → NO_FACE  
3. **Device gate**: phone/laptop/TV overlapping face → SPOOF_SUSPECTED  
4. **Frequency gate**: moire + LBP both above threshold → SPOOF_SUSPECTED  
5. **Texture gate**: MiniFASNet genuine score below threshold → SPOOF_SUSPECTED  
6. **CDCN depth gate**: depth below flatness threshold → SPOOF_SUSPECTED  
7. **Photometric gate**: combined score below threshold → SPOOF_SUSPECTED  
8. All signals pass → LIVE

### ML Aggregate Score

The challenge evaluation score is a weighted sum of four ML model signals:

| Signal | Weight | Role |
|--------|--------|------|
| Texture analysis | 15% | Surface micro-texture discrimination |
| Depth gate (MN3) | 20% | Fast binary live/spoof signal |
| Depth map (CDCN) | 55% | Primary depth discriminator |
| Screen detection | 10% | Replay device presence |

Dynamic weighting: if CDCN is unavailable, its weight redistributes to texture (2/3) and MN3 (1/3). Classical signals (frequency, photometric) act as gates but do not contribute to the continuous score.

### Challenge-Response Flow

```
IDLE -> ANALYZING -> POSITIONING -> CHALLENGE_CLOSER -> EVALUATING -> LIVE -> DONE
```

1. **ANALYZING** -- Wait for stable face detection
2. **POSITIONING** -- Face must be centered and in range
3. **CHALLENGE_CLOSER** -- User must move closer (15%+ face area increase). ML scores collected during hold phase. ESPCN super-resolution auto-enhances blurry face regions (ML quality gate decides keep/discard).
4. **EVALUATING** -- Face match check (MobileFaceNet) + weighted score evaluation against threshold
5. **LIVE** -- 1s sustain timer, then verdict delivered
6. **DONE** -- Terminal state

Failed attempts restart from ANALYZING with escalating threshold (+0.08 per attempt). Retries are unlimited.

### UI Flow (UI Mode)

The SDK provides only the camera screen. When the challenge completes (live or spoof), the result is delivered via `OpenPadListener` and the activity finishes immediately. Intro screens, verdict screens, and retry logic are the consuming app's responsibility.

```
CameraScreen (with FaceGuideOverlay) -> Result delivered via callback -> Activity finishes
```

- **CameraScreen**: Live camera preview with animated face oval, phase indicators, instruction pill, gradient scrims

---

## Model Assets

All models are stored as `.pad` files (gzip-compressed, XOR-scrambled) in `pad-core/src/main/assets/models/`. This prevents casual extraction and renaming of the TFLite models from the APK.

| File | Original Size | Packed Size | Purpose |
|------|--------------|-------------|---------|
| `face_detection.pad` | 225 KB | 195 KB | BlazeFace face detection |
| `texture_2x7.pad` | 1.7 MB | 1.5 MB | Texture analysis (2.7x crop scale) |
| `texture_4x0.pad` | 1.7 MB | 1.5 MB | Texture analysis (4.0x crop scale) |
| `depth_gate.pad` | 5.8 MB | 5.3 MB | MN3 fast depth gate |
| `depth_map.pad` | 3.4 MB | 3.2 MB | CDCN depth map |
| `device_detection.pad` | 4.0 MB | 2.8 MB | SSD MobileNet screen detection |
| `face_embedding.pad` | 5.0 MB | 4.6 MB | MobileFaceNet face consistency |
| `face_enhance.pad` | 91 KB | 79 KB | ESPCN x2 face super-resolution |
| **Total** | **21.9 MB** | **19.3 MB** | |

### Model Packing

Models are packed at build time using `scripts/pack_models.py`:

```bash
# Pack: .tflite -> .pad (gzip + XOR)
python scripts/pack_models.py

# Unpack: .pad -> .tflite (reverse)
python scripts/pack_models.py --unpack
```

At runtime, `ModelLoader.kt` reverses the process: XOR-descramble with a 32-byte key, then gzip-decompress into a `ByteBuffer` for the TFLite interpreter.

### Model Licenses

| Model | License | Source |
|-------|---------|--------|
| BlazeFace | Apache 2.0 | Google/MediaPipe |
| MiniFASNet V2 + V1SE | Apache 2.0 | MiniVision (Silent-Face-Anti-Spoofing) |
| Anti-Spoof MN3 | MIT | kprokofi (PINTO Model Zoo) |
| CDCN depth map | In-house trained | Architecture from CDCN paper (arXiv:2003.04092) |
| SSD MobileNet V1 COCO | Apache 2.0 | TensorFlow |
| MobileFaceNet | MIT | syaringan357 |
| ESPCN x2 | MIT | fannymonori/TF-ESPCN |

---

## Project Structure

```
OpenPAD/
├── app/                                    # Demo app
│   └── src/main/java/com/openpad/app/
│       ├── OpenPadApp.kt                   # Application class (theme setup)
│       ├── MainActivity.kt                 # UI mode + headless mode launcher + result display
│       ├── ConfigBottomSheet.kt            # Runtime configuration editor (all OpenPadConfig params)
│       ├── ResultBottomSheet.kt            # Verification result details (depth stats + face crops)
│       └── HeadlessActivity.kt             # Headless integration demo
│
├── pad-core/                               # SDK library module
│   ├── src/main/cpp/                       # Native C layer (see cpp/README.md)
│   │   ├── include/openpad/                # Public headers
│   │   └── src/                            # JNI, core, image, frequency, photometric, temporal, decision
│   └── src/main/java/com/openpad/core/
│       │
│       ├── OpenPad.kt                      # Singleton entry point
│       ├── OpenPadConfig.kt                # Public configuration (10 named presets)
│       ├── OpenPadConfigMapper.kt          # Maps public config → internal config
│       ├── OpenPadThemeConfig.kt            # UI theme colors
│       ├── OpenPadListener.kt              # Result callback interface
│       ├── OpenPadResult.kt                # Verdict data class
│       ├── OpenPadError.kt                 # Error types
│       ├── OpenPadSession.kt               # Headless session interface + impl
│       ├── PadConfig.kt                    # Internal pipeline thresholds (InternalPadConfig)
│       ├── PadPipeline.kt                  # Pipeline factory
│       ├── PadResult.kt                    # Per-frame result
│       │
│       ├── detection/                      # Layer 1: Face detection
│       │   ├── MediaPipeFaceDetector.kt    #   BlazeFace inference + SSD decode
│       │   ├── FaceDetection.kt            #   Face data class
│       │   └── FaceDetector.kt             #   Interface
│       │
│       ├── texture/                        # Layer 2: Texture analysis
│       │   ├── MiniFasNetAnalyzer.kt       #   Multi-scale MiniFASNet ensemble
│       │   ├── TextureAnalyzer.kt          #   Interface
│       │   └── TextureResult.kt            #   Result data class
│       │
│       ├── depth/                          # Layer 3: Depth analysis
│       │   ├── CdcnDepthAnalyzer.kt        #   MN3 + CDCN cascaded inference
│       │   ├── CascadedDepthAnalyzer.kt    #   Cascade orchestration
│       │   ├── DepthAnalyzer.kt            #   Interface
│       │   ├── DepthResult.kt              #   Result data class
│       │   └── DepthCharacteristics.kt     #   3D depth map statistics
│       │
│       ├── frequency/                      # Layer 4: Frequency analysis (native C)
│       │   ├── FrequencyAnalyzer.kt        #   Interface
│       │   ├── FrequencyResult.kt          #   FFT result data class
│       │   └── LbpResult.kt               #   LBP result data class
│       │
│       ├── device/                         # Layer 5: Device detection
│       │   ├── SsdDeviceDetector.kt        #   SSD MobileNet inference
│       │   ├── DeviceDetector.kt           #   Interface
│       │   └── DeviceDetectionResult.kt    #   Result data class
│       │
│       ├── photometric/                    # Layer 6: Photometric analysis
│       │   ├── PhotometricAnalyzer.kt      #   Specular, chrominance, DOF, lighting
│       │   └── PhotometricResult.kt        #   Result data class
│       │
│       ├── signals/                        # Layer 7: Temporal signals
│       │   ├── DefaultTemporalTracker.kt   #   Sliding window tracker
│       │   ├── TemporalSignalTracker.kt    #   Interface
│       │   └── TemporalFeatures.kt         #   Features data class
│       │
│       ├── embedding/                      # Layer 8: Face embedding
│       │   ├── MobileFaceNetAnalyzer.kt    #   Face consistency verification
│       │   ├── FaceEmbeddingAnalyzer.kt    #   Interface
│       │   └── FaceEmbeddingResult.kt      #   Result data class
│       │
│       ├── enhance/                        # Layer 9: Frame enhancement
│       │   ├── EspcnFrameEnhancer.kt      #   ESPCN x2 super-resolution (ML quality gate)
│       │   └── FrameEnhancer.kt           #   Interface
│       │
│       ├── aggregation/                    # Layer 10: Aggregation
│       │   ├── WeightedAggregator.kt       #   Rule gates + weighted fusion
│       │   ├── StateStabilizer.kt          #   Hysteresis state machine
│       │   ├── ScoreAggregator.kt          #   Interface
│       │   └── PadStatus.kt               #   Status enum (with fromInt companion)
│       │
│       ├── challenge/                      # Layer 11: Challenge-response
│       │   ├── MovementChallenge.kt        #   "Move closer" state machine
│       │   ├── ChallengeManager.kt         #   Interface
│       │   └── ChallengeState.kt           #   Phase enum + evidence
│       │
│       ├── evaluation/                     # Shared evaluation logic
│       │   ├── PositioningGuidance.kt      #   Face positioning guidance messages
│       │   ├── GenuineProbabilityCalculator.kt # Weighted ML score calculation
│       │   ├── FaceConsistencyChecker.kt   #   Face swap detection via embeddings
│       │   ├── ChallengeEvaluator.kt       #   Orchestrates full evaluation flow
│       │   └── OpenPadResultFactory.kt     #   Consistent result construction
│       │
│       ├── analyzer/                       # Frame processing
│       │   ├── PadFrameAnalyzer.kt         #   CameraX analyzer orchestration
│       │   ├── BitmapConverter.kt          #   YUV conversion, crops, similarity
│       │   ├── NativeInputBuilder.kt       #   Native C input assembly
│       │   └── PadResultMapper.kt          #   Native output → PadResult mapping
│       │
│       ├── ndk/                            # Native bridge
│       │   ├── OpenPadNative.kt            #   JNI bridge + NativeFrameOutput
│       │   └── NativeChallengeManager.kt   #   Native challenge state bridge
│       │
│       ├── model/                          # Model loading
│       │   └── ModelLoader.kt              #   .pad decryption + TFLite loading
│       │
│       └── ui/                             # Built-in SDK UI (camera only)
│           ├── PadActivity.kt              #   Host activity (camera + result delivery)
│           ├── CameraScreen.kt             #   Camera preview with overlay
│           ├── FaceGuideOverlay.kt         #   Animated face oval
│           ├── VerdictScreen.kt            #   Verdict display composable
│           ├── theme/OpenPadTheme.kt       #   Compose theme (reads OpenPadThemeConfig)
│           └── viewmodel/                  #   MVI state management
│               ├── PadViewModel.kt
│               ├── PadViewModelFactory.kt  #   ViewModel dependency injection
│               ├── PadUiState.kt
│               ├── PadIntent.kt
│               └── VerdictState.kt         #   Verdict sealed class
│
├── scripts/
│   ├── pack_models.py                      # Gzip + XOR model packer
│   └── convert_espcn.py                    # ESPCN TF→TFLite converter + .pad packer
│
└── gradle/libs.versions.toml              # Dependency version catalog
```

---

## Building

```bash
# Build everything
./gradlew :pad-core:build :app:build

# Install demo app
./gradlew :app:installDebug

# Run unit tests
./gradlew :pad-core:test
```

---

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| AGP | 8.10.0 | Build system |
| Kotlin | 2.2.0 | Language |
| CameraX | 1.4.2 | Camera preview + frame analysis |
| Compose BOM | 2026.01.01 | UI (Material3) |
| Lifecycle | 2.8.7 | ViewModel + Compose integration |
| LiteRT | 1.4.1 | TFLite model inference |
| Timber | 5.0.1 | Logging |

---

## Detection Capabilities

| Attack Type | Detection | Primary Signals |
|-------------|-----------|-----------------|
| Printed photo (still) | Strong | Frame similarity + texture + temporal |
| Printed photo (hand-held) | Strong | Texture + depth + DOF variance |
| LCD screen (static) | Strong | FFT moire + LBP + texture + device detection |
| Phone screen (static image) | Strong | Texture + depth + device detection |
| Phone screen (video replay) | Moderate | Depth + texture + LBP |
| High-PPI OLED replay | Moderate | Depth + device detection + photometric |
| Face swap mid-challenge | Strong | Face embedding consistency (MobileFaceNet) |
| 3D printed/silicone mask | Weak–Moderate | Texture + photometric; training data collection underway |

---

## Android Compatibility

- **Edge-to-edge**: The SDK camera screen handles system bar insets correctly (status bar, navigation bar)
- **16KB page alignment**: Native libraries use `useLegacyPackaging = false` for Android 15+ compatibility
- **Minimum SDK**: API 26 (Android 8.0)
- **Target SDK**: API 35

---

## Known Limitations

1. **Single RGB camera** -- no hardware depth sensing. 3D mask attacks (silicone, paper, 3D-printed) have limited detection. Training data is being collected to improve mask detection via texture and photometric signals.
2. **Single fixed challenge** -- "move closer" is predictable. Randomized multi-challenge is on the roadmap.
3. **Client-side only** -- all decisions on-device. A rooted device can bypass the pipeline.
4. **Model weights are obfuscated, not encrypted** -- XOR scrambling deters casual extraction but is not cryptographically secure.

---

## See Also

- [ARCHITECTURE.md](ARCHITECTURE.md) — Deep technical docs, security analysis, threat model, threshold tuning guide
- [pad-core/src/main/cpp/README.md](pad-core/src/main/cpp/README.md) — Native C layer: architecture, Mermaid diagrams, wire format, build
