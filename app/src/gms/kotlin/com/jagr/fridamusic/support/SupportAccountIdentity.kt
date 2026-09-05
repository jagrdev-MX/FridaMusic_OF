package com.jagr.fridamusic.support

import java.security.MessageDigest
import java.util.Locale

internal fun obfuscateSupportAccountId(accountEmail: String?): String? {
    val normalizedEmail = accountEmail
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return MessageDigest.getInstance("SHA-256")
        .digest("fridamusic-billing:v1:$normalizedEmail".toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
