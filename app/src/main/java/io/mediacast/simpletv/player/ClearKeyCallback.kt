package io.mediacast.simpletv.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import org.json.JSONArray
import org.json.JSONObject
import io.mediacast.simpletv.data.Text
import java.util.UUID

/**
 * Локальная «лицензия» ClearKey. В SimpleTV ключ передаётся VLC как
 * avdemux-options={decryption_key=HEX}; KID при этом не нужен. Здесь CDM сам сообщает
 * запрошенные KID (из default_KID манифеста), и мы отвечаем на каждый тем же ключом.
 */
@OptIn(UnstableApi::class)
class ClearKeyCallback(keyHex: String) : MediaDrmCallback {
    private val key = Text.bytesToB64Url(Text.hexToBytes(keyHex))

    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): MediaDrmCallback.Response =
        throw UnsupportedOperationException("ClearKey не требует provisioning")

    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response {
        val kids = runCatching { JSONObject(String(request.data, Charsets.UTF_8)).getJSONArray("kids") }.getOrNull()
        val keys = JSONArray()
        if (kids != null && kids.length() > 0) {
            for (i in 0 until kids.length()) {
                keys.put(JSONObject().put("kty", "oct").put("kid", kids.getString(i)).put("k", key))
            }
        }
        val data = JSONObject().put("keys", keys).put("type", "temporary").toString().toByteArray(Charsets.UTF_8)
        return MediaDrmCallback.Response(data)
    }
}
