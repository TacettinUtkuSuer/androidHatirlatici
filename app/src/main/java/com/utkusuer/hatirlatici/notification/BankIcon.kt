package com.utkusuer.hatirlatici.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.domain.Banks

/** Bildirimlerde gösterilen banka rozeti (uygulamadaki rozetin aynısı, bitmap olarak). */
object BankIcon {
    private val cache = HashMap<String, Bitmap>()

    fun bitmap(context: Context, payment: PaymentEntity): Bitmap? {
        val bank = Banks.find(payment.bankCode) ?: return null
        return cache.getOrPut(bank.code) {
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bank.color.toInt() }
            val r = size * 0.28f
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, bg)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (bank.darkText) 0xFF15171A.toInt() else 0xFFFFFFFF.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = size * when {
                    bank.short.length >= 4 -> 0.26f
                    bank.short.length == 3 -> 0.30f
                    else -> 0.38f
                }
            }
            val y = size / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(bank.short, size / 2f, y, text)
            bmp
        }
    }
}
