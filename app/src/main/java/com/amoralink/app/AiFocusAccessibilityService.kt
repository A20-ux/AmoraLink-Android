package com.amoralink.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class AiFocusAccessibilityService : AccessibilityService() {

    private var lastLaunchTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        if (!AiControlManager.isLockRequested(this)) return

        val target = AiControlManager.getLockedPackage(this) ?: return
        if (event.packageName?.toString() == target) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchTime < 2500L) return

        val launchIntent = packageManager.getLaunchIntentForPackage(target) ?: return

        lastLaunchTime = now
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        try {
            startActivity(launchIntent)
        } catch (_: Exception) {
            // 系统‮能可‬限制‮台后‬启动；不尝‮绕试‬过系统限制。
        }
    }

    override fun onInterrupt() {
        // 系‮中统‬断无障‮服碍‬务时‮执不‬行额外操作。
    }
}
