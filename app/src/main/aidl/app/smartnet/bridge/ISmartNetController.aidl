package app.smartnet.bridge;

interface ISmartNetController {
    boolean ping();
    String readFreeformSettings();
    String enableFreeform();
    boolean restoreFreeform(String snapshot);
    String launchApp(String flattenedComponent, int left, int top, int right, int bottom);
    boolean resizeTask(int taskId, int left, int top, int right, int bottom);
    boolean minimizeTask(int taskId);
    boolean closeTask(int taskId);
    String dumpVisibleTasks();
}
