package com.utkusuer.hatirlatici.domain

/**
 * Türkiye'de faaliyet gösteren bankalar. Tescilli logolar uygulamaya gömülemediği için her banka,
 * kurumsal rengine yakın bir zeminde kısaltmasıyla (monogram) gösterilir.
 */
data class Bank(val code: String, val name: String, val short: String, val color: Long, val darkText: Boolean = false)

object Banks {
    val all: List<Bank> = listOf(
        // Kamu
        Bank("ziraat", "Ziraat Bankası", "ZB", 0xFFE30613),
        Bank("halkbank", "Halkbank", "HB", 0xFF00579E),
        Bank("vakifbank", "VakıfBank", "VB", 0xFFFDB913, darkText = true),
        // Özel
        Bank("isbank", "Türkiye İş Bankası", "İŞ", 0xFF1A4D9B),
        Bank("garanti", "Garanti BBVA", "GB", 0xFF1D9A6C),
        Bank("yapikredi", "Yapı Kredi", "YK", 0xFF004B93),
        Bank("akbank", "Akbank", "AK", 0xFFE2001A),
        Bank("qnb", "QNB", "QNB", 0xFF5C0F8B),
        Bank("enpara", "Enpara.com", "EN", 0xFF7B2D8E),
        Bank("denizbank", "DenizBank", "DB", 0xFF0069B4),
        Bank("teb", "TEB", "TEB", 0xFF00A651),
        Bank("cepteteb", "CEPTETEB", "CT", 0xFF00875A),
        Bank("ing", "ING", "ING", 0xFFFF6200),
        Bank("hsbc", "HSBC", "HSBC", 0xFFDB0011),
        Bank("sekerbank", "Şekerbank", "ŞB", 0xFF00843D),
        Bank("fibabanka", "Fibabanka", "FB", 0xFFE65F00),
        Bank("odeabank", "Odeabank", "OB", 0xFF6E2C6B),
        Bank("anadolubank", "Anadolubank", "AB", 0xFF0D2C6C),
        Bank("alternatif", "Alternatif Bank", "ALT", 0xFFC8102E),
        Bank("burgan", "Burgan Bank", "BB", 0xFF003A70),
        Bank("icbc", "ICBC Turkey", "ICBC", 0xFFC8102E),
        Bank("aktif", "Aktif Bank", "AKT", 0xFFE4002B),
        Bank("turkishbank", "Turkish Bank", "TB", 0xFF1B3F8B),
        Bank("hayatfinans", "Hayat Finans", "HF", 0xFF00A19A),
        // Katılım
        Bank("kuveytturk", "Kuveyt Türk", "KT", 0xFF00843D),
        Bank("albaraka", "Albaraka Türk", "AL", 0xFFA6192E),
        Bank("turkiyefinans", "Türkiye Finans", "TF", 0xFF00727A),
        Bank("vakifkatilim", "Vakıf Katılım", "VK", 0xFFD4A017, darkText = true),
        Bank("ziraatkatilim", "Ziraat Katılım", "ZK", 0xFFB00012),
        Bank("emlakkatilim", "Emlak Katılım", "EK", 0xFF0060A8),
        Bank("dunyakatilim", "Dünya Katılım", "DK", 0xFF2C5F8A),
        // Dijital / e-para
        Bank("papara", "Papara", "P", 0xFF2E2E2E),
        Bank("ininal", "ininal", "in", 0xFF3D3D3D),
    )

    private val byCode = all.associateBy { it.code }

    fun find(code: String?): Bank? = code?.let(byCode::get)

    fun search(query: String): List<Bank> {
        if (query.isBlank()) return all
        val q = query.trim().lowercase(Formatters.TR)
        return all.filter { it.name.lowercase(Formatters.TR).contains(q) || it.short.lowercase(Formatters.TR).contains(q) }
    }
}
