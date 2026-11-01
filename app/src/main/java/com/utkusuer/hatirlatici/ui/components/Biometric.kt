package com.utkusuer.hatirlatici.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Hassas bilgileri göstermeden önce parmak izi / yüz / ekran kilidi doğrulaması.
 * Cihazda hiç ekran kilidi yoksa doğrulama yapılamayacağı için [onSuccess] doğrudan çağrılır.
 */
fun authenticate(context: Context, title: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
    val activity = context.findFragmentActivity() ?: run { onError("Doğrulama başlatılamadı"); return }
    val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Kart ve hesap bilgilerini göstermek için kimliğinizi doğrulayın")
            .setAllowedAuthenticators(authenticators)
            .build(),
    )
}

private fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
