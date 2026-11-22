# Research notes — Android freeform approaches

Research snapshot: 22 November 2026.

## 1. sunshine0523/Mi-Freeform

Repository: https://github.com/sunshine0523/Mi-Freeform

The inspected v3 codebase is not simply an overlay launcher. Its `freeform-server` module defines a system-server integration and custom freeform-display interfaces. The app side asks the custom Binder service to create/manage freeform tasks, while display surfaces are rendered through Android graphics surfaces. The inspected branch contains Zygote/Riru/Zygisk packaging and uses a custom `mi_freeform` system service. This is a very different installation/privilege model from a normal standalone APK with Shizuku alone.

Key inspected files:
- `app/src/main/java/com/sunshine/freeform/MiFreeformServiceManager.kt`
- `freeform-server/src/main/java/io/sunshine0523/freeform/service/MiFreeformService.java`
- `freeform-server/src/main/java/io/sunshine0523/freeform/service/MiFreeformServiceHolder.java`
- `freeform-server/src/main/java/com/android/server/display/MiFreeformDisplayAdapter.java`

## 2. DtHnAme/Mi-FreeForm (Flyme-style edition)

Repository: https://github.com/DtHnAme/Mi-FreeForm

This variant explicitly uses Shizuku/Sui. Its inspected source creates a `VirtualDisplay`, renders its content through a `TextureView`, launches app activities onto that display using privileged ActivityManager calls, and injects touch events into the display. This is the stronger architectural reference for app-content embedding inside a custom window. It is also substantially more involved than a conventional overlay: surface lifecycle, rotation, injected input, virtual-display ownership, keyboard/IME behavior, and secure app content all need careful handling.

Key inspected files:
- `app/src/main/java/com/sunshine/freeform/app/MiFreeform.kt`
- `app/src/main/java/com/sunshine/freeform/ui/freeform/FreeformService.kt`
- `app/src/main/java/com/sunshine/freeform/ui/freeform/FreeformView.kt`
- `app/src/main/java/com/sunshine/freeform/service/ControlService.kt`

## 3. Aypex/android-tiling-wm

Repository: https://github.com/Aypex/android-tiling-wm

This project uses Shizuku UserService with `IActivityTaskManager` access, calls task resize/windowing-mode methods, launches activities with freeform launch options and bounds, and uses AccessibilityService window events to reconcile visible tasks. Its README describes the project as a work-in-progress and says its tested environment was a Pixel 10 Pro XL on Android 17; smaller displays may not support its intended native window layout. It also notes hidden-API changes and a lack of native window chrome on its tested build.

Key inspected files:
- `app/src/main/kotlin/dev/atwm/tilingwm/service/WindowTilingServiceImpl.kt`
- `app/src/main/kotlin/dev/atwm/tilingwm/service/TilingAccessibilityService.kt`
- `app/src/main/kotlin/dev/atwm/tilingwm/util/AppLauncher.kt`
- `docs/research-spec.md`

## 4. FreeformShell

Repository: https://github.com/bravoyush/FreeformShell

Its README describes an experimental Shizuku-based native-window helper for Android 11–15+, with an AccessibilityService and custom window frames, drag/resize controls, minimize bubble, and diagnostics. It emphasizes that native freeform support remains OEM-dependent and that the app is not a standalone desktop launcher.

## Why alpha 0.1 uses native freeform first

Smart .Net alpha 0.1 starts with Android native freeform because this is the lightest path to validate on a normal phone: Shizuku requests a real task in windowing mode 5 and resizes the task; Smart .Net draws only its own control surfaces around the system task. This is easier to diagnose and is less demanding than rendering arbitrary applications into a Smart .Net-owned virtual display.

This is a deliberate first backend, not a claim that the virtual-display technique is unimportant. If the target XOS build rejects native freeform on the actual phone, a later virtual-display backend is the next research path. It must be implemented as a separate backend rather than pretending that a regular overlay is equivalent.

## Compatibility limits to verify on the target phone

- Whether XOS 15 enables or accepts native freeform mode 5 on the built-in display.
- Whether `am start --windowingMode 5` succeeds for a common resizable application.
- Whether Shizuku shell UID can enumerate visible tasks and access `IActivityTaskManager` methods on that XOS build.
- Whether `resizeTask`, task removal, and task move-to-back work with the OEM's method signatures.
- Whether the device allows more than one freeform task and how it behaves under memory pressure.
- Whether custom overlay titlebars obscure the OEM's own caption controls.

No claim of target-device compatibility should be made until those checks pass on the actual Infinix Hot 60i.

## 5. Target-device warning: Infinix HOT 60i (X6728)

The target phone recorded in this project's planning context is the regular HOT 60i, model X6728 (4G), running Android 15 / XOS 15.1.1. Infinix's published product specification identifies X6728 as the LTE model. Independent reviews of the regular HOT 60i report that XOS omits split-screen, Smart Panel, and native floating-window features on this entry-level model. Some reports describing floating-window support refer to the distinct HOT 60i 5G variant, so their feature claims should not be transferred to X6728.

This matters because AOSP documents that freeform windowing is a device/OEM feature: the product must enable multi-window support and the freeform-window-management configuration/feature. A third-party APK cannot grant itself this system capability by requesting overlay access or writing a settings value. Therefore native freeform on X6728 is a high-risk compatibility assumption and must be tested before claiming success.

The current alpha keeps the native-freeform attempt as a diagnostic path and reports a failure rather than showing a fake application surface. The next architecture milestone for X6728 should be a separate Shizuku virtual-display backend, based on the Flyme-style Mi-FreeForm research, after validating virtual-display creation, input forwarding, keyboard/IME, protected content, rotation, and memory/performance on the actual phone. That backend is not implemented in alpha 0.1.

Evidence: Infinix HOT 60i X6728 specifications (https://wap.gulf.infinixmobility.com/specs/hot-60i); Android AOSP multi-window requirements (https://source.android.com/docs/core/display/multi-window); reviews of HOT 60i XOS 15.1.1 (https://ai-trend.ru/obzor-smartfona-infinix-hot-60i/ and https://club.dns-shop.ru/review/t-78-smartfonyi/154429-obzor-infinix-hot-60i-ul-tratonkii-i-mnogofunkcionalnyii-budje/); comparison noting distinct 4G and 5G models (https://howpremium.com/infinix-hot-60i/).
