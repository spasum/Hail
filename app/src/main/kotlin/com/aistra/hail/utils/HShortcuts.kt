package io.spasum.hailshizuku.utils

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.spasum.hailshizuku.BuildConfig
import io.spasum.hailshizuku.HailApp.Companion.app
import io.spasum.hailshizuku.R
import io.spasum.hailshizuku.app.AppInfo
import io.spasum.hailshizuku.app.AppManager
import io.spasum.hailshizuku.app.HailApi
import io.spasum.hailshizuku.app.HailData
import me.zhanghai.android.appiconloader.AppIconLoader
import java.util.concurrent.ConcurrentHashMap

object HShortcuts {
    private const val API_ACTIVITY_CLASS = "io.spasum.hailshizuku.ui.api.ApiActivity"

    private val iconLoader by lazy {
        AppIconLoader(
            app.resources.getDimensionPixelSize(R.dimen.app_icon_size),
            HailData.synthesizeAdaptiveIcons,
            app
        )
    }

    /** Full-colour icon cache keyed by package name — keeps icons available after an app is hidden. */
    private val iconCache = ConcurrentHashMap<String, Bitmap>()

    fun precacheAppIcon(packageName: String) {
        val appInfo = getApplicationInfoForShortcut(packageName) ?: return
        refreshIconCache(packageName, appInfo)
    }

    private fun refreshIconCache(packageName: String, appInfo: ApplicationInfo) {
        runCatching {
            iconCache[packageName] =
                IconPack.loadIcon(appInfo.packageName) ?: iconLoader.loadIcon(appInfo)
        }
    }

    private fun loadAppIconBitmap(packageName: String, appInfo: ApplicationInfo?): Bitmap {
        iconCache[packageName]?.let { return it }
        return runCatching {
            appInfo?.let { IconPack.loadIcon(it.packageName) ?: iconLoader.loadIcon(it) }
                ?: getBitmapFromDrawable(app.packageManager.defaultActivityIcon)
        }.getOrElse {
            getBitmapFromDrawable(app.packageManager.defaultActivityIcon)
        }
    }

    private fun shortcutIdFor(packageName: String): String = packageName.hashCode().toString()

    /**
     * Build a [ShortcutInfoCompat] that reflects the current frozen/unfrozen state of [packageName].
     * Icon is greyscaled when the app is frozen and coloured when it is not, mirroring IceBox.
     */
    private fun buildAppShortcut(packageName: String): ShortcutInfoCompat {
        val appInfo = getApplicationInfoForShortcut(packageName)
        val frozen = AppManager.isAppFrozen(packageName)
        if (!frozen && appInfo != null) refreshIconCache(packageName, appInfo)
        val bmp = runCatching {
            loadAppIconBitmap(packageName, appInfo).let { if (frozen) toGreyscale(it) else it }
        }.getOrElse {
            getBitmapFromDrawable(app.packageManager.defaultActivityIcon).let {
                if (frozen) toGreyscale(it) else it
            }
        }
        val label = appInfo?.loadLabel(app.packageManager) ?: packageName
        // Explicit component + package: picky launchers (MIUI/HyperOS) reject action-only intents
        // with "Ошибка добавления" otherwise.
        val intent = Intent(HailApi.ACTION_LAUNCH).apply {
            component = ComponentName(BuildConfig.APPLICATION_ID, API_ACTIVITY_CLASS)
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra(HailData.KEY_PACKAGE, packageName)
        }
        return ShortcutInfoCompat.Builder(app, shortcutIdFor(packageName))
            .setIcon(IconCompat.createWithBitmap(bmp))
            .setShortLabel(label)
            .setIntent(intent)
            .build()
    }

    /**
     * Register a dynamic shortcut for [packageName] without launching the system pin dialog.
     * Call this when the app is added to Hail's list — the shortcut becomes visible in Hail's
     * long-press launcher menu and any future [updateShortcutIcon] call will propagate state
     * changes to it (and to a pinned copy if the user later pins it).
     */
    fun registerAppShortcut(packageName: String) {
        if (HailData.biometricLogin) return
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, buildAppShortcut(packageName)) }
            .onFailure { HLog.e(it) }
    }

    fun addPinShortcut(icon: Drawable, id: String, label: CharSequence, intent: Intent) {
        addPinShortcut(getDrawableIcon(icon), id, label, intent)
    }

    fun addPinShortcut(appInfo: AppInfo, id: String, label: CharSequence, intent: Intent) {
        val applicationInfo = appInfo.applicationInfo ?: getApplicationInfoForShortcut(appInfo.packageName)
        val bmp = runCatching {
            loadAppIconBitmap(appInfo.packageName, applicationInfo).let {
                if (AppManager.isAppFrozen(appInfo.packageName)) toGreyscale(it) else it
            }
        }.getOrElse {
            getBitmapFromDrawable(app.packageManager.defaultActivityIcon)
        }
        addPinShortcut(IconCompat.createWithBitmap(bmp), id, label, intent)
    }

    /**
     * Explicit user-initiated pin: register as dynamic so later updates land, then ask the launcher
     * to pin it to the home screen. Failures are swallowed — launchers (e.g. MIUI without the
     * "Create shortcuts" permission) show their own toast that we cannot suppress.
     */
    fun addPinShortcutForApp(packageName: String) {
        val shortcut = buildAppShortcut(packageName)
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
        runCatching { ShortcutManagerCompat.updateShortcuts(app, listOf(shortcut)) }
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(app)) {
            runCatching { ShortcutManagerCompat.requestPinShortcut(app, shortcut, null) }
                .onFailure { HLog.e(it) }
        } else HUI.showToast(
            R.string.operation_failed, app.getString(R.string.action_add_pin_shortcut)
        )
    }

    private fun addPinShortcut(icon: IconCompat, id: String, label: CharSequence, intent: Intent) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(app)) {
            val shortcut =
                ShortcutInfoCompat.Builder(app, id).setIcon(icon).setShortLabel(label)
                    .setIntent(intent).build()
            runCatching { ShortcutManagerCompat.requestPinShortcut(app, shortcut, null) }
                .onFailure { HLog.e(it) }
        } else HUI.showToast(
            R.string.operation_failed, app.getString(R.string.action_add_pin_shortcut)
        )
    }

    fun addDynamicShortcut(packageName: String) {
        if (HailData.biometricLogin) return
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(app, buildAppShortcut(packageName))
        }.onFailure { HLog.e(it) }
        addDynamicShortcutAction(HailData.dynamicShortcutAction)
    }

    /**
     * Refresh the icon of the shortcut for [packageName] to reflect [frozen]. This touches both
     * the dynamic copy (if any) and any pinned copy on the home screen, which matches IceBox's
     * colour-on-unfreeze / grey-on-freeze behaviour.
     */
    fun updateShortcutIcon(packageName: String, frozen: Boolean) {
        val shortcut = buildAppShortcut(packageName)
        // updateShortcuts targets existing dynamic OR pinned shortcuts that share the id.
        runCatching { ShortcutManagerCompat.updateShortcuts(app, listOf(shortcut)) }
            .onFailure { HLog.e(it) }
        val isDynamic = runCatching {
            ShortcutManagerCompat.getDynamicShortcuts(app).any { it.id == shortcut.id }
        }.getOrElse { false }
        if (isDynamic) {
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
        }
    }

    fun addDynamicShortcutAction(action: String) {
        if (action == HailData.ACTION_NONE) return
        val id = when (action) {
            HailData.ACTION_FREEZE_ALL -> HailApi.ACTION_FREEZE_ALL
            HailData.ACTION_FREEZE_NON_WHITELISTED -> HailApi.ACTION_FREEZE_NON_WHITELISTED
            HailData.ACTION_LOCK -> HailApi.ACTION_LOCK
            HailData.ACTION_LOCK_FREEZE -> HailApi.ACTION_LOCK_FREEZE
            else -> HailApi.ACTION_UNFREEZE_ALL
        }
        val icon = when (action) {
            HailData.ACTION_FREEZE_ALL, HailData.ACTION_FREEZE_NON_WHITELISTED -> R.drawable.ic_round_frozen_shortcut
            HailData.ACTION_LOCK, HailData.ACTION_LOCK_FREEZE -> R.drawable.ic_outline_lock_shortcut
            else -> R.drawable.ic_round_unfrozen_shortcut
        }
        val label = when (action) {
            HailData.ACTION_FREEZE_ALL -> R.string.action_freeze_all
            HailData.ACTION_FREEZE_NON_WHITELISTED -> R.string.action_freeze_non_whitelisted
            HailData.ACTION_LOCK -> R.string.action_lock
            HailData.ACTION_LOCK_FREEZE -> R.string.action_lock_freeze
            else -> R.string.action_unfreeze_all
        }
        val shortcut = ShortcutInfoCompat.Builder(app, id).setIcon(
            getDrawableIcon(AppCompatResources.getDrawable(app, icon)!!)
        ).setShortLabel(app.getString(label)).setIntent(Intent(id).apply {
            component = ComponentName(BuildConfig.APPLICATION_ID, API_ACTIVITY_CLASS)
            setPackage(BuildConfig.APPLICATION_ID)
        }).build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
            .onFailure { HLog.e(it) }
    }

    fun removeAllDynamicShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(app)
    }

    private fun toGreyscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().also { it.setSaturation(0f) })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun getDrawableIcon(drawable: Drawable): IconCompat =
        IconCompat.createWithBitmap(getBitmapFromDrawable(drawable))

    private fun getApplicationInfoForShortcut(packageName: String): ApplicationInfo? =
        HPackages.getApplicationInfoOrNull(packageName)
            ?: HPackages.getUnhiddenPackageInfoOrNull(packageName)?.applicationInfo

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        val defaultSize = app.resources.getDimensionPixelSize(R.dimen.app_icon_size)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: defaultSize
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: defaultSize
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            with(Canvas(it)) {
                drawable.setBounds(0, 0, width, height)
                drawable.draw(this)
            }
        }
    }
}
