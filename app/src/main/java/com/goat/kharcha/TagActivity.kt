package com.goat.kharcha

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf
import com.goat.kharcha.data.Repo
import com.goat.kharcha.ui.QuickTag

/** Opens over whatever app is on screen when a payment notification is tapped. */
class TagActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TX = "tx"
    }

    private val txId = mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        txId.longValue = intent?.getLongExtra(EXTRA_TX, -1L) ?: -1L
        setContent { QuickTag(txId.longValue, onDone = { finishQuietly() }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        txId.longValue = intent.getLongExtra(EXTRA_TX, -1L)
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
