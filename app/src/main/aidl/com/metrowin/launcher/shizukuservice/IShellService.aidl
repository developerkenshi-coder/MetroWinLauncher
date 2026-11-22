package com.metrowin.launcher.shizukuservice;

/**
 * What the launcher asks of its own process that Shizuku starts with the rights of the shell (docs/shizuku-user-service.md). Every answer is
 * small: a binder transaction holds about 1 MB, so a picture goes as a short JPEG, never as a bitmap.
 */
interface IShellService {
    /** The code Shizuku reserves for "stop": it calls it when the launcher unbinds. */
    void destroy() = 16777114;

    /** The tasks Android keeps, newest first, at most [max], each as "taskId|package|1 if it runs". Empty when Android's hidden API is not as expected. */
    List<String> recentTasks(int max) = 1;

    /** A JPEG of the picture Android keeps of task [taskId], no wider than [maxWidth] pixels; empty when there is none (an app that hides its screen, a task that never drew). */
    byte[] snapshot(int taskId, int maxWidth) = 2;
}
