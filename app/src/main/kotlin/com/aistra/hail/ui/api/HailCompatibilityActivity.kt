package io.spasum.hailshizuku.ui.api

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.spasum.hailshizuku.BuildConfig

class HailCompatibilityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val remappedAction = intent.action
            ?.replace("com.aistra.hail.action.", "${BuildConfig.APPLICATION_ID}.action.")
        startActivity(Intent(remappedAction ?: intent.action).apply {
            intent.extras?.let { putExtras(it) }
        })
        finish()
    }
}
