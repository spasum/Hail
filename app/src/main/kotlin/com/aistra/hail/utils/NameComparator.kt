package io.spasum.hailshizuku.utils

import android.content.pm.ApplicationInfo
import io.spasum.hailshizuku.HailApp.Companion.app
import io.spasum.hailshizuku.app.AppInfo
import java.text.Collator

object NameComparator : Comparator<Any> {
    private val c = Collator.getInstance()
    override fun compare(a: Any, b: Any): Int = when {
        a is ApplicationInfo && b is ApplicationInfo -> c.compare(
            a.loadLabel(app.packageManager),
            b.loadLabel(app.packageManager)
        )
        a is AppInfo && b is AppInfo -> when {
            a.pinned && !b.pinned -> -1
            b.pinned && !a.pinned -> 1
            else -> c.compare(a.name, b.name)
        }
        else -> 0
    }
}