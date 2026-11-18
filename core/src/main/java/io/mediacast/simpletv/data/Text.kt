package io.mediacast.simpletv.data

object Text {
    private val superscript =
        mapOf(
            'ᴴ' to "h", 'ᴰ' to "d", 'ᴼ' to "o", 'ᴿ' to "r", 'ᴵ' to "i", 'ᴳ' to "g",
            'ᵁ' to "u", 'ᴷ' to "k", 'ᴬ' to "a", 'ᴮ' to "b", 'ᴱ' to "e", 'ᴹ' to "m",
            'ᴺ' to "n", 'ᴾ' to "p", 'ᵀ' to "t", 'ᵂ' to "w", '⁺' to "+", '⁴' to "4",
        )

    /**
     * Ключ канала для объединения источников и поиска в EPG.
     * ".black ᴴᴰ" → "black hd", "Матч! ТВ" → "матч тв". HD и SD остаются разными каналами,
     * как в TVSources (там ᴴᴰ транслитерируется в отдельный суффикс NameKey).
     */
    fun channelKey(name: String): String {
        val sb = StringBuilder(name.length + 4)
        for (ch in name.lowercase()) {
            val sup = superscript[ch]
            when {
                sup != null -> sb.append(sup)
                ch == 'ё' -> sb.append('е')
                ch.isLetterOrDigit() || ch == '+' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        // "ᴴᴰ" даёт "hd" вплотную к имени — отделяем, но не ломаем FHD/UHD
        return sb.toString()
            .replace(Regex("(?<=[^ fu])hd$"), " hd")
            .replace(Regex("(?<=[а-яa-z])(?=\\d)"), " ")
            .replace(Regex("(?<=\\d)(?=[а-яa-z])"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Более грубый ключ для EPG: без HD/orig/(ott)/+0. */
    fun looseKey(name: String): String =
        channelKey(name)
            .replace(Regex("\\b(hd|fhd|uhd|4k|orig|ott|sd|50|hevc|h265)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun decode64(s: String): String {
        val clean = s.trim().replace('-', '+').replace('_', '/')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return String(decodeBase64Bytes(padded), Charsets.UTF_8)
    }

    fun hexToBytes(hex: String): ByteArray {
        val h = hex.trim().lowercase()
        require(h.isNotEmpty() && h.length % 2 == 0 && h.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Invalid hex string"
        }
        return ByteArray(h.length / 2) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun bytesToB64Url(b: ByteArray): String =
        encodeBase64(b).replace('+', '-').replace('/', '_').trimEnd('=')

    /** Декодирование m3u: UTF-8, при явных ошибках — windows-1251 (TypeCoding в TVSources). */
    fun decodePlaylist(bytes: ByteArray): String {
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        val utf = String(bytes, start, bytes.size - start, Charsets.UTF_8)
        val bad = utf.count { it == '\uFFFD' }
        return if (bad > 10 && bad > utf.length / 200) String(bytes, charset("windows-1251")) else utf
    }

    private const val B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(data: ByteArray): String {
        val out = StringBuilder((data.size * 4 + 2) / 3)
        var i = 0
        while (i < data.size) {
            val b0 = data[i++].toInt() and 0xFF
            val b1 = if (i < data.size) data[i++].toInt() and 0xFF else -1
            val b2 = if (i < data.size) data[i++].toInt() and 0xFF else -1

            out.append(B64_CHARS[b0 shr 2])
            if (b1 != -1) {
                out.append(B64_CHARS[((b0 and 0x03) shl 4) or (b1 shr 4)])
                if (b2 != -1) {
                    out.append(B64_CHARS[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                    out.append(B64_CHARS[b2 and 0x3F])
                } else {
                    out.append(B64_CHARS[(b1 and 0x0F) shl 2])
                    out.append('=')
                }
            } else {
                out.append(B64_CHARS[(b0 and 0x03) shl 4])
                out.append("==")
            }
        }
        return out.toString()
    }

    private fun decodeBase64Bytes(s: String): ByteArray {
        val clean = s.filter { it in B64_CHARS || it == '=' }
        val len = clean.length
        if (len == 0) return ByteArray(0)
        var pad = 0
        if (clean.endsWith("==")) {
            pad = 2
        } else if (clean.endsWith("=")) {
            pad = 1
        }
        val outLen = (len * 3) / 4 - pad
        val out = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < len) {
            val c0 = B64_CHARS.indexOf(clean[i++])
            val c1 = B64_CHARS.indexOf(clean[i++])
            val c2 =
                if (i < len && clean[i] != '=') {
                    B64_CHARS.indexOf(clean[i++])
                } else {
                    i++
                    -1
                }
            val c3 =
                if (i < len && clean[i] != '=') {
                    B64_CHARS.indexOf(clean[i++])
                } else {
                    i++
                    -1
                }

            if (c0 != -1 && c1 != -1 && outIdx < outLen) {
                out[outIdx++] = ((c0 shl 2) or (c1 shr 4)).toByte()
                if (c2 != -1 && outIdx < outLen) {
                    out[outIdx++] = (((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte()
                    if (c3 != -1 && outIdx < outLen) {
                        out[outIdx++] = (((c2 and 0x03) shl 6) or c3).toByte()
                    }
                }
            }
        }
        return out
    }
}
