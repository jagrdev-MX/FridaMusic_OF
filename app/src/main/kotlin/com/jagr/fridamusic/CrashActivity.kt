package com.jagr.fridamusic

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.utils.CrashHandler
import kotlin.system.exitProcess

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashLog = intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG)
            ?: getString(R.string.crash_no_log)
        val exceptionType = intent.getStringExtra(CrashHandler.EXTRA_EXCEPTION_TYPE)
            ?.takeIf { it.isNotBlank() }
            ?: extractExceptionType(crashLog)

        setContent {
            MaterialTheme {
                Surface {
                    CrashScreen(
                        crashLog = crashLog,
                        onSendReport = { openCrashReportEmail(crashLog, exceptionType) },
                        onClose = {
                            finishAffinity()
                            exitProcess(0)
                        },
                    )
                }
            }
        }
    }

    private fun openCrashReportEmail(crashLog: String, exceptionType: String) {
        val subject = getString(R.string.crash_report_email_subject, exceptionType)
        val body = sanitizeCrashLogForSharing(crashLog)
        val mailtoUri = Uri.parse(
            "mailto:$SUPPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}",
        )
        val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(emailIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.crash_email_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.crash_email_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val SUPPORT_EMAIL = "fridalabs.soporte@gmail.com"
    }
}

@Composable
private fun CrashScreen(
    crashLog: String,
    onSendReport: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_unexpected_error),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = crashLog,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.close))
                }
                Button(
                    onClick = onSendReport,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.crash_send_report))
                }
            }
        }
    }
}

private fun extractExceptionType(crashLog: String): String {
    val exceptionLine = crashLog
        .substringAfter("Stacktrace:", missingDelimiterValue = "")
        .lineSequence()
        .map(String::trim)
        .firstOrNull { line -> line.isNotEmpty() && line.any { it != '=' } }

    return exceptionLine
        ?.removePrefix("Caused by: ")
        ?.substringBefore(':')
        ?.substringAfterLast('.')
        ?.takeIf { it.isNotBlank() }
        ?: "UnknownError"
}

private fun sanitizeCrashLogForSharing(crashLog: String): String =
    SENSITIVE_CRASH_LINE.replace(crashLog) { match ->
        "${match.groupValues[1]}[REDACTED]"
    }

private val SENSITIVE_CRASH_LINE = Regex(
    pattern = """(?im)^(\s*(?:Authorization|Proxy-Authorization|Cookie|Set-Cookie|visitorData|dataSyncId|password|api[_-]?key|access[_-]?token|refresh[_-]?token|token)\s*[:=]\s*).*$""",
)
