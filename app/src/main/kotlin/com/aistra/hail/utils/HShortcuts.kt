package io.spasum.hailshizuku.utils

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
import io.spasum.hailshizuku.HailApp.Companion.app
import io.spasum.hailshizuku.R
import io.spasum.hailshizuku.app.AppInfo
import io.spasum.hailshizuku.app.AppManager
import io.spasum.hailshizuku.app.HailApi
import io.spasum.hailshizuku.app.HailData
import me.zhanghai.android.appiconloader.AppIconLoader

object HShortcuts {
    private val iconLoader by lazy {
        AppIconLoader(
            app.resources.getDimensionPixelSize(R.dimen.app_icon_size),
            HailData.synthesizeAdaptiveIcons,
            app
        )
    }

    fun addPinShortcut(icon: Drawable, id: String, label: CharSequence, intent: Intent) {
        addPinShortcut(getDrawableIcon(icon), id, label, intent)
    }

    fun addPinShortcut(appInfo: AppInfo, id: String, label: CharSequence, intent: Intent) {
        val applicationInfo = appInfo.applicationInfo ?: getApplicationInfoForShortcut(appInfo.packageName)
        applicationInfo?.let {
            var bmp = IconPack.loadIcon(it.packageName) ?: iconLoader.loadIcon(it)
            if (AppManager.isAppFrozen(appInfo.packageName)) bmp = toGreyscale(bmp)
            addPinShortcut(IconCompat.createWithBitmap(bmp), id, label, intent)
        } ?: run {
            addPinShortcut(app.packageManager.defaultActivityIcon, id, label, intent)
        }
    }

    fun addPinShortcutForApp(packageName: String) {
        val appInfo = getApplicationInfoForShortcut(packageName) ?: return
        val frozen = AppManager.isAppFrozen(packageName)
        val bmp = runCatching {
            (IconPack.loadIcon(appInfo.packageName) ?: iconLoader.loadIcon(appInfo)).let {
                if (frozen) toGreyscale(it) else it
            }
        }.getOrElse {
            getBitmapFromDrawable(app.packageManager.defaultActivityIcon).let {
                if (frozen) toGreyscale(it) else it
            }
        }
        val icon = IconCompat.createWithBitmap(bmp)
        val label = appInfo.loadLabel(app.packageManager)
        val id = packageName.hashCode().toString()
        val intent = HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, packageName)
        val shortcut = ShortcutInfoCompat.Builder(app, id).setIcon(icon).setShortLabel(label).setIntent(intent).build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
        // Sync icon on any existing pinned shortcut immediately
        runCatching { ShortcutManagerCompat.updateShortcuts(app, listOf(shortcut)) }
        addPinShortcut(icon, id, label, intent)
    }

    private fun addPinShortcut(icon: IconCompat, id: String, label: CharSequence, intent: Intent) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(app)) {
            val shortcut =
                ShortcutInfoCompat.Builder(app, id).setIcon(icon).setShortLabel(label)
                    .setIntent(intent).build()
            ShortcutManagerCompat.requestPinShortcut(app, shortcut, null)
        } else HUI.showToast(
            R.string.operation_failed, app.getString(R.string.action_add_pin_shortcut)
        )
    }

    fun addDynamicShortcut(packageName: String) {
        if (HailData.biometricLogin) return
        val applicationInfo = getApplicationInfoForShortcut(packageName)
        var bmp = applicationInfo?.let {
            IconPack.loadIcon(it.packageName) ?: iconLoader.loadIcon(it)
        } ?: getBitmapFromDrawable(app.packageManager.defaultActivityIcon)
        if (AppManager.isAppFrozen(packageName)) bmp = toGreyscale(bmp)
        val shortcut =
            ShortcutInfoCompat.Builder(app, packageName.hashCode().toString())
                .setIcon(IconCompat.createWithBitmap(bmp))
                .setShortLabel(applicationInfo?.loadLabel(app.packageManager) ?: packageName)
                .setIntent(HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, packageName))
                .build()
        ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
        addDynamicShortcutAction(HailData.dynamicShortcutAction)
    }

    fun updateShortcutIcon(packageName: String, frozen: Boolean) {
        val id = packageName.hashCode().toString()
        val appInfo = getApplicationInfoForShortcut(packageName)
        val bmp = runCatching {
            (appInfo?.let { IconPack.loadIcon(it.packageName) ?: iconLoader.loadIcon(it) }
                ?: getBitmapFromDrawable(app.packageManager.defaultActivityIcon)).let {
                if (frozen) toGreyscale(it) else it
            }
        }.getOrElse {
            HLog.e(it)
            getBitmapFromDrawable(app.packageManager.defaultActivityIcon).let { fallback ->
                if (frozen) toGreyscale(fallback) else fallback
            }
        }
        val shortcut = ShortcutInfoCompat.Builder(app, id)
            .setIcon(IconCompat.createWithBitmap(bmp))
            .setShortLabel(appInfo?.loadLabel(app.packageManager) ?: packageName)
            .setIntent(HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, packageName))
            .build()
        val isDynamic = runCatching {
            ShortcutManagerCompat.getDynamicShortcuts(app).any { it.id == id }
        }.getOrElse { false }
        if (isDynamic) {
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
        }
        // Always update pinned shortcuts on the home screen
        runCatching { ShortcutManagerCompat.updateShortcuts(app, listOf(shortcut)) }
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
        ).setShortLabel(app.getString(label)).setIntent(Intent(id)).build()
        ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
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
