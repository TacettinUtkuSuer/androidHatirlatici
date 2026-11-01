package com.utkusuer.hatirlatici.data.security

import org.json.JSONObject

/**
 * Hassas kart/hesap bilgileri. Veritabanına yalnızca [PinCipher] ile şifrelenmiş JSON olarak yazılır.
 */
data class CardSecrets(
    val cardNumber: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val pin: String = "",
    val bankingPassword: String = "",
    val accountNumber: String = "",
) {
    val isEmpty: Boolean
        get() = cardNumber.isBlank() && expiry.isBlank() && cvv.isBlank() && pin.isBlank() &&
            bankingPassword.isBlank() && accountNumber.isBlank()

    /** Kart numarasından son 4 hane (yoksa null). */
    val last4: String?
        get() = cardNumber.filter(Char::isDigit).takeIf { it.length >= 4 }?.takeLast(4)

    fun toJson(): String = JSONObject()
        .put("cardNumber", cardNumber)
        .put("expiry", expiry)
        .put("cvv", cvv)
        .put("pin", pin)
        .put("bankingPassword", bankingPassword)
        .put("accountNumber", accountNumber)
        .toString()

    companion object {
        fun fromJson(json: String): CardSecrets = JSONObject(json).run {
            CardSecrets(
                cardNumber = optString("cardNumber"),
                expiry = optString("expiry"),
                cvv = optString("cvv"),
                pin = optString("pin"),
                bankingPassword = optString("bankingPassword"),
                accountNumber = optString("accountNumber"),
            )
        }
    }
}
