package io.spasum.hailshizuku.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.spasum.hailshizuku.work.HWork

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            HWork.setAutoFreeze(true)
        }
    }
}