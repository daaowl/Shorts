package com.goat.kharcha

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.goat.kharcha.data.Repo
import com.goat.kharcha.ui.KharchaRoot

/** Single activity. FragmentActivity is needed for the system unlock prompt. */
class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_QUEUE = "queue"
        private const val RELOCK_AFTER_MS = 3 * 60 * 1000L

        /** Lives as long as the process: a fresh start of the app asks again. */
        @Volatile private var unlockedThisProcess = false
    }

    val locked = mutableStateOf(false)
    val openRequest = mutableStateOf<String?>(null)
    private var stoppedAt = 0L
    private var authInProgress = false
    private var barsDark: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setBars(true)
        if (Repo.settings.value.lock && !unlockedThisProcess && canUseDeviceAuth()) locked.value = true
        openRequest.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent { KharchaRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { openRequest.value = it }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (Repo.settings.value.lock && stoppedAt > 0 && canUseDeviceAuth() &&
            SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS
        ) locked.value = true
    }

    /** Status and navigation bar icons follow the app's theme, not just the system's. */
    fun setBars(dark: Boolean) {
        if (barsDark == dark) return
        barsDark = dark
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** Skips the next re-lock check, for trips to system screens that the app itself opened. */
    fun markExternalTrip() {
        stoppedAt = 0L
    }

    fun canUseDeviceAuth(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        if (authInProgress) return
        if (!canUseDeviceAuth()) {
            locked.value = false
            onResult(false)
            return
        }
        authInProgress = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                unlockedThisProcess = true
                locked.value = false
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
                onResult(false)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Kharcha")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    /** Called when the lock is switched on in Settings: that switch already proved it's the owner. */
    fun lockTurnedOn() {
        unlockedThisProcess = true
    }
}
