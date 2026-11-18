package io.mediacast.simpletv.data

/**
 * Разбор M3U/M3U8 с поддержкой тегов, которые встречаются в источниках сборки:
 * #EXTM3U url-tvg/catchup…, #EXTINF атрибуты, #EXTGRP, #EXTVLCOPT:http-user-agent / http-referrer,
 * Kodi-заголовки после «|», опции SimpleTV «$OPT:…» в адресе.
 */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_\-]+)\s*=\s*"([^"]*)"""")

    data class Result(val channels: List<SourceChannel>, val tvgUrl: String?)

    fun parse(
        text: String,
        source: SourceConfig,
        skipWebPages: Boolean,
        keepVlcOpts: Boolean = true,
    ): Result {
        val out = ArrayList<SourceChannel>()
        var header = emptyMap<String, String>()
        var tvgUrl: String? = null

        var extinf: String? = null
        var group: String? = null
        var vlcUa: String? = null
        var vlcRef: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U") -> {
                    header = attrs(line)
                    tvgUrl = header["url-tvg"] ?: header["x-tvg-url"] ?: tvgUrl
                }
                line.startsWith("#EXTINF") -> {
                    extinf = line
                    group = null
                    vlcUa = null
                    vlcRef = null
                }
                line.startsWith("#EXTGRP:") -> group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:") ->
                    if (keepVlcOpts) {
                        val opt = line.substringAfter(':')
                        when {
                            opt.startsWith("http-user-agent=") -> vlcUa = opt.substringAfter('=').trim()
                            opt.startsWith("http-referrer=") -> vlcRef = opt.substringAfter('=').trim()
                        }
                    }
                line.startsWith("#") -> Unit
                else -> {
                    val inf = extinf ?: continue
                    extinf = null
                    val a = attrs(headPart(inf))
                    val name = namePart(inf).ifBlank { a["tvg-name"] ?: continue }
                    val ch =
                        buildChannel(
                            name = name, url = line, a = a, header = header, source = source,
                            group = a["group-title"] ?: group, vlcUa = vlcUa, vlcRef = vlcRef,
                            skipWebPages = skipWebPages,
                        ) ?: continue
                    out += ch
                }
            }
        }
        return Result(out, tvgUrl)
    }

    private fun attrs(s: String): Map<String, String> =
        attrRegex.findAll(s).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    /** Часть #EXTINF до запятой-разделителя имени (с учётом кавычек). */
    private fun headPart(inf: String): String {
        val idx = nameComma(inf)
        return if (idx < 0) inf else inf.substring(0, idx)
    }

    private fun namePart(inf: String): String {
        val idx = nameComma(inf)
        return if (idx < 0) "" else inf.substring(idx + 1).trim()
    }

    private fun nameComma(inf: String): Int {
        var inQuotes = false
        for (i in inf.indices) {
            val c = inf[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                return i
            }
        }
        return -1
    }

    private fun buildChannel(
        name: String,
        url: String,
        a: Map<String, String>,
        header: Map<String, String>,
        source: SourceConfig,
        group: String?,
        vlcUa: String?,
        vlcRef: String?,
        skipWebPages: Boolean,
    ): SourceChannel? {
        var address = url
        val headers = LinkedHashMap<String, String>()
        var ua = vlcUa
        var ref = vlcRef

        // Kodi-стиль: url|User-Agent=...&Referer=...
        if ('|' in address) {
            val tail = address.substringAfter('|')
            address = address.substringBefore('|')
            tail.split('&').forEach { kv ->
                val k = kv.substringBefore('=').trim()
                val rawValue = kv.substringAfter('=', "")
                // Одна кривая %-последовательность не должна ронять весь плейлист.
                val v = runCatching { java.net.URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
                when (k.lowercase()) {
                    "user-agent" -> ua = v
                    "referer", "referrer" -> ref = v
                    "" -> Unit
                    else -> headers[k] = v
                }
            }
        }

        val resolved = StreamResolver.resolve(address, source.kind) ?: return null
        // SimpleTV-опции $OPT:http-user-agent=… в самом адресе
        resolved.options["http-user-agent"]?.let { ua = it }
        resolved.options["http-referrer"]?.let { ref = it }
        if (skipWebPages && !PublicTv.needsResolution(resolved.url) && StreamResolver.isWebPage(resolved.url)) return null

        val catchup = catchupOf(a, header, resolved.kind)
        val variant =
            StreamVariant(
                url = resolved.url,
                sourceId = source.id,
                sourceName = source.name,
                kind = resolved.kind,
                userAgent = ua ?: source.streamUserAgent,
                referer = ref,
                headers = headers,
                clearKey = resolved.clearKey,
                catchup = catchup,
            )
        val cleanName = name.replace(Regex("\\s+"), " ").trim()
        val logo = a["tvg-logo"]?.ifBlank { null } ?: LogoProvider.findLogo(cleanName)
        return SourceChannel(
            name = cleanName,
            group = (group ?: "").trim().ifEmpty { "Новые каналы" },
            logo = logo,
            tvgId = a["tvg-id"]?.ifBlank { null },
            tvgName = a["tvg-name"]?.ifBlank { null },
            variant = variant,
        )
    }

    private fun catchupOf(
        a: Map<String, String>,
        header: Map<String, String>,
        kind: StreamKind,
    ): Catchup? {
        fun get(k: String) = a[k] ?: header[k]
        val days =
            get("catchup-days")?.toDoubleOrNull()
                ?: get("tvg-rec")?.toDoubleOrNull()
                ?: get("timeshift")?.toDoubleOrNull()
                ?: get("catchup-minutes")?.toDoubleOrNull()?.div(1440.0)
                ?: 0.0
        // Расширения httptimeshift задают архив Wink (3 дня) и Beeline независимо от тегов
        return when (kind) {
            StreamKind.WINK -> Catchup("wink", if (days > 0) days else 3.0)
            StreamKind.BEELINE -> if (days > 0) Catchup("beeline", days) else null
            StreamKind.GENERIC -> {
                if (days <= 0) return null
                val type = (get("catchup") ?: get("catchup-type") ?: "shift").lowercase()
                Catchup(type, days, get("catchup-source"))
            }
        }
    }
}
