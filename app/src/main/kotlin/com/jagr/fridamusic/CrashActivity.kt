package com.jagr.fridamusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

        setContent {
            MaterialTheme {
                Surface {
                    CrashScreen(crashLog) {
                        finishAffinity()
                        exitProcess(0)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashScreen(crashLog: String, onClose: () -> Unit) {
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
            Button(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
