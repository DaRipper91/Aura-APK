package com.aura.app

class ShellBridge {

    interface ShellCallback {
        fun onOutput(line: String)
        fun onComplete(exitCode: Int)
    }

    fun isShizukuAvailable(): Boolean {
        return false
    }

    fun execute(command: String, callback: ShellCallback) {
        callback.onOutput("[ERROR] Advanced Mode Temporarily Unavailable")
        callback.onComplete(-1)
    }
}
