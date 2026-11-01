package com.utkusuer.hatirlatici.ui.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.ui.components.ConfirmPaidDialog
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
                ConfirmPaidDialog(
                    title = title,
                    onConfirm = {
                        lifecycleScope.launch {
                            engine.markPaid(cycleId)
                            finish()
                        }
                    },
                    onDismiss = { finish() },
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
