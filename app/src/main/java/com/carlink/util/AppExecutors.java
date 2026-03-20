package com.carlink.util;

/**
   * AppExecutors - Centralized Thread Management for Carlink Android Plugin
   *
   * PURPOSE:
   * Manages concurrent execution contexts for H.264 video decoding
   * during Android Auto/CarPlay projection with the CPC200-CCPA adapter.
   *
   * THREAD POOL:
   * - mediaCodec1: Multi-threaded pool for H.264 decode pipeline
   *
   * OPTIMIZATION:
   * MediaCodec thread pool is sized dynamically at runtime:
   * - Uses available processor count instead of a single target platform
   * - THREAD_PRIORITY_DISPLAY for low-latency video rendering
   * - Queue depth scales with CPU count to stay conservative on Android 9/T7 hosts
   */
import android.os.Process;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AppExecutors
{
    // Optimized thread pool for the current device at runtime
    private static class OptimizedMediaCodecExecutor implements Executor {
        private final ThreadPoolExecutor executor;
        private final int androidPriority;

        // Track which threads have had priority set (thread-local for efficiency)
        private final ThreadLocal<Boolean> prioritySet = ThreadLocal.withInitial(() -> false);

        private OptimizedMediaCodecExecutor(String executorName, int androidPriority) {
            int numberOfCores = Runtime.getRuntime().availableProcessors();
            int queueCapacity = Math.max(32, numberOfCores * 32);

            // Create optimized thread pool based on Android best practices
            // Core pool: half cores, Max pool: all cores for burst recovery after frame drops
            this.executor = new ThreadPoolExecutor(
                    Math.max(1, numberOfCores / 2), // corePoolSize - utilize half cores initially
                    numberOfCores, // maximumPoolSize - can scale to all cores under load
                    60L, // keepAliveTime - standard Android recommendation
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(queueCapacity),
                    r -> {
                        Thread t = new Thread(r, executorName);
                        return t;
                    }
            );
            this.executor.allowCoreThreadTimeOut(true); // Allow core threads to timeout for efficiency
            this.androidPriority = androidPriority;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            executor.execute(() -> {
                // Set thread priority only once per thread (avoids syscall overhead)
                if (!prioritySet.get()) {
                    Process.setThreadPriority(androidPriority);
                    prioritySet.set(true);
                }
                command.run();
            });
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    private final OptimizedMediaCodecExecutor mediaCodec1;

    public AppExecutors()
    {
        // MediaCodec executor optimized according to Android MediaCodec best practices
        mediaCodec1 = new OptimizedMediaCodecExecutor("MediaCodec", Process.THREAD_PRIORITY_DISPLAY);
    }

    public Executor mediaCodec1() {
        return mediaCodec1;
    }

    /**
     * Shutdown executor following Android threading guidelines.
     * Should be called when the plugin is detached to prevent memory leaks.
     */
    public void shutdown() {
        try {
            mediaCodec1.shutdown();
        } catch (Exception e) {
            android.util.Log.w("AppExecutors", "Error during shutdown: " + e.getMessage());
        }
    }
}
