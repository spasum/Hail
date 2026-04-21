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

    /** Used as the shortcut's owning activity. Must be the LAUNCHER activity — OEM launchers
     *  (Vivo/BBK/OriginOS) reject shortcuts bound to activities with excludeFromRecents=true or
     *  singleInstance launch mode, which ApiActivity has. The shortcut *intent* still targets
     *  ApiActivity explicitly; this only affects launcher grouping / validation. */
    private const val MAIN_ACTIVITY_CLASS = "io.spasum.hailshizuku.ui.main.MainActivity"

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

    /** Pinned-shortcut ID is distinct from the dynamic-shortcut ID so BBK / Vivo launcher doesn't
     * reject the pin request as a duplicate of an existing dynamic shortcut. */
    private fun pinnedShortcutIdFor(packageName: String): String = "pin_" + shortcutIdFor(packageName)

    /** Shortcut short-label per Google guideline: ≤10 chars. Some OEM launchers (Vivo/BBK) fail the
     *  pin outright when the short label is longer. Full name still lives in long label. */
    private fun truncateShortLabel(label: CharSequence): CharSequence {
        val s = label.toString()
        return if (s.length <= 10) s else s.substring(0, 10)
    }

    /**
     * Build the launch intent that will be wrapped in a shortcut. We use a custom action
     * (`${applicationId}.action.LAUNCH`) with extras rather than a data-URI intent, because
     * some OEM launchers (Vivo / OriginOS 5) reject URI-based shortcut intents at pin-persist
     * time with an "Ошибка добавления" toast. Extras-based intents serialize more reliably.
     *
     * An explicit component is set so the intent resolves without relying on intent-filter
     * matching. CATEGORY_DEFAULT is intentionally omitted — it is unnecessary for explicit-
     * component intents and Vivo/OriginOS launchers reject shortcut intents that carry non-
     * standard categories during their pin-time validation step.
     *
     * FLAG_ACTIVITY_CLEAR_TASK is intentionally omitted — for singleInstance activities it can
     * cause Vivo launcher validation to fail; FLAG_ACTIVITY_NEW_TASK alone is sufficient.
     */
    fun buildLaunchIntent(packageName: String, tag: String? = null): Intent =
        Intent(HailApi.ACTION_LAUNCH).apply {
            component = ComponentName(BuildConfig.APPLICATION_ID, API_ACTIVITY_CLASS)
            setPackage(BuildConfig.APPLICATION_ID)
            putExtra(HailData.KEY_PACKAGE, packageName)
            if (tag != null) putExtra(HailData.KEY_TAG, tag)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Build a [ShortcutInfoCompat] that reflects the current frozen/unfrozen state of [packageName].
     * Icon is greyscaled when the app is frozen and coloured when it is not, mirroring IceBox.
     *
     * The icon is wrapped via [IconCompat.createWithAdaptiveBitmap] because strict OEM launchers
     * (OriginOS 5 on Vivo, HyperOS) reject legacy-style bitmap icons with their own "add failed"
     * toast when presented in the pin-shortcut dialog. [setActivity] binds the shortcut to
     * [ApiActivity] so the launcher's intent resolution has a clear owner, and [setLongLived]
     * tells the framework this shortcut is intended to survive beyond a single app session.
     */
    private fun buildAppShortcut(packageName: String, forPin: Boolean = false): ShortcutInfoCompat {
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
        val id = if (forPin) pinnedShortcutIdFor(packageName) else shortcutIdFor(packageName)
        return ShortcutInfoCompat.Builder(app, id)
            .setIcon(IconCompat.createWithAdaptiveBitmap(bmp))
            .setShortLabel(truncateShortLabel(label))
            .setLongLabel(label)
            .setActivity(ComponentName(BuildConfig.APPLICATION_ID, MAIN_ACTIVITY_CLASS))
            .setLongLived(true)
            .setIntent(buildLaunchIntent(packageName))
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
        addPinShortcut(getDrawableIcon(icon), id, label, normalizeLauncherIntent(intent))
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
        addPinShortcut(IconCompat.createWithAdaptiveBitmap(bmp), id, label, normalizeLauncherIntent(intent))
    }

    /**
     * Explicit user-initiated pin invoked from the Home tab's long-press menu.
     *
     * IMPORTANT: the pinned shortcut intentionally uses the SAME ID as the dynamic shortcut
     * ([shortcutIdFor], not [pinnedShortcutIdFor]).  Vivo OriginOS and Xiaomi MIUI/HyperOS
     * launchers do NOT honour [ShortcutManagerCompat.updateShortcuts] for pinned shortcuts —
     * they cache the icon at pin time and never refresh it.  However, they DO refresh a pinned
     * shortcut's icon when [ShortcutManagerCompat.pushDynamicShortcut] is called with the same
     * shortcut ID.  Sharing the ID between dynamic and pinned shortcuts is therefore the only
     * reliable way to get live colour/grey updates on the home screen.
     *
     * To prevent Vivo/BBK from rejecting [requestPinShortcut] with a "duplicate already exists"
     * error, the dynamic shortcut is removed before the pin request and re-registered immediately
     * after so both entries coexist with the same ID from that point on.
     */
    fun addPinShortcutForApp(packageName: String) {
        val shortcut = buildAppShortcut(packageName, forPin = false)  // ID = shortcutIdFor (hash)
        // Remove the dynamic shortcut temporarily — Vivo/BBK rejects requestPinShortcut when
        // a dynamic shortcut with the same ID already exists.
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(shortcut.id)) }
        val bmp = iconCache[packageName] ?: loadAppIconBitmap(packageName, getApplicationInfoForShortcut(packageName))
        val label = shortcut.shortLabel ?: packageName
        requestPinShortcutAllLaunchers(shortcut, label, bmp, buildLaunchIntent(packageName))
        // Re-register as dynamic so long-press launcher menu and the pinned home-screen copy
        // both use ID shortcutIdFor(pkg). Future pushDynamicShortcut() calls update both.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
            .onFailure { HLog.e(it) }
    }

    private fun addPinShortcut(icon: IconCompat, id: String, label: CharSequence, intent: Intent) {
        val shortcut = ShortcutInfoCompat.Builder(app, id)
            .setIcon(icon)
            .setShortLabel(label)
            .setLongLabel(label)
            .setActivity(ComponentName(BuildConfig.APPLICATION_ID, MAIN_ACTIVITY_CLASS))
            .setLongLived(true)
            .setIntent(intent)
            .build()
        val bmp = bitmapFromIconCompat(icon) ?: getBitmapFromDrawable(app.packageManager.defaultActivityIcon)
        requestPinShortcutAllLaunchers(shortcut, label, bmp, intent)
    }

    /**
     * Pin a shortcut to the home screen using both the modern API and the legacy broadcast.
     *
     * [ShortcutManagerCompat.requestPinShortcut] is tried first — it shows the system dialog on
     * stock Android 8+ launchers.  However, Vivo / BBK / OriginOS 5 launchers often return `true`
     * (accepting the request and showing a dialog) and then silently discard the shortcut, so we
     * ALWAYS also send the legacy INSTALL_SHORTCUT broadcast.  On Android 8+ AOSP (Pixel, etc.)
     * Launcher3 ignores INSTALL_SHORTCUT broadcasts entirely, so there is no duplicate risk on
     * stock Android while OEM launchers receive the broadcast as a reliable fallback.
     */
    private fun requestPinShortcutAllLaunchers(
        shortcut: ShortcutInfoCompat,
        label: CharSequence,
        bitmap: Bitmap,
        launchIntent: Intent,
    ) {
        var lastError: Throwable? = null
        var modernPinned = false
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(app)) {
            modernPinned = runCatching { ShortcutManagerCompat.requestPinShortcut(app, shortcut, null) }
                .onFailure { lastError = it; HLog.e(it) }
                .getOrDefault(false)
        }
        // Send legacy broadcast unconditionally — OEM launchers process it even when they also
        // accepted the modern pin request; AOSP ignores it, so no duplicate on stock Android.
        val legacySent = sendLegacyInstallShortcutBroadcast(label, bitmap, launchIntent)
        if (modernPinned || legacySent) return
        val detail = lastError?.let { it.message ?: it.javaClass.simpleName }
            ?: app.getString(R.string.action_add_pin_shortcut)
        HUI.showToast(R.string.operation_failed, detail, isLengthLong = true)
    }

    /**
     * Send the legacy INSTALL_SHORTCUT broadcast, targeted at whichever launcher is set as the
     * device's HOME. Explicit-package broadcasts skip Android 14+ implicit-broadcast restrictions
     * and reach BBK / Vivo launcher receivers reliably.
     */
    private fun sendLegacyInstallShortcutBroadcast(
        label: CharSequence,
        bitmap: Bitmap,
        launchIntent: Intent,
    ): Boolean {
        val homePackage = runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            app.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        }.getOrNull() ?: return false
        val actions = arrayOf(
            "com.bbk.launcher.action.INSTALL_SHORTCUT",
            "com.vivo.launcher.action.INSTALL_SHORTCUT",
            "com.android.launcher.action.INSTALL_SHORTCUT",
        )
        var delivered = false
        actions.forEach { action ->
            runCatching {
                val broadcast = Intent(action).apply {
                    setPackage(homePackage)
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, label.toString())
                    putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
                    putExtra("duplicate", false)
                }
                app.sendBroadcast(broadcast)
                delivered = true
            }.onFailure { HLog.e(it) }
        }
        return delivered
    }

    private fun bitmapFromIconCompat(icon: IconCompat): Bitmap? = runCatching {
        val drawable = icon.loadDrawable(app) ?: return@runCatching null
        getBitmapFromDrawable(drawable)
    }.getOrNull()

    fun addDynamicShortcut(packageName: String) {
        if (HailData.biometricLogin) return
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(app, buildAppShortcut(packageName))
        }.onFailure { HLog.e(it) }
        addDynamicShortcutAction(HailData.dynamicShortcutAction)
    }

    /**
     * Refresh the home-screen shortcut icon to reflect the current frozen/unfrozen state.
     *
     * Two APIs are used in combination because OEM launchers differ in what they honour:
     * - [ShortcutManagerCompat.pushDynamicShortcut]: reliably refreshes the icon for both the
     *   dynamic launcher-menu entry AND any pinned home-screen copy that shares the same shortcut
     *   ID.  This is the primary update path for Vivo OriginOS / Xiaomi MIUI launchers, which
     *   ignore [updateShortcuts] for pinned shortcuts but DO refresh pinned copies on push.
     * - [ShortcutManagerCompat.updateShortcuts]: the documented API for updating pinned shortcuts;
     *   honoured by AOSP Launcher3, Pixel, Samsung, etc.  Also sent for backwards compatibility
     *   with old pinned shortcuts that were created with the separate "pin_hash" ID.
     */
    fun updateShortcutIcon(packageName: String, frozen: Boolean) {
        val shortcut = buildAppShortcut(packageName, forPin = false)  // ID = shortcutIdFor (hash)
        // Primary path: push as dynamic — refreshes both the dynamic entry and the pinned
        // home-screen copy when they share the same ID (see addPinShortcutForApp).
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
            .onFailure { HLog.e(it) }
        // Secondary path: updateShortcuts for AOSP/Pixel/Samsung launchers, and backwards
        // compat for old shortcuts created with the legacy "pin_hash" ID scheme.
        val legacyPinned = buildAppShortcut(packageName, forPin = true)  // ID = "pin_hash"
        runCatching { ShortcutManagerCompat.updateShortcuts(app, listOf(shortcut, legacyPinned)) }
            .onFailure { HLog.e(it) }
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
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }).build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(app, shortcut) }
            .onFailure { HLog.e(it) }
    }

    fun removeAllDynamicShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(app)
    }

    /**
     * Ensure any launcher-facing intent has an explicit component, category, and the activity
     * flags that launchers require. Used for ad-hoc pin intents coming from the Settings screen
     * (freeze-all / unfreeze-all / lock actions).
     */
    private fun normalizeLauncherIntent(src: Intent): Intent = Intent(src).apply {
        if (component == null) {
            component = ComponentName(BuildConfig.APPLICATION_ID, API_ACTIVITY_CLASS)
        }
        if (`package` == null) setPackage(BuildConfig.APPLICATION_ID)
        // CATEGORY_DEFAULT is intentionally omitted — unnecessary for explicit-component intents
        // and rejected by Vivo/OriginOS launchers during pin-time intent validation.
        flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
        IconCompat.createWithAdaptiveBitmap(getBitmapFromDrawable(drawable))

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
