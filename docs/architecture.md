# Smart .Net architecture — alpha 0.1

## UI layers

- `MainActivity`: animated splash, authorization checklist, home screen, Start flow.
- `SettingsActivity`: persisted appearance, workspace, and freeform compatibility settings.
- `AppPickerActivity`: launcher-activity enumeration and favorite shortcut selection.
- `AboutActivity`: product and compatibility information.
- `OverlayService`: persistent foreground service that owns the system overlay windows.
- `SmartOverlayController`: edge handle and frosted sidebar; app selection dispatches a real task launch request.
- `WindowChromeManager`: titlebar and resize grip overlays that send move/resize/minimize/close requests to the real Android task.
- `ShizukuBridge`: binds the user-authorized Shizuku UserService and shares the AIDL controller with the activity/service process.
- `SmartNetUserService`: shell-UID task-manager bridge for freeform launch, task enumeration, resize, minimize, close, and reversible freeform settings.

## Window launch sequence

```text
Tap app shortcut
    -> close sidebar
    -> calculate portrait / landscape window bounds
    -> ISmartNetController.launchApp(component, bounds)
    -> Shizuku UserService runs `am start --windowingMode 5`
    -> inspect visible task list via IActivityTaskManager
    -> request freeform mode and resizeTask(taskId, bounds)
    -> verify the task is reported in windowing mode 5
    -> attach Smart .Net titlebar + resize grip overlays
```

A failure before verification must not attach a fake titlebar. The app can report a reason and leave the real app in the system's normal state if the OEM rejected the operation.

## Performance choices

- App icons are enumerated only when opening the app picker/sidebar; favorite shortcuts are persisted locally.
- Drag/resize overlays update locally immediately. Remote task resize requests are coalesced to reduce Binder traffic during touch movement.
- The sidebar adapts its grid from 3 columns in portrait to 5 in landscape.
- The UI has no artificial app-window count cap. Android task limits and RAM remain the actual bounds.

## Authorization model

Required:
- Shizuku running and permission granted.
- `SYSTEM_ALERT_WINDOW` special access granted by the user.

Optional:
- `POST_NOTIFICATIONS` on Android 13+ for visible foreground-service status.
- Battery optimization/autostart adjustment in OEM Settings where needed.

The project intentionally does not request Accessibility, notification-listener, contacts, usage-access, or other broad permissions in this alpha because the current feature set does not need them.

## System setting safety

The user is shown a confirmation dialog before changing Android's global freeform switches. The user-service returns the previous values for:
- `enable_freeform_support`
- `force_resizable_activities`

The app stores the snapshot locally and exposes an explicit restore action in Settings. This cannot guarantee an OEM will honor the settings.
