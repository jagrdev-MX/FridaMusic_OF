package com.jagr.fridamusic.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

const val SUPPORT_PAYPAL_URL =
    "https://paypal.me/JAGRDEVELOPER?locale.x=es_XC&country.x=MX"
const val FRIDAMUSIC_PLAY_PACKAGE = "com.jagr.fridamusic"

enum class SupportLinkResult {
    PAYPAL_OPENED,
    PLAY_STORE_OPENED,
    BROWSER_OPENED,
    UNAVAILABLE,
}

fun openSupportPayPal(context: Context): SupportLinkResult {
    val opened = context.tryOpenUri(Uri.parse(SUPPORT_PAYPAL_URL))
    return if (opened) SupportLinkResult.PAYPAL_OPENED else SupportLinkResult.UNAVAILABLE
}

fun openFridaMusicPlayStore(
    context: Context,
    forceBrowser: Boolean = false,
): SupportLinkResult {
    if (!forceBrowser) {
        val marketUri = Uri.parse("market://details?id=$FRIDAMUSIC_PLAY_PACKAGE")
        if (context.tryOpenUri(marketUri)) return SupportLinkResult.PLAY_STORE_OPENED
    }

    val webUri = Uri.parse(
        "https://play.google.com/store/apps/details?id=$FRIDAMUSIC_PLAY_PACKAGE",
    )
    return if (context.tryOpenUri(webUri)) {
        SupportLinkResult.BROWSER_OPENED
    } else {
        SupportLinkResult.UNAVAILABLE
    }
}

private fun Context.tryOpenUri(uri: Uri): Boolean = runCatching {
    startActivity(
        Intent(Intent.ACTION_VIEW, uri).apply {
            if (this@tryOpenUri !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}.isSuccess
