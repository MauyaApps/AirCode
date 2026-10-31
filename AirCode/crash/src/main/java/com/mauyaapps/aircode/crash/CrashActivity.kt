package com.mauyaapps.aircode.crash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauyaapps.aircode.theme.AirCodeTheme
import com.mauyaapps.aircode.theme.ThemeMode

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT).orEmpty()
        val email = getString(R.string.crash_report_email)

        setContent {
            AirCodeTheme(mode = ThemeMode.SYSTEM) {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.crash_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(text = stringResource(R.string.crash_description))

                        Text(
                            text = report,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        )

                        Button(
                            onClick = { sendReport(email, report) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.crash_send))
                        }
                        OutlinedButton(
                            onClick = ::restartApp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.crash_restart))
                        }
                    }
                }
            }
        }
    }

    private fun sendReport(email: String, report: String) {
        val mail = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_mail_subject))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching {
            startActivity(Intent.createChooser(mail, getString(R.string.crash_mail_chooser)))
        }
    }

    private fun restartApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(launch)
        }
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_REPORT = "report"
    }
}
