package io.spasum.hailshizuku.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.spasum.hailshizuku.HailApp.Companion.app
import io.spasum.hailshizuku.utils.HShizuku.setAppRestricted
import io.spasum.hailshizuku.utils.HTarget

class UnsuspendedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PACKAGE_UNSUSPENDED_MANUALLY) runCatching {
            if (HTarget.P) setAppRestricted(
                intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)!!,
                false
            )
            app.setAutoFreezeService()
        }
    }

    companion object {
        private const val ACTION_PACKAGE_UNSUSPENDED_MANUALLY =
            "android.intent.action.PACKAGE_UNSUSPENDED_MANUALLY"
    }
}