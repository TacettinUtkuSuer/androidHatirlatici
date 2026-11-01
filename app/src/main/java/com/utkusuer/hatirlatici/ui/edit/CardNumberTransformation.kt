package com.utkusuer.hatirlatici.ui.edit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Kart numarasını 4'lü gruplar halinde gösterir: "1234 5678 9012 3456". */
object CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = buildString {
            raw.forEachIndexed { i, c ->
                if (i > 0 && i % 4 == 0) append(' ')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + (if (offset > 0) (offset - 1) / 4 else 0)
            override fun transformedToOriginal(offset: Int): Int = (offset - offset / 5).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
