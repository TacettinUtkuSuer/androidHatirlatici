package com.utkusuer.hatirlatici.ui.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.R
import com.utkusuer.hatirlatici.ui.theme.HatirlaticiTheme
import kotlinx.coroutines.launch

/**
 * "Ödemeyi Yaptım" çift onayı. Bildirim aksiyonundan doğrudan açılır.
 * Vazgeç / geri / dışarı dokunma → hiçbir şey değişmez, bildirimler devam eder.
 */
class ConfirmPaidActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cycleId = intent.getLongExtra(EXTRA_CYCLE_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (cycleId < 0) {
            finish(); return
        }
        val engine = (application as HatirlaticiApp).container.reminderEngine

        setContent {
            HatirlaticiTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.confirm_paid_title)) },
                    text = { Text(stringResource(R.string.confirm_paid_body, title)) },
                    confirmButton = {
                        TextButton(onClick = {
                            lifecycleScope.launch {
                                engine.markPaid(cycleId)
                                finish()
                            }
                        }) { Text(stringResource(R.string.confirm_yes)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.confirm_no)) }
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CYCLE_ID = "cycle_id"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, cycleId: Long, title: String): Intent =
            Intent(context, ConfirmPaidActivity::class.java)
                .putExtra(EXTRA_CYCLE_ID, cycleId)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
