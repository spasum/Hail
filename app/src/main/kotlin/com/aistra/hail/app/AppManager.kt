package io.spasum.hailshizuku.app

import android.content.Intent
import io.spasum.hailshizuku.BuildConfig
import io.spasum.hailshizuku.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object AppManager {
    // Временный кэш для решения гонки состояний при асинхронном обновлении Package Manager
    private val transientStates = ConcurrentHashMap<String, Boolean>()

    val lockScreen: Boolean
        get() = when {
            HailData.workingMode.startsWith(HailData.SHIZUKU) -> HShizuku.lockScreen
            else -> false
        }

    fun isAppFrozen(packageName: String): Boolean {
        // Отдаем приоритет ожидаемому состоянию, если ОС еще не успела его обновить
        transientStates[packageName]?.let { return it }

        return when {
            HailData.workingMode.endsWith(HailData.STOP) -> HPackages.isAppStopped(packageName)
            HailData.workingMode.endsWith(HailData.DISABLE) -> HPackages.isAppDisabled(packageName)
            HailData.workingMode.endsWith(HailData.HIDE) -> HPackages.isAppHidden(packageName)
            HailData.workingMode.endsWith(HailData.SUSPEND) -> HPackages.isAppSuspended(packageName)
            else -> HPackages.isAppDisabled(packageName)
                    || HPackages.isAppHidden(packageName)
                    || HPackages.isAppSuspended(packageName)
        }
    }

    fun setListFrozen(frozen: Boolean, vararg appInfo: AppInfo): String? {
        val excludeMe = appInfo.filter { it.packageName != BuildConfig.APPLICATION_ID }
        val names = mutableListOf<String>()
        var denied = false
        when (HailData.workingMode) {
            else -> {
                excludeMe.forEach {
                    when {
                        setAppFrozen(it.packageName, frozen) -> names.add(it.name.toString())
                        it.applicationInfo != null -> denied = true
                    }
                }
            }
        }
        return if (denied && names.isEmpty()) null else names.joinToString("\n")
    }

    fun setAppFrozen(packageName: String, frozen: Boolean): Boolean {
        if (frozen) HShortcuts.precacheAppIcon(packageName)

        // Кэшируем ожидаемое состояние до отправки команд
        transientStates[packageName] = frozen

        val result = packageName != BuildConfig.APPLICATION_ID && when (HailData.workingMode) {
            HailData.MODE_SHIZUKU_STOP -> !frozen || HShizuku.forceStopApp(packageName)
            HailData.MODE_SHIZUKU_DISABLE -> HShizuku.setAppDisabled(packageName, frozen)
            HailData.MODE_SHIZUKU_HIDE -> HShizuku.setAppHidden(packageName, frozen)
            HailData.MODE_SHIZUKU_SUSPEND -> HShizuku.setAppSuspended(packageName, frozen)
            else -> false
        }

        if (result) {
            HShortcuts.updateShortcutIcon(packageName, frozen)
            
            // Даем ОС 2.5 секунды на обновление статуса, затем сбрасываем кэш
            CoroutineScope(Dispatchers.Default).launch {
                delay(2500)
                // Удаляем только если состояние не было перезаписано новым вызовом
                if (transientStates[packageName] == frozen) {
                    transientStates.remove(packageName)
                }
            }
        } else {
            // Откат кэша при неудаче
            transientStates.remove(packageName)
        }

        return result
    }

    fun uninstallApp(packageName: String): Boolean {
        when {
            HailData.workingMode.startsWith(HailData.SHIZUKU) ->
                if (HShizuku.uninstallApp(packageName)) return true
        }
        HUI.startActivity(Intent.ACTION_DELETE, HPackages.packageUri(packageName))
        return false
    }

    fun reinstallApp(packageName: String): Boolean = when {
        HailData.workingMode.startsWith(HailData.SHIZUKU) -> HShizuku.reinstallApp(packageName)
        else -> false
    }

    suspend fun execute(command: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        when {
            HailData.workingMode.startsWith(HailData.SHIZUKU) -> HShizuku.execute(command)
            else -> 0 to null
        }
    }
}
