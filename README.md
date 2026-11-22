# Smart .Net

Android floating-workspace experiment.

## Current milestone: foundation
- Dark onboarding screen and overlay permission flow.
- Edge handle overlay that can be dragged vertically.
- Sidebar shell that opens and closes.
- GitHub Actions debug APK workflow.

## Important capability note
This milestone does **not** yet embed arbitrary installed apps inside resizable floating windows. Android freeform task support is device/ROM dependent and needs a dedicated integration and on-device validation. The next milestone will evaluate Shizuku/native freeform support before claiming that feature works.

## Build
Open the repository in Android Studio or run the GitHub Actions workflow **Android Debug APK**. The workflow uploads the debug APK as an artifact.
