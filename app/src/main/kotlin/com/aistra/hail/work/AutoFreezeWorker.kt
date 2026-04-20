package io.spasum.hailshizuku.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.spasum.hailshizuku.HailApp.Companion.app
import io.spasum.hailshizuku.app.AppInfo
import io.spasum.hailshizuku.app.AppManager
import io.spasum.hailshizuku.app.HailData
import io.spasum.hailshizuku.services.AutoFreezeService
import io.spasum.hailshizuku.utils.HSystem

class AutoFreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if ((inputData.getBoolean(HailData.ACTION_LOCK, true)
                    && HSystem.isInteractive(applicationContext))
            || isSkipWhileCharging(applicationContext)
        ) return Result.success() // Not stopping the AutoFreezeService here. The worker will run at some point. Then we'll stop the Service
        val checkedList = HailData.checkedList.filter { !isSkipApp(applicationContext, it) }
        val result = AppManager.setListFrozen(true, *checkedList.toTypedArray())
        return if (result == null) {
            Result.failure()
        } else {
            app.setAutoFreezeService()
            Result.success()
        }
    }

    private fun isSkipWhileCharging(context: Context): Boolean =
        HailData.skipWhileCharging && HSystem.isCharging(context)

    private fun isSkipApp(context: Context, appInfo: AppInfo): Boolean =
        AppManager.isAppFrozen(appInfo.packageName) || (HailData.skipForegroundApp && HSystem.isForegroundApp(
            context, appInfo.packageName
        )) || (HailData.skipNotifyingApp && AutoFreezeService.instance.activeNotifications.any { it.packageName == appInfo.packageName }) || appInfo.whitelisted
}