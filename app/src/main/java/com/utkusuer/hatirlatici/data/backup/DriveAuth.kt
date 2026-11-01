package com.utkusuer.hatirlatici.data.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Google hesabı ile Drive yetkilendirmesi (Google Identity Services — AuthorizationClient). */
object DriveAuth {

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveClient.SCOPE))).build()

    /**
     * Daha önce izin verilmişse kullanıcıya bir şey göstermeden erişim anahtarı alır (arka plan yedeği için).
     * Etkileşim gerekiyorsa null döner.
     */
    suspend fun silentToken(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request()))
            if (result.hasResolution()) null else result.accessToken
        }.getOrNull()
    }

    /** Kullanıcıya gösterilecek anlaşılır hata metni. */
    fun describe(error: Throwable): String = when {
        error is ApiException && error.statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
            "Google Cloud'da bu uygulama için OAuth istemcisi tanımlı değil. docs/GOOGLE_DRIVE.md adımlarını izleyin."
        error is ApiException && error.statusCode == CommonStatusCodes.NETWORK_ERROR -> "İnternet bağlantısı yok."
        error is ApiException && error.statusCode == CommonStatusCodes.CANCELED -> "İşlem iptal edildi."
        error is DriveException && error.code == 401 -> "Google oturumunun süresi dolmuş, tekrar deneyin."
        error is DriveException && error.code == 403 -> "Drive erişimi reddedildi (Drive API etkin mi?)."
        error is java.io.IOException -> "Bağlantı hatası: ${error.message}"
        else -> error.message ?: "Bilinmeyen hata"
    }
}
