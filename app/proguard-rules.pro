# Shizuku instantiates the UserService by class name in a separate process.
-keep class app.smartnet.bridge.SmartNetUserService { *; }
-keep interface app.smartnet.bridge.ISmartNetController { *; }
-keep interface app.smartnet.bridge.ISmartNetController$* { *; }
-keep class rikka.shizuku.** { *; }
