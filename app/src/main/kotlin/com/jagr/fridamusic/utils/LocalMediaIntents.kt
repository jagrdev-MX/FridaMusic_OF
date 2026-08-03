

package com.jagr.fridamusic.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File
import java.util.Locale

fun String.isLocalMediaId(): Boolean {
    return runCatching {
        when (toUri().scheme?.lowercase(Locale.US)) {
            "content", "file", "android.resource" -> true
            else -> false
        }
    }.getOrDefault(false)
}

fun shareLocalAudio(
    context: Context,
    mediaId: String,
    mimeType: String? = null,
): Boolean {
    val uri = mediaId.toUri()
    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme != "content" && scheme != "android.resource") return false

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType?.takeIf(String::isNotBlank) ?: "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(shareIntent, null))
        true
    }.getOrDefault(false)
}

fun openLocalAudioFolder(
    context: Context,
    relativePath: String?,
    absolutePath: String?,
): Boolean {
    val documentId = localFolderDocumentId(relativePath, absolutePath) ?: return false
    val folderUri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        documentId,
    )
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (runCatching { context.startActivity(viewIntent) }.isSuccess) return true

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (runCatching { context.startActivity(pickerIntent) }.isSuccess) return true
    }
    return false
}

private fun localFolderDocumentId(relativePath: String?, absolutePath: String?): String? {
    val parentPath = absolutePath?.let(::File)?.parent
        ?.replace('\\', '/')
        ?.trimEnd('/')
    if (parentPath != null) {
        val primaryPrefix = "/storage/emulated/0/"
        if (parentPath.startsWith(primaryPrefix, ignoreCase = true)) {
            return "primary:${parentPath.substring(primaryPrefix.length)}"
        }
        val storageMatch = Regex("^/storage/([^/]+)/(.+)$", RegexOption.IGNORE_CASE).matchEntire(parentPath)
        if (storageMatch != null) {
            return "${storageMatch.groupValues[1]}:${storageMatch.groupValues[2]}"
        }
    }

    val normalizedRelativePath = relativePath
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf(String::isNotBlank)
        ?: return null
    return "primary:$normalizedRelativePath"
}
