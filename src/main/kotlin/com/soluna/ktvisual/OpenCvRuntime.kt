package com.soluna.ktvisual

import nu.pattern.OpenCV
import org.opencv.core.Core

object OpenCvRuntime {

    @Volatile
    private var loaded: Boolean = false

    fun ensureLoaded() {
        if (loaded) return

        synchronized(this) {
            if (loaded) return

            /**
             * Java 12+ 场景建议使用 loadLocally。
             * 它会从依赖包中解压当前平台对应的 native library，并通过 System.load 加载。
             */
            OpenCV.loadLocally()

            loaded = true
        }
    }

    fun version(): String {
        ensureLoaded()
        return Core.VERSION
    }
}