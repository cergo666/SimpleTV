package io.mediacast.simpletv.data

/**
 * Фильтры и порядок каналов поверх объединённого списка источников:
 * скрытие SD-дублей, объединение OTT-версий, каналов других часовых поясов,
 * фильтрация каналов для взрослых, общепринятый порядок первых 20 кнопок.
 */
object ChannelArranger {
    data class Options(
        val hideSdDuplicates: Boolean,
        val hideTimezones: Boolean,
        val standardOrder: Boolean,
        val mergeOtt: Boolean = true,
        val hideAdult: Boolean = true,
        val preferMaxQuality: Boolean = true,
    )

    /**
     * Первый и второй мультиплексы цифрового ТВ России (общероссийские обязательные
     * общедоступные каналы, «кнопки» 1–20 в LCN приставок и пультов).
     */
    private val standard: List<List<String>> =
        listOf(
            listOf("первый канал", "первый", "1 канал"),
            listOf("россия 1", "россия"),
            listOf("матч тв", "матч", "матч!"),
            listOf("нтв"),
            listOf("пятый канал", "5 канал", "петербург 5 канал"),
            listOf("россия к", "россия культура", "культура"),
            listOf("россия 24"),
            listOf("карусель"),
            listOf("отр"),
            listOf("тв центр", "твц", "тв центр москва"),
            listOf("рен тв", "рен"),
            listOf("спас"),
            listOf("стс"),
            listOf("домашний"),
            listOf("тв 3", "тв3"),
            listOf("пятница", "пятница!"),
            listOf("звезда"),
            listOf("мир"),
            listOf("тнт"),
            listOf("муз тв"),
        ).map { aliases -> aliases.map { Text.looseKey(it) } }

    /**
     * "Первый канал +4", "Россия 1 (+2)", "НТВ (Восток)", "СТС +7 HD", "ТНТ (UTC+3)".
     *
     * Android не поддерживает inline-флаг U (UNICODE_CHARACTER_CLASS): `(?iuU)`
     * падает с PatternSyntaxException при загрузке класса. Кириллица в шаблоне
     * уже задана явно, поэтому достаточно `(?iu)`.
     */
    private val tzRegex =
        Regex(
            """(?iu)(\s|\()[+\-−]\s?\d{1,2}(\s?ч\.?)?\)?(\s|$)|\(\s*(восток|орбита|дальний восток|сибирь|урал)\s*\)|\bорбита\b|\butc\s?[+\-]\s?\d""",
        )

    private val adultRegex =
        Regex(
            """(?iu)\b(18\+|xxx|brazzers|hustler|babes|playboy|exxxotica|dorcel|penthouse|candy|erox|erotika|erotic|эротика|эрокс|шалун|flirt)\b|русская\s*ночь|ночной\s*клуб|red\s*lips|blue\s*hustler|barely\s*legal""",
        )
    private val adultGroupRegex =
        Regex(
            """(?i)взросл|18\+|xxx|эротик|adult|ночн|night|секс|porn|порно""",
        )

    fun isAdult(
        name: String,
        group: String?,
    ): Boolean =
        (group != null && adultGroupRegex.containsMatchIn(group)) || adultRegex.containsMatchIn(name)

    fun isOtherTimezone(name: String): Boolean = tzRegex.containsMatchIn(name)

    fun isUhd(name: String): Boolean {
        val k = Text.channelKey(name)
        return uhdRegex.containsMatchIn(k)
    }

    fun isHd(name: String): Boolean {
        val k = Text.channelKey(name)
        return isUhd(name) || hdRegex.containsMatchIn(k)
    }

    private val uhdRegex = Regex("""\b(4k|uhd|2160p?|8k)\b""")
    private val hdRegex = Regex("""\b(hd|fhd|1080p?|720p?)\b""")

    /** "Первый канал HD (OTT)", "ТНТ (OTT)", "СТС OTT", "Ю [OTT]". */
    private val ottRegex = Regex("""(?i)\b(ott)\b""")
    private val ottMarkerRegex = Regex("""(?i)\s*[\(\[]?\s*ott\s*[\)\]]?""")

    fun isOtt(name: String): Boolean = ottRegex.containsMatchIn(name)

    fun cleanOtt(name: String): String =
        name.replace(ottMarkerRegex, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun variantScore(v: StreamVariant): Int {
        val u = v.url.lowercase()
        val s = v.sourceName.lowercase()
        var score = 100
        if (u.contains("4k") || u.contains("uhd") || u.contains("2160") || s.contains("4k") || s.contains("uhd")) {
            score = 400
        } else if (u.contains("1080") || u.contains("fhd") || s.contains("1080") || s.contains("fhd")) {
            score = 300
        } else if (u.contains("720") || u.contains("hd") || s.contains("hd")) {
            score = 200
        }
        return score
    }

    private fun sourcePriority(v: StreamVariant): Int =
        when (v.sourceId) {
            "beeline" -> 0
            "wink" -> if (isOtt(v.sourceName)) 1 else 2
            else -> 3
        }

    private val sdPath = Regex("""/(?:ch_[^/]*sd|sd)(?:/|$)""", RegexOption.IGNORE_CASE)

    private fun isExplicitSd(v: StreamVariant): Boolean =
        sdPath.containsMatchIn(v.url.substringBefore('?').substringBefore('#'))

    fun arrange(
        list: List<Channel>,
        o: Options,
    ): List<Channel> {
        var out = list
        if (o.hideAdult) out = out.filter { !isAdult(it.name, it.group) }
        if (o.hideTimezones) out = out.filter { !isOtherTimezone(it.name) }
        if (o.mergeOtt) out = mergeOtt(out)
        if (o.hideSdDuplicates) out = hideSd(out)
        if (o.standardOrder) out = order(out, o.preferMaxQuality)
        val order =
            compareBy<StreamVariant> { sourcePriority(it) }
                .thenByDescending { if (o.preferMaxQuality) variantScore(it) else 0 }
        out =
            out.map { channel ->
                val variants =
                    if (o.hideSdDuplicates && isHd(channel.name)) {
                        channel.variants.filterNot(::isExplicitSd).ifEmpty { channel.variants }
                    } else {
                        channel.variants
                    }
                channel.copy(variants = variants.sortedWith(order))
            }
        return out.mapIndexed { i, ch -> ch.copy(number = i + 1) }
    }

    /**
     * Каналы с пометкой «OTT» объединяются с одноимёнными основными версиями:
     * их потоки становятся резервными источниками (с пометкой "(OTT)").
     */
    private fun mergeOtt(list: List<Channel>): List<Channel> {
        val nonOttByKey = list.filter { !isOtt(it.name) }.associateBy { it.key }
        if (nonOttByKey.isEmpty()) return list

        val absorbed = HashSet<String>()
        val extra = HashMap<String, MutableList<StreamVariant>>()

        for (ch in list) {
            if (!isOtt(ch.name)) continue
            val cleanName = cleanOtt(ch.name)
            val baseKey = Text.channelKey(cleanName)
            val target = nonOttByKey[baseKey] ?: continue

            absorbed += ch.key
            val targetVariants = extra.getOrPut(target.key) { ArrayList() }
            ch.variants.forEach { v ->
                val label =
                    if (v.sourceName.contains("ott", ignoreCase = true)) {
                        v.sourceName
                    } else {
                        "${v.sourceName} (OTT)"
                    }
                targetVariants += v.copy(sourceName = label)
            }
        }

        if (absorbed.isEmpty()) return list

        return list.filter { it.key !in absorbed }.map { ch ->
            val add = extra[ch.key]
            if (add != null) {
                ch.copy(variants = (ch.variants + add).distinctBy { it.url })
            } else {
                ch
            }
        }
    }

    /**
     * Если есть HD-версия канала, SD-версию не показываем, но её потоки становятся
     * резервными источниками HD-канала (переключение при ошибке и вручную).
     */
    private fun hideSd(list: List<Channel>): List<Channel> {
        val byLoose = list.groupBy { Text.looseKey(it.name) }
        val absorbed = HashSet<String>()
        val extra = HashMap<String, List<StreamVariant>>()
        for ((_, group) in byLoose) {
            if (group.size < 2) continue
            val hd = group.filter { isHd(it.name) }
            if (hd.isEmpty()) continue
            val best = hd.firstOrNull { !isUhd(it.name) } ?: hd.first()
            val sd = group.filter { !isHd(it.name) }
            if (sd.isEmpty()) continue
            sd.forEach { absorbed += it.key }
            extra[best.key] = sd.flatMap { it.variants }
        }
        if (absorbed.isEmpty()) return list
        return list.filter { it.key !in absorbed }.map { ch ->
            extra[ch.key]?.let { add -> ch.copy(variants = (ch.variants + add).distinctBy { it.url }) } ?: ch
        }
    }

    private fun order(
        list: List<Channel>,
        preferMaxQuality: Boolean,
    ): List<Channel> {
        val slots = arrayOfNulls<Channel>(standard.size)
        val extras = ArrayList<Channel>()
        val rest = ArrayList<Channel>()
        val candidates =
            if (preferMaxQuality) {
                list.sortedByDescending { if (isHd(it.name)) 1 else 0 }
            } else {
                list.sortedBy { if (isHd(it.name)) 1 else 0 }
            }
        val ranked = HashSet<String>()
        for (ch in candidates) {
            val k = Text.looseKey(ch.name)
            val i = standard.indexOfFirst { k in it }
            if (i < 0) continue
            ranked += ch.key
            if (slots[i] == null && !Regex(""" (ott|orig) """).containsMatchIn(Text.channelKey(ch.name))) slots[i] = ch
        }
        for (ch in list) {
            when {
                ch.key !in ranked -> rest += ch
                slots.none { it?.key == ch.key } -> extras += ch
            }
        }
        return slots.filterNotNull() + extras + rest
    }
}
