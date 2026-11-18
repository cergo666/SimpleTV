package io.mediacast.simpletv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.SourceConfig
import io.mediacast.simpletv.data.EpgSource
import io.mediacast.simpletv.data.SourceType
import io.mediacast.simpletv.data.UserAgents
import android.content.Context
import java.util.Date
import java.util.Locale

private class EditRequest(
    val title: String,
    val fields: List<Pair<String, String>>,
    val presets: List<Pair<String, String>> = emptyList(),
    val singleLine: Boolean = true,
    val onDone: (List<String>) -> Unit,
)

@Composable
fun SettingsScreen(vm: MainViewModel, wide: Boolean, onClose: () -> Unit) {
    val sections = listOf(
        stringResource(R.string.section_playlists),
        stringResource(R.string.section_channels),
        stringResource(R.string.section_epg),
        stringResource(R.string.section_ua),
        stringResource(R.string.section_playback),
        stringResource(R.string.section_appearance),
        stringResource(R.string.section_about),
    )
    var section by rememberSaveable { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<EditRequest?>(null) }
    var revision by remember { mutableIntStateOf(0) } // перерисовка после изменения SharedPreferences
    val bump: () -> Unit = { revision++ }
    BackHandler { if (edit != null) edit = null else onClose() }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstFr.requestFocus() } }

    Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.97f))) {
        val content: @Composable (Modifier) -> Unit = { m ->
            key(revision) {
                Column(m) {
                    when (section) {
                        0 -> PlaylistsSection(vm, bump) { edit = it }
                        1 -> ChannelsSection(vm, bump)
                        2 -> EpgSection(vm, bump) { edit = it }
                        3 -> UaSection(vm, bump) { edit = it }
                        4 -> PlaybackSection(vm, bump)
                        5 -> AppearanceSection(vm, bump)
                        else -> AboutSection()
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(32.dp)) {
                Column(Modifier.width(260.dp).fillMaxHeight()) {
                    Text(stringResource(R.string.settings_title), color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, bottom = 16.dp))
                    sections.forEachIndexed { i, s ->
                        FocusItem(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp), selected = i == section,
                            focusRequester = if (i == 0) firstFr else null,
                            onFocus = { section = i }, onClick = { section = i },
                        ) { _ -> Text(s, color = if (i == section) C.Text else C.Text2, fontSize = 17.sp, modifier = Modifier.padding(14.dp)) }
                    }
                    Spacer(Modifier.weight(1f))
                    FocusItem(Modifier.fillMaxWidth(), onClick = onClose) { _ ->
                        Text(stringResource(R.string.back_to_playback), color = C.Text2, fontSize = 16.sp, modifier = Modifier.padding(14.dp))
                    }
                }
                Spacer(Modifier.width(24.dp))
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusItem(onClick = onClose, focusRequester = firstFr) { _ -> Text(stringResource(R.string.action_back), color = C.Text, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                    Text(stringResource(R.string.settings_title), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(sections.size) { i ->
                        FocusItem(selected = i == section, onClick = { section = i }) { _ ->
                            Text(sections[i], color = if (i == section) C.Text else C.Text2, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
                content(Modifier.weight(1f))
            }
        }
        edit?.let { req -> EditDialog(req, onCancel = { edit = null }) { values -> req.onDone(values); edit = null; bump() } }
    }
}

// ---------------------------------------------------------------- секции

@Composable
private fun PlaylistsSection(vm: MainViewModel, bump: () -> Unit, onEdit: (EditRequest) -> Unit) {
    val busy by vm.playlistBusy.collectAsState()
    val s = vm.settings
    val context = LocalContext.current
    val invalidUrl = stringResource(R.string.error_invalid_url)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Header(stringResource(R.string.sources_header), stringResource(R.string.sources_header_desc)) }
        items(s.sources, key = { it.id }) { src ->
            SettingCard {
                ToggleRow(src.name, src.urls.joinToString("\n") + (src.streamUserAgent?.let { "\nUA: $it" } ?: ""), src.enabled) {
                    vm.updateSource(src.copy(enabled = !src.enabled)); bump()
                }
                if (src.type == SourceType.AGGREGATOR) {
                    ToggleRow(stringResource(R.string.check_streams), stringResource(R.string.check_streams_desc), src.checkStreams) {
                        vm.updateSource(src.copy(checkStreams = !src.checkStreams)); bump()
                    }
                }
                if (!src.builtIn) ActionRow(stringResource(R.string.action_delete_source), null) { vm.removeSource(src); bump() }
            }
        }
        item {
            val request = EditRequest(
                stringResource(R.string.dialog_new_playlist),
                listOf(
                    stringResource(R.string.field_name) to "",
                    stringResource(R.string.field_link_m3u) to "https://",
                    stringResource(R.string.field_ua_optional) to "",
                ),
            ) { v ->
                if (v[1].trim().length > 8) vm.addSource(v[0], v[1], v[2])
                else vm.show(invalidUrl)
            }
            ActionRow(stringResource(R.string.action_add_playlist), stringResource(R.string.add_playlist_desc)) {
                onEdit(request)
            }
        }
        item {
            ActionRow(busy ?: stringResource(R.string.action_refresh_playlists), status(context, s.lastPlaylistUpdate, s.playlistStatus)) { vm.refreshPlaylists() }
        }
        item {
            CycleRow(stringResource(R.string.auto_update_channels), pluralStringResource(R.plurals.auto_update_days, s.playlistIntervalDays, s.playlistIntervalDays)) {
                s.playlistIntervalDays = next(listOf(1, 3, 7, 14), s.playlistIntervalDays); bump()
            }
        }
        item {
            ToggleRow(stringResource(R.string.skip_web_title), stringResource(R.string.skip_web_desc), s.skipWebPages) {
                s.skipWebPages = !s.skipWebPages; bump()
            }
        }
    }
}

@Composable
private fun ChannelsSection(vm: MainViewModel, bump: () -> Unit) {
    val s = vm.settings
    fun toggle(set: () -> Unit) { set(); vm.reapplyFilters(); bump() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Header(stringResource(R.string.channels_header), stringResource(R.string.channels_header_desc)) }
        item {
            ToggleRow(stringResource(R.string.hide_sd_title), stringResource(R.string.hide_sd_desc), s.hideSdDuplicates) {
                toggle { s.hideSdDuplicates = !s.hideSdDuplicates }
            }
        }
        item {
            ToggleRow(
                stringResource(R.string.merge_ott_title),
                stringResource(R.string.merge_ott_desc),
                s.mergeOtt
            ) {
                toggle { s.mergeOtt = !s.mergeOtt }
            }
        }
        item {
            ToggleRow(stringResource(R.string.hide_tz_title), stringResource(R.string.hide_tz_desc), s.hideTimezones) {
                toggle { s.hideTimezones = !s.hideTimezones }
            }
        }
        item {
            ToggleRow(stringResource(R.string.hide_adult_title), stringResource(R.string.hide_adult_desc), s.hideAdult) {
                toggle { s.hideAdult = !s.hideAdult }
            }
        }
        item {
            ToggleRow(stringResource(R.string.prefer_max_title), stringResource(R.string.prefer_max_desc), s.preferMaxQuality) {
                toggle { s.preferMaxQuality = !s.preferMaxQuality }
            }
        }
        item {
            ToggleRow(stringResource(R.string.hd_groups_title), null, s.hdGroups) { toggle { s.hdGroups = !s.hdGroups } }
        }
        item {
            ToggleRow(
                stringResource(R.string.standard_order_title),
                stringResource(R.string.standard_order_desc),
                s.standardOrder
            ) { toggle { s.standardOrder = !s.standardOrder } }
        }
        item {
            ToggleRow(
                stringResource(R.string.reverse_zap_title),
                stringResource(R.string.reverse_zap_desc),
                s.reverseZap
            ) {
                s.reverseZap = !s.reverseZap; bump()
            }
        }
    }
}

@Composable
private fun EpgSection(vm: MainViewModel, bump: () -> Unit, onEdit: (EditRequest) -> Unit) {
    val busy by vm.epgBusy.collectAsState()
    val s = vm.settings
    val context = LocalContext.current
    val invalidUrl = stringResource(R.string.error_invalid_url)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Header(stringResource(R.string.epg_header), stringResource(R.string.epg_header_desc)) }
        items(s.epgSources, key = { it.id }) { src ->
            // Подписи встроенных источников переводятся по стабильному id
            val label = when (src.id) {
                "iptvx_lite" -> stringResource(R.string.epgsrc_iptvx_lite)
                "epgpw" -> stringResource(R.string.epgsrc_epgpw)
                "programtv" -> stringResource(R.string.epgsrc_programtv)
                "iptvx" -> stringResource(R.string.epgsrc_iptvx)
                "edem" -> stringResource(R.string.epgsrc_edem)
                "gabba" -> stringResource(R.string.epgsrc_gabba)
                "glanz" -> stringResource(R.string.epgsrc_glanz)
                else -> src.name
            }
            SettingCard {
                ToggleRow(label, src.url, src.enabled) { vm.updateEpgSource(src.copy(enabled = !src.enabled)); bump() }
                if (src.id !in builtInEpgIds) ActionRow(stringResource(R.string.action_delete), null) { vm.removeEpgSource(src); bump() }
            }
        }
        item {
            val request = EditRequest(
                stringResource(R.string.dialog_epg_source),
                listOf(stringResource(R.string.field_name) to "", stringResource(R.string.field_link) to "https://"),
            ) { v ->
                if (v[1].trim().length > 8) vm.addEpgSource(v[0], v[1])
                else vm.show(invalidUrl)
            }
            ActionRow(stringResource(R.string.action_add_epg), stringResource(R.string.add_epg_desc)) {
                onEdit(request)
            }
        }
        item { ActionRow(busy ?: stringResource(R.string.action_refresh_epg), status(context, s.lastEpgUpdate, s.epgStatus)) { vm.refreshEpg(force = true) } }
        item { ActionRow(stringResource(R.string.action_clear_epg), stringResource(R.string.clear_epg_desc)) { vm.clearEpg() } }
        item {
            CycleRow(stringResource(R.string.auto_update_epg), pluralStringResource(R.plurals.auto_update_epg_desc, s.epgIntervalDays, s.epgIntervalDays)) {
                s.epgIntervalDays = next(listOf(1, 2, 3, 7), s.epgIntervalDays); bump()
            }
        }
    }
}

@Composable
private fun UaSection(vm: MainViewModel, bump: () -> Unit, onEdit: (EditRequest) -> Unit) {
    val s = vm.settings
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Header(stringResource(R.string.ua_header), stringResource(R.string.ua_header_desc)) }
        item {
            val request = EditRequest(
                stringResource(R.string.dialog_global_ua),
                listOf(stringResource(R.string.field_ua) to s.globalUserAgent),
                UserAgents.presets,
                singleLine = false,
            ) { v ->
                if (v[0].isNotBlank()) s.globalUserAgent = v[0].trim()
            }
            ActionRow(stringResource(R.string.ua_global), s.globalUserAgent, mono = true) {
                onEdit(request)
            }
        }
        item {
            val request = EditRequest(
                stringResource(R.string.dialog_epg_ua),
                listOf(stringResource(R.string.field_ua) to s.epgUserAgent),
                UserAgents.presets,
                singleLine = false,
            ) { v ->
                if (v[0].isNotBlank()) s.epgUserAgent = v[0].trim()
            }
            ActionRow(stringResource(R.string.ua_epg), s.epgUserAgent, mono = true) {
                onEdit(request)
            }
        }
        items(s.sources, key = { "ua" + it.id }) { src ->
            val request = EditRequest(
                stringResource(R.string.dialog_source_ua, src.name),
                listOf(stringResource(R.string.field_ua) to (src.streamUserAgent ?: "")),
                UserAgents.presets,
                singleLine = false,
            ) { v ->
                vm.updateSource(src.copy(streamUserAgent = v[0].trim().ifBlank { null }))
            }
            ActionRow(stringResource(R.string.ua_source, src.name), src.streamUserAgent ?: stringResource(R.string.ua_global_value), mono = true) {
                onEdit(request)
            }
        }
        item {
            SettingCard {
                Text(stringResource(R.string.ua_found_header), color = C.Accent2, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                listOf(
                    stringResource(R.string.ua_vlcrc) to UserAgents.VLCRC_GLOBAL,
                    stringResource(R.string.ua_wink) to UserAgents.WINK_TIZEN,
                    stringResource(R.string.ua_beeline) to UserAgents.BEELINE_MANIFEST,
                    stringResource(R.string.ua_tvs) to UserAgents.TVS_DOWNLOAD,
                    stringResource(R.string.ua_check) to UserAgents.DUMP_CHECK,
                ).forEach { (k, v) ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(k, color = C.Text2, fontSize = 14.sp)
                        Text(v, color = C.Text3, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PlaybackSection(vm: MainViewModel, bump: () -> Unit) {
    val s = vm.settings
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Header(stringResource(R.string.playback_header), stringResource(R.string.playback_header_desc)) }
        item {
            CycleRow(stringResource(R.string.buffer_title), stringResource(R.string.buffer_value, s.bufferMs)) {
                s.bufferMs = next(listOf(2000, 4000, 8000, 15000), s.bufferMs); bump()
            }
        }
        item {
            SettingCard {
                Text(
                    stringResource(R.string.remote_help),
                    color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(vm: MainViewModel, bump: () -> Unit) {
    val current = C.accentId
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Header(stringResource(R.string.appearance_header), stringResource(R.string.appearance_header_desc)) }
        item {
            ToggleRow(stringResource(R.string.browse_home_startup), null, vm.settings.showHomeAtStartup) {
                vm.settings.showHomeAtStartup = !vm.settings.showHomeAtStartup
                bump()
            }
        }
        item {
            ToggleRow(stringResource(R.string.browse_preview_first), stringResource(R.string.browse_preview_hint), vm.settings.tvPreviewFirst) {
                vm.settings.tvPreviewFirst = !vm.settings.tvPreviewFirst
                bump()
            }
        }
        items(AccentPalette.entries) { palette ->
            FocusItem(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card),
                selected = palette.id == current,
                onClick = { vm.setAccent(palette.id); bump() },
            ) { _ ->
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(palette.accent, palette.gradientEnd)))
                    )
                    Text(
                        accentLabel(palette),
                        color = C.Text, fontSize = 16.sp,
                        modifier = Modifier.padding(start = 14.dp).weight(1f),
                    )
                    if (palette.id == current) Text("✓", color = C.Accent2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun accentLabel(palette: AccentPalette): String = stringResource(
    when (palette) {
        AccentPalette.VIOLET -> R.string.accent_violet
        AccentPalette.ORANGE -> R.string.accent_orange
        AccentPalette.BLUE -> R.string.accent_blue
        AccentPalette.TEAL -> R.string.accent_teal
        AccentPalette.PINK -> R.string.accent_pink
        AccentPalette.RED -> R.string.accent_red
    }
)

@Composable
private fun AboutSection() {
    LazyColumn {
        item {
            Header(stringResource(R.string.app_name) + " ${io.mediacast.simpletv.BuildConfig.VERSION_NAME}", stringResource(R.string.about_header))
            SettingCard {
                Text(
                    stringResource(R.string.about_body),
                    color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- элементы

/** Id встроенных EPG-источников из core Defaults: их нельзя удалить, подписи переводятся. */
private val builtInEpgIds = setOf("iptvx_lite", "epgpw", "programtv", "iptvx", "edem", "gabba", "glanz")

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = C.Text3, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)) { content() }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onToggle: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth(), onClick = onToggle) { _ ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 16.sp)
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = C.Text3, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Switch(
                checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.Text)
            )
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, mono: Boolean = false, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card), onClick = onClick) { _ ->
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, color = C.Text, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) Text(
                subtitle, color = C.Text3, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

@Composable
private fun CycleRow(title: String, value: String, onClick: () -> Unit) {
    FocusItem(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card), onClick = onClick) { _ ->
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("‹ $value ›", color = C.Accent2, fontSize = 15.sp)
        }
    }
}

@Composable
private fun EditDialog(req: EditRequest, onCancel: () -> Unit, onDone: (List<String>) -> Unit) {
    val values = remember(req) { req.fields.map { mutableStateOf(it.second) } }
    val fr = remember { FocusRequester() }
    LaunchedEffect(req) { delay(80); runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth(0.92f).clip(RoundedCornerShape(20.dp)).background(C.PanelSolid).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(req.title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            req.fields.forEachIndexed { i, (label, _) ->
                OutlinedTextField(
                    value = values[i].value, onValueChange = { values[i].value = it },
                    label = { Text(label) }, singleLine = req.singleLine,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Accent, focusedTextColor = C.Text, unfocusedTextColor = C.Text),
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(fr) else Modifier)
                )
            }
            if (req.presets.isNotEmpty()) {
                Text(stringResource(R.string.presets), color = C.Text3, fontSize = 13.sp)
                req.presets.forEach { (name, ua) ->
                    // Подписи пресетов переводятся по значению UA, а не по исходной строке core.
                    val label = when (ua) {
                        UserAgents.VLCRC_GLOBAL -> stringResource(R.string.preset_vlcrc)
                        UserAgents.WINK_TIZEN -> stringResource(R.string.preset_wink)
                        UserAgents.BEELINE_MANIFEST -> stringResource(R.string.preset_firefox)
                        "VLC/3.0.21 LibVLC/3.0.21" -> stringResource(R.string.preset_vlc)
                        "TV+Android/1.1.1.2 (Linux;Android 7.1.2) ExoPlayerLib/2.4.4" -> stringResource(R.string.preset_exoplayer)
                        else -> name
                    }
                    FocusItem(Modifier.fillMaxWidth(), onClick = { values[0].value = ua }) { _ ->
                        Text(label, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                FocusItem(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Accent), onClick = { onDone(values.map { it.value }) }) { _ ->
                    Text(stringResource(R.string.action_save), color = C.OnAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
                FocusItem(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Card), onClick = onCancel) { _ ->
                    Text(stringResource(R.string.action_cancel), color = C.Text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
    }
}

private fun next(options: List<Int>, cur: Int): Int = options[(options.indexOf(cur) + 1) % options.size]

private fun status(context: Context, time: Long, text: String): String {
    if (time == 0L) return context.getString(R.string.status_never)
    val locale = dateLocale(primaryLocale(context.resources.configuration).language)
    val dtf = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, locale)
    return context.getString(R.string.status_last, dtf.format(Date(time))) + if (text.isNotBlank()) " • $text" else ""
}
