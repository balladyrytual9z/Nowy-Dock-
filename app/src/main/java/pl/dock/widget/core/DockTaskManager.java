
package pl.dock.widget.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central executors for Dock 2.0 â avoid spawning ad-hoc threads.
 */
public final class DockTaskManager {

    private static final DockTaskManager I = new DockTaskManager();

    public static DockTaskManager get() {
        return I;
    }

    private final ExecutorService network;
    private final ExecutorService images;
    private final ScheduledExecutorService scheduler;
    private final Handler main;

    private DockTaskManager() {
        network = Executors.newFixedThreadPool(2, named("dock-net"));
        images = Executors.newSingleThreadExecutor(named("dock-img"));
        scheduler = Executors.newSingleThreadScheduledExecutor(named("dock-sched"));
        main = new Handler(Looper.getMainLooper());
    }

    private static ThreadFactory named(final String prefix) {
        final AtomicInteger n = new AtomicInteger(1);
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
    }

    public ExecutorService network() { return network; }
    public ExecutorService images() { return images; }
    public ScheduledExecutorService scheduler() { return scheduler; }
    public Handler main() { return main; }

    public void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }

    public void postMain(Runnable r) {
        main.post(r);
    }

    public void postMainDelayed(Runnable r, long delayMs) {
        main.postDelayed(r, delayMs);
    }
}






