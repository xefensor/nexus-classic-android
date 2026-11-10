# Validation

## Automated checks

- Seven JVM tests: pulse initialization, four-way grid-snapped taps, bounded extra pool, complete-tail expiry, speed independence, Pixel 8a coordinate mapping and hidden-time handling.
- Android lint and optimized release build.
- Three instrumentation tests on the Pixel 8a AVD at 1080 × 2400, Android 17 (API 37), x86_64 with 16 KB pages and SwiftShader rendering: preview pause/resume and recreation, persisted frame-rate selection, and visible coloured pixels after a tap.

The emulator uses an installed Android 17 beta system image. It checks rendering and Android integration; it does not reproduce a physical Pixel's GPU, battery or thermal behaviour.

## Manual emulator checks

The signed release APK is checked through its normal app and system wallpaper picker entry points, including application to the home and lock screens, tap interaction, rotation and reopening settings. Captures in this directory come from the emulator.

## Physical Pixel 8a

No physical phone was connected during development. Sustained frame pacing, battery drain and thermal behaviour on Pixel 8a hardware remain unmeasured. Before claiming a hardware performance result, run both 60 and 30 fps settings on the phone, including repeated taps, scrolling and screen-off transitions, with Perfetto or Android Studio profiling.

The renderer has a fixed maximum of sixty trails, reuses its buffers, and stops producing frames when hidden. Those implementation bounds are distinct from real-device performance measurements.
