package io.mediacast.simpletv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.mediacast.simpletv.BuildConfig
import io.mediacast.simpletv.R
import io.mediacast.simpletv.data.UserAgents
import kotlinx.coroutines.delay
import java.util.Date

private class EditRequest(
    val title: String,
    val fields: List<Pair<String, String>>,
    val presets: List<Pair<String, String>> = emptyList(),
    val singleLine: Boolean = true,
    val onDone: (List<String>) -> Unit,
)

@Composable
fun SettingsScreen(vm: MainViewModel, isTv: Boolean, wide: Boolean, onClose: () -> Unit) {
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
    val bump: () -> Unit = { vm.notifySettingsChanged() }
    val handleBack = { if (edit != null) edit = null else onClose() }
    BackHandler(onBack = handleBack)
    val firstFr = remember { FocusRequester() }
    val sectionFrs = remember(sections.size) { List(sections.size) { FocusRequester() } }
    val contentFr = remember { FocusRequester() }
    var focusContentNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { (sectionFrs.getOrNull(section) ?: firstFr).requestFocus() }
    }
    LaunchedEffect(focusContentNonce) {
        if (focusContentNonce == 0) return@LaunchedEffect
        delay(40)
        runCatching { contentFr.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg.copy(alpha = 0.97f)),
    ) {
        val content: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                when (section) {
                    0 -> PlaylistsSection(vm, contentFr, bump) { edit = it }
                    1 -> ChannelsSection(vm, contentFr, bump)
                    2 -> EpgSection(vm, contentFr, bump) { edit = it }
                    3 -> UaSection(vm, contentFr, bump) { edit = it }
                    4 -> PlaybackSection(vm, contentFr, bump)
                    5 -> AppearanceSection(vm, isTv, contentFr, bump)
                    else -> AboutSection()
                }
            }
        }
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isTv) Modifier.padding(horizontal = TvSafe.horizontal, vertical = TvSafe.vertical)
                        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.xl),
                    ),
            ) {
                Column(Modifier.width(260.dp).fillMaxHeight()) {
                    Text(
                        stringResource(R.string.settings_title),
                        color = C.Text,
                        fontSize = AppType.display,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = AppSpace.sm, bottom = AppSpace.lg),
                    )
                    sections.forEachIndexed { i, s ->
                        val isSelected = i == section
                        FocusItem(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                                        section = i
                                        focusContentNonce++
                                        true
                                    } else {
                                        false
                                    }
                                },
                            selected = isSelected,
                            shape = RoundedCornerShape(AppRadius.control),
                            focusRequester = sectionFrs[i],
                            onFocus = { section = i },
                            onClick = {
                                section = i
                                focusContentNonce++
                            },
                        ) { _ ->
                            Row(
                                Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SelectedBar(isSelected)
                                Spacer(Modifier.width(AppSpace.sm))
                                Text(
                                    s,
                                    color = if (isSelected) C.Text else C.Text2,
                                    fontSize = AppType.subtitle,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FocusItem(
                        Modifier.fillMaxWidth(),
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        onClick = onClose,
                    ) { _ ->
                        Row(
                            Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = C.Text2, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpace.sm))
                            Text(stringResource(R.string.back_to_playback), color = C.Text, fontSize = AppType.body)
                        }
                    }
                }
                Spacer(Modifier.width(AppSpace.xl))
                content(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                                runCatching { sectionFrs[section].requestFocus() }
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(AppSpace.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusItem(Modifier.size(48.dp), shape = CircleShape, focusRequester = firstFr, onClick = onClose) { _ ->
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_close),
                            tint = C.Text,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Spacer(Modifier.width(AppSpace.xs))
                    Text(stringResource(R.string.settings_title), color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
                    contentPadding = PaddingValues(vertical = AppSpace.sm),
                ) {
                    items(sections.size) { i ->
                        val isSelected = i == section
                        FocusItem(
                            selected = isSelected,
                            containerColor = if (isSelected) C.Accent else C.Card,
                            borderColor = if (isSelected) Color.Transparent else C.Border,
                            shape = RoundedCornerShape(50),
                            onClick = { section = i },
                        ) { _ ->
                            Text(
                                sections[i],
                                color = if (isSelected) C.OnAccent else C.Text2,
                                fontSize = AppType.body,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                            )
                        }
                    }
                }
                content(Modifier.weight(1f))
            }
        }
        edit?.let { req ->
            EditDialog(req, isTv = isTv, onCancel = { edit = null }) { values ->
                req.onDone(values)
                edit = null
                bump()
            }
        }
    }
}

// ---------------------------------------------------------------- секции

@Composable
private fun PlaylistsSection(vm: MainViewModel, firstFr: FocusRequester, bump: () -> Unit, onEdit: (EditRequest) -> Unit) {
    val busy by vm.playlistBusy.collectAsState()
    val revision by vm.settingsRevision.collectAsState()
    val s = vm.settings
    val sources = remember(revision) { s.sources }
    val intervalDays = s.playlistIntervalDays
    val skipWeb = s.skipWebPages
    val context = LocalContext.current
    val statusText = status(context, s.lastPlaylistUpdate, s.playlistStatus)
    val invalidUrl = stringResource(R.string.error_invalid_url)
    val firstSourceId = sources.firstOrNull()?.id

    LazyColumn(
        contentPadding = PaddingValues(AppSpace.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        item { Header(stringResource(R.string.sources_header), stringResource(R.string.sources_header_desc)) }
        items(sources, key = { it.id }) { src ->
            SettingCard {
                ToggleRow(
                    src.name,
                    src.urls.joinToString("\n") + (src.streamUserAgent?.let { "\nUA: $it" } ?: ""),
                    src.enabled,
                    inCard = true,
                    focusRequester = if (src.id == firstSourceId) firstFr else null,
                ) {
                    vm.updateSource(src.copy(enabled = !src.enabled))
                    bump()
                }
                if (src.type == io.mediacast.simpletv.data.SourceType.AGGREGATOR) {
                    HorizontalDivider(color = C.Divider)
                    ToggleRow(stringResource(R.string.check_streams), stringResource(R.string.check_streams_desc), src.checkStreams, inCard = true) {
                        vm.updateSource(src.copy(checkStreams = !src.checkStreams))
                        bump()
                    }
                }
                if (!src.builtIn) {
                    HorizontalDivider(color = C.Divider)
                    ActionRow(stringResource(R.string.action_delete_source), null, icon = Icons.Default.Delete, inCard = true) {
                        vm.removeSource(src)
                        bump()
                    }
                }
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
            ActionRow(
                stringResource(R.string.action_add_playlist),
                stringResource(R.string.add_playlist_desc),
                icon = Icons.Default.Add,
                focusRequester = if (firstSourceId == null) firstFr else null,
            ) {
                onEdit(request)
            }
        }
        item {
            ActionRow(busy ?: stringResource(R.string.action_refresh_playlists), statusText, icon = Icons.Default.Refresh) {
                vm.refreshPlaylists()
            }
        }
        item {
            CycleRow(
                stringResource(R.string.auto_update_channels),
                pluralStringResource(R.plurals.auto_update_days, intervalDays, intervalDays),
            ) {
                s.playlistIntervalDays = next(listOf(1, 3, 7, 14), intervalDays)
                bump()
            }
        }
        item {
            ToggleRow(stringResource(R.string.skip_web_title), stringResource(R.string.skip_web_desc), skipWeb) {
                s.skipWebPages = !skipWeb
                bump()
            }
        }
    }
}

@Composable
private fun ChannelsSection(vm: MainViewModel, firstFr: FocusRequester, bump: () -> Unit) {
    val revision by vm.settingsRevision.collectAsState()
    val s = vm.settings
    val hideSd = remember(revision) { s.hideSdDuplicates }
    val mergeOtt = remember(revision) { s.mergeOtt }
    val hideTz = remember(revision) { s.hideTimezones }
    val hideAdult = remember(revision) { s.hideAdult }
    val preferMax = remember(revision) { s.preferMaxQuality }
    val hdGroups = remember(revision) { s.hdGroups }
    val standardOrder = remember(revision) { s.standardOrder }
    val reverseZap = remember(revision) { s.reverseZap }

    fun toggle(set: () -> Unit) {
        set()
        vm.reapplyFilters()
        bump()
    }

    LazyColumn(
        contentPadding = PaddingValues(AppSpace.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        item { Header(stringResource(R.string.channels_header), stringResource(R.string.channels_header_desc)) }
        item {
            ToggleRow(stringResource(R.string.hide_sd_title), stringResource(R.string.hide_sd_desc), hideSd, focusRequester = firstFr) {
                toggle { s.hideSdDuplicates = !hideSd }
            }
        }
        item {
            ToggleRow(stringResource(R.string.merge_ott_title), stringResource(R.string.merge_ott_desc), mergeOtt) {
                toggle { s.mergeOtt = !mergeOtt }
            }
        }
        item {
            ToggleRow(stringResource(R.string.hide_tz_title), stringResource(R.string.hide_tz_desc), hideTz) {
                toggle { s.hideTimezones = !hideTz }
            }
        }
        item {
            ToggleRow(stringResource(R.string.hide_adult_title), stringResource(R.string.hide_adult_desc), hideAdult) {
                toggle { s.hideAdult = !hideAdult }
            }
        }
        item {
            ToggleRow(stringResource(R.string.prefer_max_title), stringResource(R.string.prefer_max_desc), preferMax) {
                toggle { s.preferMaxQuality = !preferMax }
            }
        }
        item {
            ToggleRow(stringResource(R.string.hd_groups_title), null, hdGroups) {
                toggle { s.hdGroups = !hdGroups }
            }
        }
        item {
            ToggleRow(stringResource(R.string.standard_order_title), stringResource(R.string.standard_order_desc), standardOrder) {
                toggle { s.standardOrder = !standardOrder }
            }
        }
        item {
            ToggleRow(stringResource(R.string.reverse_zap_title), stringResource(R.string.reverse_zap_desc), reverseZap) {
                s.reverseZap = !reverseZap
                bump()
            }
        }
    }
}

@Composable
private fun EpgSection(vm: MainViewModel, firstFr: FocusRequester, bump: () -> Unit, onEdit: (EditRequest) -> Unit) {
    val busy by vm.epgBusy.collectAsState()
    val revision by vm.settingsRevision.collectAsState()
    val s = vm.settings
    val epgSources = remember(revision) { s.epgSources }
    val intervalDays = s.epgIntervalDays
    val context = LocalContext.current
    val statusText = status(context, s.lastEpgUpdate, s.epgStatus)
    val invalidUrl = stringResource(R.string.error_invalid_url)
    val firstEpgId = epgSources.firstOrNull()?.id

    LazyColumn(
        contentPadding = PaddingValues(AppSpace.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        item { Header(stringResource(R.string.epg_header), stringResource(R.string.epg_header_desc)) }
        items(epgSources, key = { it.id }) { src ->
            val label = when (src.id) {
                "iptvx_lite" -> stringResource(R.string.epgsrc_iptvx_lite)
                "epgpw" -> stringResource(R.string.epgsrc_epgpw)
                "teleguide" -> stringResource(R.string.epgsrc_teleguide)
                "programtv" -> stringResource(R.string.epgsrc_programtv)
                "iptvx" -> stringResource(R.string.epgsrc_iptvx)
                "edem" -> stringResource(R.string.epgsrc_edem)
                "gabba" -> stringResource(R.string.epgsrc_gabba)
                "glanz" -> stringResource(R.string.epgsrc_glanz)
                else -> src.name
            }
            SettingCard {
                ToggleRow(
                    label,
                    src.url,
                    src.enabled,
                    inCard = true,
                    focusRequester = if (src.id == firstEpgId) firstFr else null,
                ) {
                    vm.updateEpgSource(src.copy(enabled = !src.enabled))
                    bump()
                }
                if (src.id !in builtInEpgIds) {
                    HorizontalDivider(color = C.Divider)
                    ActionRow(stringResource(R.string.action_delete), null, icon = Icons.Default.Delete, inCard = true) {
                        vm.removeEpgSource(src)
                        bump()
                    }
                }
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
            ActionRow(
                stringResource(R.string.action_add_epg),
                stringResource(R.string.add_epg_desc),
                icon = Icons.Default.Add,
                focusRequester = if (firstEpgId == null) firstFr else null,
            ) {
                onEdit(request)
            }
        }
        item {
            ActionRow(busy ?: stringResource(R.string.action_refresh_epg), statusText, icon = Icons.Default.Refresh) {
                vm.refreshEpg(force = true)
            }
        }
        item {
            ActionRow(stringResource(R.string.action_clear_epg), stringResource(R.string.clear_epg_desc), icon = Icons.Default.Delete) {
                vm.clearEpg()
            }
        }
        item {
            CycleRow(
                stringResource(R.string.auto_update_epg),
                pluralStringResource(R.plurals.auto_update_epg_desc, intervalDays, intervalDays),
            ) {
                s.epgIntervalDays = next(listOf(1, 2, 3, 7), intervalDays)
                bump()
            }
        }
    }
}

@Composable
private fun UaSection(vm: MainViewModel, firstFr: FocusRequester, bump: () -> Unit, onEdit: (EditRequest) -> Unit) {
    val revision by vm.settingsRevision.collectAsState()
    val s = vm.settings
    val globalUa = remember(revision) { s.globalUserAgent }
    val epgUa = remember(revision) { s.epgUserAgent }
    val sources = remember(revision) { s.sources }

    LazyColumn(
        contentPadding = PaddingValues(AppSpace.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        item { Header(stringResource(R.string.ua_header), stringResource(R.string.ua_header_desc)) }
        item {
            val request = EditRequest(
                stringResource(R.string.dialog_global_ua),
                listOf(stringResource(R.string.field_ua) to globalUa),
                UserAgents.presets,
                singleLine = false,
            ) { v ->
                if (v[0].isNotBlank()) s.globalUserAgent = v[0].trim()
                bump()
            }
            ActionRow(stringResource(R.string.ua_global), globalUa, mono = true, focusRequester = firstFr) {
                onEdit(request)
            }
        }
        item {
            val request = EditRequest(
                stringResource(R.string.dialog_epg_ua),
                listOf(stringResource(R.string.field_ua) to epgUa),
                UserAgents.presets,
                singleLine = false,
            ) { v ->
                if (v[0].isNotBlank()) s.epgUserAgent = v[0].trim()
                bump()
            }
            ActionRow(stringResource(R.string.ua_epg), epgUa, mono = true) {
                onEdit(request)
            }
        }
        items(sources, key = { "ua" + it.id }) { src ->
            val request = EditRequest(
                stringResource(R.string.dialog_source_ua, src.name),
                listOf(stringResource(R.string.field_ua) to (src.streamUserAgent ?: "")),
                UserAgents.presets,
                singleLine = false,
            ) { v ->
                vm.updateSource(src.copy(streamUserAgent = v[0].trim().ifBlank { null }))
                bump()
            }
            ActionRow(stringResource(R.string.ua_source, src.name), src.streamUserAgent ?: stringResource(R.string.ua_global_value), mono = true) {
                onEdit(request)
            }
        }
        item {
            SettingCard {
                Text(
                    stringResource(R.string.ua_found_header),
                    color = C.Accent2,
                    fontSize = AppType.body,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(AppSpace.md),
                )
                listOf(
                    stringResource(R.string.ua_vlcrc) to UserAgents.VLCRC_GLOBAL,
                    stringResource(R.string.ua_wink) to UserAgents.WINK_TIZEN,
                    stringResource(R.string.ua_beeline) to UserAgents.BEELINE_MANIFEST,
                    stringResource(R.string.ua_tvs) to UserAgents.TVS_DOWNLOAD,
                    stringResource(R.string.ua_check) to UserAgents.DUMP_CHECK,
                ).forEach { (k, v) ->
                    Column(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.xs)) {
                        Text(k, color = C.Text2, fontSize = AppType.body)
                        Text(v, color = C.Text3, fontSize = AppType.caption, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(AppSpace.sm))
            }
        }
    }
}

@Composable
private fun PlaybackSection(vm: MainViewModel, firstFr: FocusRequester, bump: () -> Unit) {
    val revision by vm.settingsRevision.collectAsState()
    val s = vm.settings
    val bufferMs = remember(revision) { s.bufferMs }

    LazyColumn(
        contentPadding = PaddingValues(AppSpace.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        item { Header(stringResource(R.string.playback_header), stringResource(R.string.playback_header_desc)) }
        item {
            CycleRow(stringResource(R.string.buffer_title), stringResource(R.string.buffer_value, bufferMs), focusRequester = firstFr) {
                s.bufferMs = next(listOf(2000, 4000, 8000, 15000), bufferMs)
                bump()
            }
        }
        item {
            SettingCard {
                Text(
                    stringResource(R.string.remote_help),
                    color = C.Text2,
                    fontSize = AppType.body,
                    modifier = Modifier.padding(AppSpace.lg),
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(vm: MainViewModel, isTv: Boolean, firstFr: FocusRequester, bump: () -> Unit) {
    val revision by vm.settingsRevision.collectAsState()
    val uiMode by vm.uiMode.collectAsState()
    val current = remember(revision) { C.accentId }
    val showHome = remember(revision) { vm.settings.showHomeAtStartup }
    val tvPreview = remember(revision) { vm.settings.tvPreviewFirst }
    val uiModes = listOf("auto", "tv", "phone")
    val uiModeLabel = when (uiMode) {
        "tv" -> stringResource(R.string.ui_mode_tv)
        "phone" -> stringResource(R.string.ui_mode_phone)
        else -> stringResource(R.string.ui_mode_auto)
    }

    LazyColumn(
        contentPadding = PaddingValues(AppSpace.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
    ) {
        item { Header(stringResource(R.string.appearance_interface_header), stringResource(R.string.appearance_interface_desc)) }
        item {
            CycleRow(stringResource(R.string.ui_mode_title), uiModeLabel, focusRequester = firstFr) {
                val nextMode = uiModes[(uiModes.indexOf(uiMode).coerceAtLeast(0) + 1) % uiModes.size]
                vm.setUiMode(nextMode)
                bump()
            }
        }
        item {
            ToggleRow(stringResource(R.string.browse_home_startup), null, showHome) {
                vm.settings.showHomeAtStartup = !showHome
                bump()
            }
        }
        if (isTv) {
            item {
                ToggleRow(stringResource(R.string.browse_preview_first), stringResource(R.string.browse_preview_hint), tvPreview) {
                    vm.settings.tvPreviewFirst = !tvPreview
                    bump()
                }
            }
        }
        item {
            Spacer(Modifier.height(AppSpace.md))
            Header(stringResource(R.string.appearance_header), stringResource(R.string.appearance_header_desc))
        }
        items(AccentPalette.entries, key = { it.id }) { palette ->
            val selected = palette.id == current
            FocusItem(
                Modifier.fillMaxWidth(),
                selected = selected,
                containerColor = C.Card,
                borderColor = C.Border,
                shape = RoundedCornerShape(AppRadius.panel),
                onClick = {
                    vm.setAccent(palette.id)
                    bump()
                },
            ) { _ ->
                Row(
                    Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(palette.accent, palette.gradientEnd))),
                    )
                    Text(
                        accentLabel(palette),
                        color = C.Text,
                        fontSize = AppType.subtitle,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = AppSpace.md).weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Default.Check, null, tint = C.Accent2, modifier = Modifier.size(20.dp))
                    }
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
    },
)

@Composable
private fun AboutSection() {
    LazyColumn(contentPadding = PaddingValues(AppSpace.xs)) {
        item {
            Header(stringResource(R.string.app_name) + " ${BuildConfig.VERSION_NAME}", stringResource(R.string.about_header))
            SettingCard {
                Text(
                    stringResource(R.string.about_body),
                    color = C.Text2,
                    fontSize = AppType.body,
                    modifier = Modifier.padding(AppSpace.lg),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- элементы

/** Id встроенных EPG-источников из core Defaults: их нельзя удалить, подписи переводятся. */
private val builtInEpgIds = setOf("iptvx_lite", "epgpw", "teleguide", "programtv", "iptvx", "edem", "gabba", "glanz")

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = AppSpace.sm)) {
        Text(title, color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = C.Text3, fontSize = AppType.body, modifier = Modifier.padding(top = AppSpace.xs))
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.panel))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(AppRadius.panel)),
    ) { content() }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    inCard: Boolean = false,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit,
) {
    FocusItem(
        Modifier.fillMaxWidth(),
        containerColor = if (inCard) Color.Transparent else C.Card,
        borderColor = if (inCard) Color.Transparent else C.Border,
        shape = RoundedCornerShape(AppRadius.panel),
        focusRequester = focusRequester,
        onClick = onToggle,
    ) { _ ->
        Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = AppSpace.md)) {
                Text(title, color = C.Text, fontSize = AppType.subtitle)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = C.Text3, fontSize = AppType.caption, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = C.Accent,
                    checkedThumbColor = C.OnAccent,
                    uncheckedTrackColor = C.PanelSolid,
                    uncheckedThumbColor = C.Text3,
                    uncheckedBorderColor = C.Border,
                ),
            )
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    mono: Boolean = false,
    inCard: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusItem(
        Modifier.fillMaxWidth(),
        containerColor = if (inCard) Color.Transparent else C.Card,
        borderColor = if (inCard) Color.Transparent else C.Border,
        shape = RoundedCornerShape(AppRadius.panel),
        focusRequester = focusRequester,
        onClick = onClick,
    ) { _ ->
        Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = C.Accent2, modifier = Modifier.padding(end = AppSpace.md).size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = AppType.subtitle)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = C.Text3,
                        fontSize = AppType.caption,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    )
                }
            }
        }
    }
}

@Composable
private fun CycleRow(
    title: String,
    value: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusItem(
        Modifier.fillMaxWidth(),
        containerColor = C.Card,
        borderColor = C.Border,
        shape = RoundedCornerShape(AppRadius.panel),
        focusRequester = focusRequester,
        onClick = onClick,
    ) { _ ->
        Row(Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.Text, fontSize = AppType.subtitle, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
                Text(value, color = C.Accent2, fontSize = AppType.body, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.Accent2, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditDialog(req: EditRequest, isTv: Boolean, onCancel: () -> Unit, onDone: (List<String>) -> Unit) {
    val values = remember(req) { req.fields.map { mutableStateOf(it.second) } }
    val fr = remember { FocusRequester() }
    LaunchedEffect(req) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg.copy(alpha = 0.88f))
            .imePadding()
            .padding(horizontal = if (isTv) TvSafe.horizontal else AppSpace.lg, vertical = AppSpace.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(AppRadius.card))
                .background(C.PanelSolid)
                .border(1.dp, C.Border, RoundedCornerShape(AppRadius.card))
                .padding(AppSpace.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md),
        ) {
            Text(req.title, color = C.Text, fontSize = AppType.title, fontWeight = FontWeight.Bold)
            req.fields.forEachIndexed { i, (label, _) ->
                OutlinedTextField(
                    value = values[i].value,
                    onValueChange = { values[i].value = it },
                    label = { Text(label) },
                    singleLine = req.singleLine,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Accent2,
                        unfocusedBorderColor = C.Border,
                        focusedLabelColor = C.Accent2,
                        unfocusedLabelColor = C.Text3,
                        focusedTextColor = C.Text,
                        unfocusedTextColor = C.Text,
                        cursorColor = C.Accent2,
                    ),
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(fr) else Modifier),
                )
            }
            if (req.presets.isNotEmpty()) {
                Text(stringResource(R.string.presets), color = C.Text3, fontSize = AppType.caption)
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
                    FocusItem(
                        Modifier.fillMaxWidth(),
                        containerColor = C.Card,
                        borderColor = C.Border,
                        shape = RoundedCornerShape(AppRadius.control),
                        onClick = { values[0].value = ua },
                    ) { _ ->
                        Text(label, color = C.Text2, fontSize = AppType.body, modifier = Modifier.padding(horizontal = AppSpace.md, vertical = AppSpace.sm))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.md), modifier = Modifier.padding(top = AppSpace.xs)) {
                FocusItem(
                    containerColor = C.Accent,
                    prominent = true,
                    liftOnFocus = true,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = { onDone(values.map { it.value }) },
                ) { _ ->
                    Text(
                        stringResource(R.string.action_save),
                        color = C.OnAccent,
                        fontSize = AppType.body,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = AppSpace.xl, vertical = AppSpace.sm),
                    )
                }
                FocusItem(
                    containerColor = C.Card,
                    borderColor = C.Border,
                    liftOnFocus = true,
                    shape = RoundedCornerShape(AppRadius.control),
                    onClick = onCancel,
                ) { _ ->
                    Text(
                        stringResource(R.string.action_cancel),
                        color = C.Text,
                        fontSize = AppType.body,
                        modifier = Modifier.padding(horizontal = AppSpace.xl, vertical = AppSpace.sm),
                    )
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
