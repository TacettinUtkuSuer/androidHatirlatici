package com.utkusuer.hatirlatici.data.backup

import com.utkusuer.hatirlatici.data.local.entity.ActivityLogEntity
import com.utkusuer.hatirlatici.data.local.entity.CycleStatus
import com.utkusuer.hatirlatici.data.local.entity.EntryKind
import com.utkusuer.hatirlatici.data.local.entity.LogType
import com.utkusuer.hatirlatici.data.local.entity.PaymentCategory
import com.utkusuer.hatirlatici.data.local.entity.PaymentCycleEntity
import com.utkusuer.hatirlatici.data.local.entity.PaymentEntity
import com.utkusuer.hatirlatici.data.local.entity.Recurrence
import com.utkusuer.hatirlatici.data.local.entity.ReminderTimeEntity
import com.utkusuer.hatirlatici.data.local.entity.ScheduleType
import com.utkusuer.hatirlatici.data.local.entity.WeekendRule
import com.utkusuer.hatirlatici.data.security.CardSecrets
import com.utkusuer.hatirlatici.data.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Yedeğin tüm içeriği (hassas bilgiler düz halde; yalnızca bellekte). */
data class BackupData(
    val createdAt: Long,
    val settings: AppSettings,
    val payments: List<PaymentEntity>,
    val reminderTimes: List<ReminderTimeEntity>,
    val cycles: List<PaymentCycleEntity>,
    val log: List<ActivityLogEntity>,
    /** paymentId → bilgiler. Boşsa yedekte hassas bilgi yoktur. */
    val secrets: Map<Long, CardSecrets> = emptyMap(),
)

/** Dosyayı açmadan önce gösterilecek özet. */
data class BackupInfo(val createdAt: Long, val paymentCount: Int, val hasSecrets: Boolean)

class WrongPasswordException : Exception("Yedek parolası hatalı")
class InvalidBackupException(msg: String) : Exception(msg)

/**
 * JSON yedek biçimi. Hassas bilgiler (kart no, CVV, PIN, şifreler) cihaz anahtarına bağlı olmadan,
 * kullanıcının belirlediği parolayla PBKDF2-SHA256 + AES-256-GCM ile şifrelenir.
 */
object BackupCodec {
    const val FORMAT = "hatirlatici-backup"
    const val VERSION = 1
    private const val ITERATIONS = 210_000

    fun encode(data: BackupData, password: String?): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", data.createdAt)
            .put("settings", settingsToJson(data.settings))
            .put("payments", JSONArray(data.payments.map(::paymentToJson)))
            .put("reminderTimes", JSONArray(data.reminderTimes.map { JSONObject().put("paymentId", it.paymentId).put("minuteOfDay", it.minuteOfDay) }))
            .put("cycles", JSONArray(data.cycles.map(::cycleToJson)))
            .put("log", JSONArray(data.log.map(::logToJson)))
        if (data.secrets.isNotEmpty()) {
            requireNotNull(password) { "Hassas bilgiler için parola gerekli" }
            val plain = JSONObject().apply { data.secrets.forEach { (id, s) -> put(id.toString(), JSONObject(s.toJson())) } }
            root.put("secrets", encryptWithPassword(plain.toString(), password))
        }
        return root.toString(2)
    }

    fun info(json: String): BackupInfo {
        val root = parseRoot(json)
        return BackupInfo(root.getLong("createdAt"), root.getJSONArray("payments").length(), root.has("secrets"))
    }

    fun decode(json: String, password: String?): BackupData {
        val root = parseRoot(json)
        val secrets = if (root.has("secrets") && password != null) {
            val plain = JSONObject(decryptWithPassword(root.getJSONObject("secrets"), password))
            plain.keys().asSequence().associate { it.toLong() to CardSecrets.fromJson(plain.getJSONObject(it).toString()) }
        } else emptyMap()
        return BackupData(
            createdAt = root.getLong("createdAt"),
            settings = settingsFromJson(root.getJSONObject("settings")),
            payments = root.getJSONArray("payments").objects().map(::paymentFromJson),
            reminderTimes = root.getJSONArray("reminderTimes").objects().map { ReminderTimeEntity(it.getLong("paymentId"), it.getInt("minuteOfDay")) },
            cycles = root.getJSONArray("cycles").objects().map(::cycleFromJson),
            log = root.optJSONArray("log")?.objects()?.map(::logFromJson).orEmpty(),
            secrets = secrets,
        )
    }

    private fun parseRoot(json: String): JSONObject {
        val root = runCatching { JSONObject(json) }.getOrElse { throw InvalidBackupException("Dosya okunamadı") }
        if (root.optString("format") != FORMAT) throw InvalidBackupException("Bu bir Hatırlatıcı yedeği değil")
        if (root.optInt("version") > VERSION) throw InvalidBackupException("Yedek daha yeni bir sürümle alınmış")
        return root
    }

    // --- Parola ile şifreleme ---

    fun encryptWithPassword(plain: String, password: String): JSONObject {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val enc = Base64.getEncoder()
        return JSONObject()
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", enc.encodeToString(salt))
            .put("iv", enc.encodeToString(iv))
            .put("data", enc.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8))))
    }

    fun decryptWithPassword(box: JSONObject, password: String): String {
        val dec = Base64.getDecoder()
        val key = deriveKey(password, dec.decode(box.getString("salt")), box.getInt("iterations"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, dec.decode(box.getString("iv"))))
        return try {
            String(cipher.doFinal(dec.decode(box.getString("data"))), Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    // --- Dönüştürücüler ---

    private fun settingsToJson(s: AppSettings) = JSONObject()
        .put("defaultReminderMinutes", JSONArray(s.defaultReminderMinutes))
        .put("persistentNotificationsEnabled", s.persistentNotificationsEnabled)
        .put("remindersEnabled", s.remindersEnabled)
        .put("upcomingWindowDays", s.upcomingWindowDays)

    private fun settingsFromJson(o: JSONObject): AppSettings {
        val d = AppSettings()
        val times = o.optJSONArray("defaultReminderMinutes")
        return d.copy(
            defaultReminderMinutes = times?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: d.defaultReminderMinutes,
            persistentNotificationsEnabled = o.optBoolean("persistentNotificationsEnabled", d.persistentNotificationsEnabled),
            remindersEnabled = o.optBoolean("remindersEnabled", d.remindersEnabled),
            upcomingWindowDays = o.optInt("upcomingWindowDays", d.upcomingWindowDays),
        )
    }

    private fun paymentToJson(p: PaymentEntity) = JSONObject()
        .put("id", p.id)
        .put("title", p.title)
        .putOpt("institution", p.institution)
        .put("category", p.category.name)
        .put("kind", p.kind.name)
        .putOpt("bankCode", p.bankCode)
        .put("scheduleType", p.scheduleType.name)
        .put("recurrence", p.recurrence.name)
        .put("dayStart", p.dayStart)
        .putOpt("dayEnd", p.dayEnd)
        .put("reminderDelayDays", p.reminderDelayDays)
        .put("leadDays", p.leadDays)
        .put("weekendRule", p.weekendRule.name)
        .put("startDateEpochDay", p.startDateEpochDay)
        .putOpt("endDateEpochDay", p.endDateEpochDay)
        .putOpt("totalInstallments", p.totalInstallments)
        .putOpt("amountMinor", p.amountMinor)
        .put("currency", p.currency)
        .put("isActive", p.isActive)
        .put("persistentNotification", p.persistentNotification)
        .put("useDefaultReminderTimes", p.useDefaultReminderTimes)
        .putOpt("cardLast4", p.cardLast4)
        .putOpt("note", p.note)
        .put("createdAt", p.createdAt)
        .put("updatedAt", p.updatedAt)

    private fun paymentFromJson(o: JSONObject) = PaymentEntity(
        id = o.getLong("id"),
        title = o.getString("title"),
        institution = o.optStringOrNull("institution"),
        category = enumOr(o.optString("category"), PaymentCategory.OTHER),
        kind = enumOr(o.optString("kind"), EntryKind.EXPENSE),
        bankCode = o.optStringOrNull("bankCode"),
        scheduleType = enumOr(o.optString("scheduleType"), ScheduleType.SINGLE_DAY),
        recurrence = enumOr(o.optString("recurrence"), Recurrence.MONTHLY),
        dayStart = o.getInt("dayStart"),
        dayEnd = o.optIntOrNull("dayEnd"),
        reminderDelayDays = o.optInt("reminderDelayDays"),
        leadDays = o.optInt("leadDays"),
        weekendRule = enumOr(o.optString("weekendRule"), WeekendRule.NONE),
        startDateEpochDay = o.getLong("startDateEpochDay"),
        endDateEpochDay = o.optLongOrNull("endDateEpochDay"),
        totalInstallments = o.optIntOrNull("totalInstallments"),
        amountMinor = o.optLongOrNull("amountMinor"),
        currency = o.optString("currency", "TRY"),
        isActive = o.optBoolean("isActive", true),
        persistentNotification = o.optBoolean("persistentNotification", true),
        useDefaultReminderTimes = o.optBoolean("useDefaultReminderTimes", true),
        cardLast4 = o.optStringOrNull("cardLast4"),
        note = o.optStringOrNull("note"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun cycleToJson(c: PaymentCycleEntity) = JSONObject()
        .put("id", c.id)
        .put("paymentId", c.paymentId)
        .put("periodKey", c.periodKey)
        .put("windowStartEpochDay", c.windowStartEpochDay)
        .put("windowEndEpochDay", c.windowEndEpochDay)
        .put("notifyFromEpochDay", c.notifyFromEpochDay)
        .put("status", c.status.name)
        .putOpt("paidAt", c.paidAt)
        .putOpt("paidAmountMinor", c.paidAmountMinor)

    private fun cycleFromJson(o: JSONObject) = PaymentCycleEntity(
        id = o.getLong("id"),
        paymentId = o.getLong("paymentId"),
        periodKey = o.getString("periodKey"),
        windowStartEpochDay = o.getLong("windowStartEpochDay"),
        windowEndEpochDay = o.getLong("windowEndEpochDay"),
        notifyFromEpochDay = o.getLong("notifyFromEpochDay"),
        status = enumOr(o.optString("status"), CycleStatus.PENDING),
        paidAt = o.optLongOrNull("paidAt"),
        paidAmountMinor = o.optLongOrNull("paidAmountMinor"),
    )

    private fun logToJson(l: ActivityLogEntity) = JSONObject()
        .put("id", l.id)
        .put("timestamp", l.timestamp)
        .put("type", l.type.name)
        .putOpt("paymentId", l.paymentId)
        .put("title", l.title)
        .putOpt("detail", l.detail)
        .putOpt("amountMinor", l.amountMinor)

    private fun logFromJson(o: JSONObject) = ActivityLogEntity(
        id = o.getLong("id"),
        timestamp = o.getLong("timestamp"),
        type = enumOr(o.optString("type"), LogType.UPDATED),
        paymentId = o.optLongOrNull("paymentId"),
        title = o.getString("title"),
        detail = o.optStringOrNull("detail"),
        amountMinor = o.optLongOrNull("amountMinor"),
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k)) null else optLong(k)
    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(default)
}
