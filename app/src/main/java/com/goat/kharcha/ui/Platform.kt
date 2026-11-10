package com.goat.kharcha.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.goat.kharcha.service.Detector
import com.goat.kharcha.service.PaymentListener
import kotlin.random.Random

/** Shortcuts into the system screens the app needs. Each falls back to a wider screen if the exact one is missing. */
object Permissions {
    fun openListenerSettings(ctx: Context) {
        val component = ComponentName(ctx, PaymentListener::class.java).flattenToString()
        val tries = buildList {
            if (Build.VERSION.SDK_INT >= 30) {
                add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component))
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        }
        launchFirst(ctx, tries)
    }

    fun openAppInfo(ctx: Context) {
        launchFirst(ctx, listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))))
    }

    fun openNotificationSettings(ctx: Context) {
        launchFirst(
            ctx,
            listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
            ),
        )
    }

    private fun launchFirst(ctx: Context, intents: List<Intent>) {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Throwable) {
            }
        }
    }
}

/** Sends a realistic payment through the exact same path a real notification takes. */
object TestPayment {
    private val places = listOf(
        "SAVOUR FOODS" to (550..1400),
        "CHAI SHAI" to (180..450),
        "IMTIAZ SUPER MARKET" to (1800..6500),
        "PSO GT ROAD" to (2000..5000),
        "SERVAID PHARMACY" to (300..1900),
        "GLORIA JEANS" to (700..1500),
        "DARAZ" to (900..4200),
    )

    fun send(ctx: Context, text: String? = null) {
        val body = text ?: run {
            val (place, range) = places.random()
            val amount = (Random.nextInt(range.first, range.last) / 10) * 10
            "PKR ${"%,d".format(amount)}.00 paid at $place with card ending 4417. Ref ${Random.nextInt(100000, 999999)}"
        }
        Detector.handle(ctx, ctx.packageName + ".test", "Test bank", listOf<Pair<String?, String>>("Payment alert" to body), System.currentTimeMillis())
    }
}
