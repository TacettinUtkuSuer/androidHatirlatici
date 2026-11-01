package com.utkusuer.hatirlatici.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class DriveFile(val id: String, val name: String, val modifiedTime: String)

/**
 * Google Drive REST v3 için küçük istemci (ek kütüphane yok). Yalnızca `drive.file` kapsamı kullanılır:
 * uygulama sadece kendi oluşturduğu dosyaları görebilir. Yedekler "Hatırlatıcı Yedekleri" klasöründe tutulur.
 */
class DriveClient(private val accessToken: String) {

    suspend fun upload(name: String, json: String): DriveFile = withContext(Dispatchers.IO) {
        val folderId = findOrCreateFolder()
        val boundary = "hatirlatici-" + UUID.randomUUID()
        val metadata = JSONObject().put("name", name).put("mimeType", MIME_JSON).put("parents", org.json.JSONArray().put(folderId))
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--$boundary\r\nContent-Type: $MIME_JSON\r\n\r\n")
            append(json).append("\r\n")
            append("--$boundary--")
        }
        val res = request(
            "POST",
            "$UPLOAD/files?uploadType=multipart&fields=id,name,modifiedTime",
            body.toByteArray(Charsets.UTF_8),
            "multipart/related; boundary=$boundary",
        )
        JSONObject(res).toDriveFile()
    }

    suspend fun listBackups(): List<DriveFile> = withContext(Dispatchers.IO) {
        val folderId = findFolder() ?: return@withContext emptyList()
        val q = "'$folderId' in parents and trashed=false and mimeType='$MIME_JSON'"
        val res = request("GET", "$API/files?q=${enc(q)}&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime)&pageSize=50")
        val arr = JSONObject(res).getJSONArray("files")
        (0 until arr.length()).map { arr.getJSONObject(it).toDriveFile() }
    }

    suspend fun download(fileId: String): String = withContext(Dispatchers.IO) {
        request("GET", "$API/files/$fileId?alt=media")
    }

    suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        request("DELETE", "$API/files/$fileId")
        Unit
    }

    /** En yeni [keep] yedek dışındakileri siler. */
    suspend fun prune(keep: Int = 10) {
        listBackups().drop(keep).forEach { runCatching { delete(it.id) } }
    }

    private fun findFolder(): String? {
        val q = "mimeType='$MIME_FOLDER' and name='$FOLDER_NAME' and trashed=false"
        val res = request("GET", "$API/files?q=${enc(q)}&fields=files(id)&pageSize=1")
        val arr = JSONObject(res).getJSONArray("files")
        return if (arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    private fun findOrCreateFolder(): String = findFolder() ?: run {
        val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", MIME_FOLDER)
        JSONObject(request("POST", "$API/files?fields=id", meta.toString().toByteArray(), "application/json")).getString("id")
    }

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw DriveException(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.toDriveFile() = DriveFile(getString("id"), optString("name"), optString("modifiedTime"))
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val MIME_JSON = "application/json"
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private const val FOLDER_NAME = "Hatırlatıcı Yedekleri"
    }
}

class DriveException(val code: Int, body: String) : IOException("Drive hatası $code: ${body.take(200)}")
