import 'package:flutter/material.dart';

import '../../data/models/settings_model.dart';
import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';

class SettingsOverlay extends StatefulWidget {
  final PlayerViewModel vm;

  const SettingsOverlay({super.key, required this.vm});

  @override
  State<SettingsOverlay> createState() => _SettingsOverlayState();
}

String _epgSourceLabel(EpgSourceConfig source) =>
    epgSourceLabel(source.id, source.name);

class _SettingsOverlayState extends State<SettingsOverlay> {
  Future<void> _showAddSourceDialog() async {
    final nameCtrl = TextEditingController();
    final urlCtrl = TextEditingController();
    final uaCtrl = TextEditingController();

    await showDialog(
      context: context,
      builder: (ctx) {
        var urlError = false;
        return StatefulBuilder(
          builder: (ctx, setDialogState) => AlertDialog(
            backgroundColor: AppColors.overlayBackground,
            title: Text(
              L10n.t.addPlaylistTitle,
              style: const TextStyle(color: AppColors.text),
            ),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  style: const TextStyle(color: AppColors.text),
                  decoration: InputDecoration(
                    labelText: L10n.t.playlistName,
                    labelStyle: const TextStyle(color: AppColors.textSecondary),
                    hintText: L10n.t.playlistNameHint,
                    hintStyle: const TextStyle(color: AppColors.textMuted),
                  ),
                ),
                const SizedBox(height: AppSpace.md),
                TextField(
                  controller: urlCtrl,
                  style: const TextStyle(color: AppColors.text),
                  decoration: InputDecoration(
                    labelText: L10n.t.playlistUrl,
                    labelStyle: const TextStyle(color: AppColors.textSecondary),
                    hintText: L10n.t.playlistUrlHint,
                    hintStyle: const TextStyle(color: AppColors.textMuted),
                    errorText: urlError ? L10n.t.invalidUrl : null,
                  ),
                ),
                TextField(
                  controller: uaCtrl,
                  decoration: InputDecoration(labelText: L10n.t.uaOptional),
                ),
              ],
            ),
            actions: [
              TextButton(
                child: Text(
                  L10n.t.cancel,
                  style: const TextStyle(color: AppColors.textSecondary),
                ),
                onPressed: () => Navigator.pop(ctx),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                ),
                child: Text(
                  L10n.t.add,
                  style: TextStyle(color: AppColors.onAccent),
                ),
                onPressed: () {
                  // Пустой или не-http(s) адрес не добавляем — но показываем причину
                  final ok = _isValidHttpUrl(urlCtrl.text);
                  setDialogState(() => urlError = !ok);
                  if (!ok) return;
                  widget.vm.addCustomSource(
                    nameCtrl.text,
                    urlCtrl.text,
                    userAgent: uaCtrl.text,
                  );
                  Navigator.pop(ctx);
                  setState(() {});
                },
              ),
            ],
          ),
        );
      },
    );
    nameCtrl.dispose();
    urlCtrl.dispose();
    uaCtrl.dispose();
  }

  bool _isValidHttpUrl(String value) {
    final uri = Uri.tryParse(value.trim());
    if (uri == null) return false;
    // Drag-and-drop кладёт m3u рядом с приложением и добавляет file:// —
    // диалог обязан принимать такие же адреса, как и дроп.
    if (uri.scheme == 'file') return uri.path.isNotEmpty;
    return uri.host.isNotEmpty && ['http', 'https'].contains(uri.scheme);
  }

  Future<void> _addGuide() async {
    final url = TextEditingController();
    final name = TextEditingController();
    final source = await showDialog<EpgSourceConfig>(
      context: context,
      builder: (context) {
        var urlError = false;
        return StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: Text(L10n.t.epgSourceTitle),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: name,
                  decoration: InputDecoration(labelText: L10n.t.name),
                ),
                TextField(
                  controller: url,
                  decoration: InputDecoration(
                    labelText: L10n.t.xmltvUrl,
                    errorText: urlError ? L10n.t.invalidUrl : null,
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(L10n.t.cancel),
              ),
              FilledButton(
                onPressed: () {
                  // Раньше некорректный адрес просто молча игнорировался
                  final ok = _isValidHttpUrl(url.text);
                  setDialogState(() => urlError = !ok);
                  if (!ok) return;
                  final uri = Uri.parse(url.text.trim());
                  Navigator.pop(
                    context,
                    EpgSourceConfig(
                      id: 'custom_${DateTime.now().microsecondsSinceEpoch}',
                      name: name.text.trim().isEmpty
                          ? uri.host
                          : name.text.trim(),
                      url: uri.toString(),
                    ),
                  );
                },
                child: Text(L10n.t.add),
              ),
            ],
          ),
        );
      },
    );
    url.dispose();
    name.dispose();
    if (!mounted || source == null) return;
    final settings = widget.vm.settings;
    setState(() => settings.epgSources = [...settings.epgSources, source]);
    await widget.vm.refreshEpg();
  }

  Widget _buildAppearance() {
    final current = AppThemeController.current.id;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          L10n.t.appearanceSection,
          style: TextStyle(
            fontSize: AppType.subtitle,
            fontWeight: FontWeight.bold,
            color: AppColors.accentLight,
          ),
        ),
        const SizedBox(height: AppSpace.xs),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(L10n.t.browseHomeStartup),
          value: widget.vm.settings.showHomeAtStartup,
          onChanged: (value) =>
              setState(() => widget.vm.settings.showHomeAtStartup = value),
        ),
        Text(
          L10n.t.appearanceSubtitle,
          style: const TextStyle(
            fontSize: AppType.caption,
            color: AppColors.textMuted,
          ),
        ),
        const SizedBox(height: AppSpace.md),
        Wrap(
          spacing: AppSpace.sm,
          runSpacing: AppSpace.sm,
          children: [
            for (final option in accentOptions)
              InkWell(
                borderRadius: BorderRadius.circular(AppRadius.control),
                onTap: () {
                  widget.vm.setAccent(option.id);
                  setState(() {});
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpace.md,
                    vertical: AppSpace.sm,
                  ),
                  decoration: BoxDecoration(
                    color: option.id == current
                        ? AppColors.accent.withValues(alpha: 0.14)
                        : AppColors.surface,
                    borderRadius: BorderRadius.circular(AppRadius.control),
                    border: Border.all(
                      color: option.id == current
                          ? AppColors.accent
                          : AppColors.border,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 18,
                        height: 18,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                            colors: [option.accent, option.gradientEnd],
                          ),
                        ),
                      ),
                      const SizedBox(width: AppSpace.sm),
                      Text(
                        _accentLabel(option.id),
                        style: TextStyle(
                          fontSize: AppType.caption,
                          color: option.id == current
                              ? AppColors.text
                              : AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ],
    );
  }

  String _accentLabel(String id) {
    return switch (id) {
      'orange' => L10n.t.accentOrange,
      'blue' => L10n.t.accentBlue,
      'teal' => L10n.t.accentTeal,
      'pink' => L10n.t.accentPink,
      'red' => L10n.t.accentRed,
      _ => L10n.t.accentViolet,
    };
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.vm.settings;

    return Center(
      child: Container(
        width: 680,
        height: 600,
        padding: const EdgeInsets.all(AppSpace.xl),
        decoration: BoxDecoration(
          color: AppColors.overlayBackground,
          borderRadius: BorderRadius.circular(AppRadius.card),
          border: Border.all(color: AppColors.border),
          boxShadow: const [
            BoxShadow(
              color: Colors.black54,
              blurRadius: 28,
              offset: Offset(0, 12),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.settings,
                      color: AppColors.accentLight,
                      size: 24,
                    ),
                    const SizedBox(width: AppSpace.md),
                    Text(
                      L10n.t.desktopSettingsTitle,
                      style: const TextStyle(
                        fontSize: AppType.title,
                        fontWeight: FontWeight.bold,
                        color: AppColors.text,
                      ),
                    ),
                  ],
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: AppColors.textSecondary),
                  onPressed: () => widget.vm.closeOverlay(),
                ),
              ],
            ),
            const Divider(color: AppColors.border, height: 20),
            Expanded(
              child: ListView(
                children: [
                  _buildAppearance(),
                  const Divider(color: AppColors.border, height: 28),
                  // --- СЕКЦИЯ: ИСТОЧНИКИ ПЛЕЙЛИСТОВ ---
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            L10n.t.sourcesSection,
                            style: TextStyle(
                              fontSize: AppType.subtitle,
                              fontWeight: FontWeight.bold,
                              color: AppColors.accentLight,
                            ),
                          ),
                          const SizedBox(height: AppSpace.xs),
                          Text(
                            L10n.t.sourcesSubtitle,
                            style: const TextStyle(
                              fontSize: AppType.caption,
                              color: AppColors.textMuted,
                            ),
                          ),
                        ],
                      ),
                      OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.accentLight,
                          side: BorderSide(color: AppColors.accent),
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpace.md,
                            vertical: AppSpace.sm,
                          ),
                        ),
                        icon: const Icon(Icons.add, size: 16),
                        label: Text(
                          L10n.t.add,
                          style: const TextStyle(fontSize: AppType.caption),
                        ),
                        onPressed: _showAddSourceDialog,
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpace.sm),
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(AppRadius.control),
                      border: Border.all(color: AppColors.border),
                    ),
                    child: Column(
                      children: s.sources.map((src) {
                        final status = widget.vm.playlistRepo.sourceStatus(
                          src.id,
                        );
                        final lastSuccess = status?.lastSuccess?.toLocal();
                        final date = MaterialLocalizations.of(context);
                        return ListTile(
                          dense: true,
                          title: Row(
                            children: [
                              Text(
                                src.name,
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              if (src.builtIn)
                                Container(
                                  margin: const EdgeInsets.only(
                                    left: AppSpace.sm,
                                  ),
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: AppSpace.sm,
                                    vertical: 1,
                                  ),
                                  decoration: BoxDecoration(
                                    color: AppColors.surfaceLight,
                                    borderRadius: BorderRadius.circular(
                                      AppRadius.chip,
                                    ),
                                  ),
                                  child: Text(
                                    L10n.t.builtIn,
                                    style: const TextStyle(
                                      fontSize: AppType.caption,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                          subtitle: Text(
                            [
                              if (src.enabled && status?.failed == true)
                                L10n.t.updateError,
                              if (lastSuccess != null)
                                L10n.t.lastLoad(
                                  '${date.formatShortDate(lastSuccess)} '
                                  '${date.formatTimeOfDay(TimeOfDay.fromDateTime(lastSuccess))}',
                                )
                              else if (src.enabled && status?.failed == true)
                                L10n.t.noLastLoad,
                              if (src.urls.isNotEmpty) src.urls.first,
                            ].join('\n'),
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: src.enabled && status?.failed == true
                                  ? AppColors.red
                                  : AppColors.textMuted,
                              fontSize: AppType.caption,
                            ),
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Switch(
                                value: src.enabled,
                                activeThumbColor: AppColors.accent,
                                onChanged: (val) {
                                  widget.vm.toggleSource(src.id, val);
                                  setState(() {});
                                },
                              ),
                              if (!src.builtIn)
                                IconButton(
                                  icon: const Icon(
                                    Icons.delete_outline,
                                    color: AppColors.red,
                                    size: 20,
                                  ),
                                  onPressed: () {
                                    widget.vm.removeCustomSource(src.id);
                                    setState(() {});
                                  },
                                ),
                            ],
                          ),
                        );
                      }).toList(),
                    ),
                  ),

                  const SizedBox(height: AppSpace.lg),
                  const Divider(color: AppColors.border),

                  // --- СЕКЦИЯ: УПРАВЛЕНИЕ ---
                  Text(
                    L10n.t.controlsSection,
                    style: TextStyle(
                      fontSize: AppType.body,
                      fontWeight: FontWeight.bold,
                      color: AppColors.accentLight,
                    ),
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.reverseZapTitle),
                    subtitle: Text(L10n.t.reverseZapSub),
                    value: s.reverseZap,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.reverseZap = v);
                      widget.vm.refresh();
                    },
                  ),

                  const Divider(color: AppColors.border),

                  // --- СЕКЦИЯ: ФИЛЬТРЫ ---
                  Text(
                    L10n.t.filtersSection,
                    style: TextStyle(
                      fontSize: AppType.body,
                      fontWeight: FontWeight.bold,
                      color: AppColors.accentLight,
                    ),
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.hideAdult),
                    subtitle: Text(L10n.t.hideAdultSub),
                    value: s.hideAdult,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.hideAdult = v);
                      widget.vm.reapplyArrangerOptions();
                    },
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.preferMax),
                    subtitle: Text(L10n.t.preferMaxSub),
                    value: s.preferMaxQuality,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.preferMaxQuality = v);
                      widget.vm.reapplyArrangerOptions();
                    },
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.hideSd),
                    subtitle: Text(L10n.t.hideSdSub),
                    value: s.hideSdDuplicates,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.hideSdDuplicates = v);
                      widget.vm.reapplyArrangerOptions();
                    },
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.hideTz),
                    subtitle: Text(L10n.t.hideTzSub),
                    value: s.hideTimezones,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.hideTimezones = v);
                      widget.vm.reapplyArrangerOptions();
                    },
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.mergeOtt),
                    subtitle: Text(L10n.t.mergeOttSub),
                    value: s.mergeOtt,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.mergeOtt = v);
                      widget.vm.reapplyArrangerOptions();
                    },
                  ),
                  SwitchListTile(
                    title: Text(L10n.t.lcn),
                    subtitle: Text(L10n.t.lcnSub),
                    value: s.standardOrder,
                    activeThumbColor: AppColors.accent,
                    onChanged: (v) {
                      setState(() => s.standardOrder = v);
                      widget.vm.reapplyArrangerOptions();
                    },
                  ),

                  const Divider(color: AppColors.border),

                  // --- СЕКЦИЯ: СООТНОШЕНИЕ СТОРОН ---
                  Text(
                    L10n.t.aspectSection,
                    style: TextStyle(
                      fontSize: AppType.body,
                      fontWeight: FontWeight.bold,
                      color: AppColors.accentLight,
                    ),
                  ),
                  const SizedBox(height: AppSpace.sm),
                  Wrap(
                    spacing: 8,
                    children: [null, '16:9', '4:3', '16:10', '21:9'].map((
                      ratio,
                    ) {
                      final isSel = s.aspectRatio == ratio;
                      return ChoiceChip(
                        label: Text(
                          ratio == null
                              ? L10n.t.original
                              : (ratio == '16:10'
                                    ? L10n.t.aspect1610
                                    : (ratio == '21:9'
                                          ? L10n.t.aspect219
                                          : ratio)),
                        ),
                        selected: isSel,
                        selectedColor: AppColors.accent,
                        // null — исходный формат; значение не содержит локализованных строк
                        onSelected: (_) => widget.vm.setAspectRatio(ratio),
                      );
                    }).toList(),
                  ),

                  const SizedBox(height: AppSpace.xl),
                  const Divider(color: AppColors.border),

                  ListTile(
                    title: Text(L10n.t.buffer),
                    subtitle: Text(L10n.t.bufferSub),
                    trailing: DropdownButton<int>(
                      value: s.streamCacheSeconds,
                      items: [
                        for (final seconds in [4, 8, 15, 20, 30])
                          DropdownMenuItem(
                            value: seconds,
                            child: Text(L10n.t.secondsShort(seconds)),
                          ),
                      ],
                      onChanged: (seconds) {
                        if (seconds != null) {
                          setState(() => s.streamCacheSeconds = seconds);
                        }
                      },
                    ),
                  ),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          L10n.t.epgSection,
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                      ),
                      TextButton.icon(
                        onPressed: _addGuide,
                        icon: const Icon(Icons.add),
                        label: Text(L10n.t.xmltvSource),
                      ),
                    ],
                  ),
                  for (final source in s.epgSources)
                    Row(
                      children: [
                        Expanded(
                          child: SwitchListTile(
                            title: Text(_epgSourceLabel(source)),
                            value: source.enabled,
                            onChanged: (enabled) => setState(() {
                              s.epgSources = [
                                for (final item in s.epgSources)
                                  if (item.id == source.id)
                                    EpgSourceConfig(
                                      id: item.id,
                                      name: item.name,
                                      url: item.url,
                                      enabled: enabled,
                                    )
                                  else
                                    item,
                              ];
                            }),
                          ),
                        ),
                        if (source.id.startsWith('custom_'))
                          IconButton(
                            tooltip: L10n.t.deleteEpgTooltip,
                            icon: const Icon(Icons.delete_outline),
                            onPressed: () => setState(() {
                              s.epgSources = s.epgSources
                                  .where((item) => item.id != source.id)
                                  .toList();
                            }),
                          ),
                      ],
                    ),
                  ListTile(
                    title: Text(L10n.t.autoEpg),
                    trailing: DropdownButton<int>(
                      value: s.epgRefreshHours,
                      items: [
                        for (final hours in [6, 12, 24, 48])
                          DropdownMenuItem(
                            value: hours,
                            child: Text(L10n.t.everyHours(hours)),
                          ),
                      ],
                      onChanged: (hours) {
                        if (hours != null) {
                          setState(() => s.epgRefreshHours = hours);
                        }
                      },
                    ),
                  ),

                  // Кнопки обновления
                  Wrap(
                    spacing: 12,
                    runSpacing: 8,
                    children: [
                      ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.accent,
                          foregroundColor: AppColors.onAccent,
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpace.lg,
                            vertical: AppSpace.md,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              AppRadius.control,
                            ),
                          ),
                        ),
                        icon: const Icon(Icons.refresh, size: 18),
                        label: Text(L10n.t.applyPlaylists),
                        onPressed: widget.vm.playlistRepo.isLoading
                            ? null
                            : () async {
                                final refresh = widget.vm.refreshPlaylists();
                                setState(() {});
                                await refresh;
                                if (mounted) setState(() {});
                              },
                      ),
                      OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.text,
                          side: const BorderSide(color: AppColors.border),
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpace.lg,
                            vertical: AppSpace.md,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              AppRadius.control,
                            ),
                          ),
                        ),
                        icon: const Icon(Icons.tv, size: 18),
                        label: Text(L10n.t.refreshEpg),
                        onPressed: () {
                          widget.vm.refreshEpg();
                          widget.vm.closeOverlay();
                        },
                      ),
                    ],
                  ),
                  const Divider(color: AppColors.border, height: 28),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(L10n.t.checkUpdates),
                    subtitle: Text(
                      L10n.t.checkUpdatesDesc,
                      style: const TextStyle(
                        color: AppColors.textMuted,
                        fontSize: AppType.caption,
                      ),
                    ),
                    trailing: OutlinedButton.icon(
                      onPressed: widget.vm.updateBusy == null
                          ? () => widget.vm.checkForUpdates(manual: true)
                          : null,
                      icon: const Icon(Icons.system_update, size: 18),
                      label: Text(L10n.t.checkUpdates),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
