import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../../data/services/stream_checker_service.dart';
import '../../data/models/channel.dart';
import '../../data/services/stream_resolver.dart';
import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';
import 'channel_logo.dart';
import 'guide_timeline.dart';

String _groupLabel(String group) => switch (group) {
  FilterKeys.all => L10n.t.all,
  FilterKeys.favourites => L10n.t.favourites,
  FilterKeys.recent => L10n.t.recent,
  // «Общие» — имя группы из данных плейлиста, а не значение фильтра
  'Общие' => L10n.t.groupGeneric,
  'General' => L10n.t.groupGeneric,
  _ => group,
};

/// Русская плюрализация выполняется ICU-правилами в ARB.
String _channelsCount(int n) => L10n.t.channelsCount(n);

class PlaylistOverlay extends StatefulWidget {
  final PlayerViewModel vm;

  const PlaylistOverlay({super.key, required this.vm});

  @override
  State<PlaylistOverlay> createState() => _PlaylistOverlayState();
}

class _PlaylistOverlayState extends State<PlaylistOverlay> {
  final TextEditingController _searchCtrl = TextEditingController();
  final ScrollController _scrollCtrl = ScrollController();
  final ScrollController _scheduleScrollCtrl = ScrollController();
  final ScrollController _groupScrollCtrl = ScrollController();

  Programme? _selectedProgramme;
  bool _showDetails = false;
  Timer? _searchDebounce;

  void _openChannel(Channel channel, [Programme? programme]) {
    widget.vm.setPreviewChannel(channel);
    setState(() {
      _selectedProgramme = programme;
      _showDetails = true;
    });
  }

  @override
  void initState() {
    super.initState();
    _searchCtrl.text = widget.vm.searchQuery;
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchCtrl.dispose();
    _scrollCtrl.dispose();
    _scheduleScrollCtrl.dispose();
    _groupScrollCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final channels = widget.vm.filteredChannels;
    final activeKey = widget.vm.playbackInfo.channel?.key;
    final preview = widget.vm.previewChannel ?? channels.firstOrNull;

    return Scaffold(
      backgroundColor: Colors.black54,
      body: Center(
        child: Container(
          width: MediaQuery.of(context).size.width * 0.94,
          height: MediaQuery.of(context).size.height * 0.90,
          constraints: const BoxConstraints(maxWidth: 1300, maxHeight: 850),
          decoration: BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.circular(AppRadius.panel),
            border: Border.all(color: AppColors.border, width: 1.2),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.7),
                blurRadius: 24,
                spreadRadius: 4,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Column(
            children: [
              // Верхний заголовок окна
              _buildWindowTitleBar(context, channels.length),

              const Divider(color: AppColors.border, height: 1),

              Expanded(
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final wide = constraints.maxWidth >= 1050;
                    return Row(
                      children: [
                        if (wide)
                          SizedBox(width: 176, child: _buildGroupRail()),
                        // Левая колонка: список каналов с поиском и категориями (~440px)
                        SizedBox(
                          width: constraints.maxWidth * (wide ? .34 : .46),
                          child: Column(
                            children: [
                              _buildSearchField(),
                              if (widget.vm.sourceNames.length > 2)
                                _buildSourceFilter(),
                              if (!wide) _buildGroupTabs(),
                              const Divider(color: AppColors.border, height: 1),
                              Expanded(
                                // Сброс скролла при смене выборки, а не сохранение протухшего offset.
                                key: ValueKey(
                                  '${widget.vm.selectedGroup}|${widget.vm.selectedSource}|${widget.vm.searchQuery}',
                                ),
                                child: _buildChannelList(
                                  channels,
                                  activeKey,
                                  preview,
                                ),
                              ),
                            ],
                          ),
                        ),

                        const VerticalDivider(
                          color: AppColors.border,
                          width: 1,
                        ),

                        // Правая колонка: подробный ТВ-Гид, описание передачи и источники
                        Expanded(
                          child: widget.vm.nowOnly && !_showDetails
                              ? GuideTimeline(
                                  vm: widget.vm,
                                  channels: channels,
                                  onProgramme: _openChannel,
                                )
                              : preview == null
                              ? Center(
                                  child: Text(
                                    L10n.t.selectChannelForGuide,
                                    style: const TextStyle(
                                      color: AppColors.textMuted,
                                    ),
                                  ),
                                )
                              : _buildEpgDetailPane(preview, activeKey),
                        ),
                      ],
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildWindowTitleBar(BuildContext context, int totalCount) {
    return Container(
      height: 48,
      padding: const EdgeInsets.symmetric(horizontal: AppSpace.lg),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.only(
          topLeft: Radius.circular(AppRadius.panel),
          topRight: Radius.circular(AppRadius.panel),
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.live_tv, size: 20, color: AppColors.accentLight),
          const SizedBox(width: AppSpace.md),
          Flexible(
            child: Text(
              L10n.t.guideTitle,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: AppType.subtitle,
                fontWeight: FontWeight.bold,
                color: AppColors.text,
              ),
            ),
          ),
          const SizedBox(width: AppSpace.md),
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpace.sm,
              vertical: AppSpace.xs,
            ),
            decoration: BoxDecoration(
              color: AppColors.surfaceLight,
              borderRadius: BorderRadius.circular(AppRadius.control),
              border: Border.all(color: AppColors.border),
            ),
            child: Text(
              _channelsCount(totalCount),
              style: const TextStyle(
                fontSize: AppType.caption,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          const Spacer(),
          IconButton(
            tooltip: L10n.t.browseHome,
            onPressed: () => widget.vm.setOverlay(OverlayType.home),
            icon: const Icon(Icons.home_outlined),
          ),
          FilterChip(
            label: Text(L10n.t.onAirNow),
            selected: widget.vm.nowOnly,
            onSelected: (_) {
              setState(() => _showDetails = false);
              widget.vm.toggleNow();
            },
          ),
          if (widget.vm.nowOnly && _showDetails)
            IconButton(
              tooltip: L10n.t.toGuideGrid,
              icon: const Icon(Icons.view_timeline_outlined),
              onPressed: () => setState(() => _showDetails = false),
            ),
          TextButton.icon(
            style: TextButton.styleFrom(
              foregroundColor: AppColors.textSecondary,
            ),
            icon: const Icon(Icons.close, size: 18),
            label: Text(
              L10n.t.closeEsc,
              style: const TextStyle(fontSize: AppType.body),
            ),
            onPressed: () => widget.vm.closeOverlay(),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchField() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpace.lg,
        AppSpace.md,
        AppSpace.lg,
        AppSpace.sm,
      ),
      child: TextField(
        controller: _searchCtrl,
        onChanged: _search,
        style: const TextStyle(color: AppColors.text, fontSize: AppType.body),
        decoration: InputDecoration(
          hintText: L10n.t.searchHint,
          hintStyle: const TextStyle(
            color: AppColors.textMuted,
            fontSize: AppType.body,
          ),
          prefixIcon: const Icon(
            Icons.search,
            color: AppColors.textSecondary,
            size: 18,
          ),
          suffixIcon: _searchCtrl.text.isNotEmpty
              ? IconButton(
                  icon: const Icon(
                    Icons.clear,
                    color: AppColors.textSecondary,
                    size: 16,
                  ),
                  onPressed: () {
                    _searchCtrl.clear();
                    _search('');
                  },
                )
              : null,
          filled: true,
          fillColor: AppColors.surface,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(
            vertical: AppSpace.sm,
            horizontal: AppSpace.md,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(AppRadius.control),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    );
  }

  void _search(String query) {
    _selectedProgramme = null;
    // Поиск по EPG идёт по всем каналам — дебаунсим, иначе каждый кейстрок
    // пересчитывает O(N×M) совпадений.
    _searchDebounce?.cancel();
    _searchDebounce = Timer(const Duration(milliseconds: 250), () {
      if (!mounted) return;
      widget.vm.setSearchQuery(query.trim());
      widget.vm.setPreviewChannel(widget.vm.filteredChannels.firstOrNull);
    });
  }

  Widget _buildSourceFilter() {
    return SizedBox(
      height: 28,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: AppSpace.lg),
        scrollDirection: Axis.horizontal,
        itemCount: widget.vm.sourceNames.length,
        separatorBuilder: (context, index) =>
            const SizedBox(width: AppSpace.sm),
        itemBuilder: (context, i) {
          final src = widget.vm.sourceNames[i];
          final isSel = widget.vm.selectedSource == src;
          return InkWell(
            onTap: () => widget.vm.selectSource(src),
            borderRadius: BorderRadius.circular(AppRadius.panel),
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpace.md,
                vertical: AppSpace.xs,
              ),
              decoration: BoxDecoration(
                color: isSel
                    ? AppColors.accentLight.withValues(alpha: 0.25)
                    : AppColors.surface,
                borderRadius: BorderRadius.circular(AppRadius.panel),
                border: Border.all(
                  color: isSel ? AppColors.accentLight : AppColors.border,
                  width: isSel ? 1.2 : 0.8,
                ),
              ),
              alignment: Alignment.center,
              child: Text(
                src == FilterKeys.allSources ? L10n.t.allSources : src,
                style: TextStyle(
                  color: isSel
                      ? AppColors.accentLight
                      : AppColors.textSecondary,
                  fontSize: AppType.caption,
                  fontWeight: isSel ? FontWeight.bold : FontWeight.normal,
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildGroupRail() {
    return Material(
      color: AppColors.surface,
      child: ListView(
        padding: const EdgeInsets.all(AppSpace.sm),
        children: [
          ListTile(
            leading: const Icon(Icons.home_outlined),
            title: Text(L10n.t.browseHome),
            onTap: () => widget.vm.setOverlay(OverlayType.home),
          ),
          for (final group in widget.vm.groups)
            ListTile(
              dense: true,
              selected: widget.vm.selectedGroup == group,
              title: Text(
                _groupLabel(group),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              onTap: () {
                setState(() => _selectedProgramme = null);
                widget.vm.selectGroup(group);
              },
            ),
        ],
      ),
    );
  }

  Widget _buildGroupTabs() {
    final groups = widget.vm.groups;
    return Padding(
      padding: const EdgeInsets.only(top: AppSpace.sm, bottom: AppSpace.xs),
      child: SizedBox(
        height: 40,
        child: Scrollbar(
          controller: _groupScrollCtrl,
          thumbVisibility: true,
          interactive: true,
          scrollbarOrientation: ScrollbarOrientation.bottom,
          child: Listener(
            behavior: HitTestBehavior.opaque,
            onPointerSignal: _scrollGroupsWithWheel,
            child: _buildGroupList(groups),
          ),
        ),
      ),
    );
  }

  void _scrollGroupsWithWheel(PointerSignalEvent event) {
    if (event is! PointerScrollEvent ||
        event.scrollDelta.dx != 0 ||
        event.scrollDelta.dy == 0 ||
        !_groupScrollCtrl.hasClients) {
      return;
    }
    final position = _groupScrollCtrl.position;
    final target = (position.pixels + event.scrollDelta.dy).clamp(
      position.minScrollExtent,
      position.maxScrollExtent,
    );
    if (target == position.pixels) return;
    GestureBinding.instance.pointerSignalResolver.register(
      event,
      (_) => position.pointerScroll(event.scrollDelta.dy),
    );
  }

  Widget _buildGroupList(List<String> groups) {
    return ListView.separated(
      controller: _groupScrollCtrl,
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(
        AppSpace.lg,
        0,
        AppSpace.lg,
        AppSpace.sm,
      ),
      itemCount: groups.length,
      separatorBuilder: (context, index) => const SizedBox(width: AppSpace.sm),
      itemBuilder: (context, i) {
        final grp = groups[i];
        final isSelected = widget.vm.selectedGroup == grp;
        return InkWell(
          onTap: () => widget.vm.selectGroup(grp),
          borderRadius: BorderRadius.circular(AppRadius.control),
          child: Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpace.md,
              vertical: AppSpace.sm,
            ),
            decoration: BoxDecoration(
              color: isSelected ? AppColors.accent : AppColors.surface,
              borderRadius: BorderRadius.circular(AppRadius.control),
            ),
            alignment: Alignment.center,
            child: Text(
              _groupLabel(grp),
              style: TextStyle(
                color: isSelected
                    ? AppColors.onAccent
                    : AppColors.textSecondary,
                fontSize: AppType.caption,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildChannelList(
    List<Channel> channels,
    String? activeKey,
    Channel? preview,
  ) {
    if (channels.isEmpty) {
      return Center(
        child: Text(
          L10n.t.noChannels,
          style: const TextStyle(color: AppColors.textMuted),
        ),
      );
    }

    return ListView.builder(
      key: PageStorageKey((
        widget.vm.selectedGroup,
        widget.vm.selectedSource,
        widget.vm.searchQuery,
      )),
      controller: _scrollCtrl,
      itemCount: channels.length,
      itemBuilder: (context, i) {
        final ch = channels[i];
        final isPlaying = ch.key == activeKey;
        final isSelected = ch.key == preview?.key;
        final isFav = widget.vm.favorites.contains(ch.key);
        final prog = widget.vm.epgService.getCurrentProgramme(ch);

        return Material(
          color: isSelected
              ? AppColors.surfaceHover
              : (isPlaying
                    ? AppColors.surfaceLight.withValues(alpha: 0.5)
                    : Colors.transparent),
          child: InkWell(
            autofocus: isSelected && !widget.vm.nowOnly,
            onFocusChange: (focused) {
              if (focused && widget.vm.previewChannel?.key != ch.key) {
                _openChannel(ch);
              }
            },
            onTap: () => _openChannel(ch),
            onDoubleTap: () {
              widget.vm.play(ch);
            },
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpace.md,
                vertical: AppSpace.sm,
              ),
              decoration: BoxDecoration(
                border: Border(
                  left: BorderSide(
                    color: isSelected
                        ? AppColors.accentLight
                        : (isPlaying ? AppColors.green : Colors.transparent),
                    width: 3.5,
                  ),
                  bottom: const BorderSide(color: AppColors.border, width: 0.5),
                ),
              ),
              child: Row(
                children: [
                  // Звездочка (Избранное)
                  InkWell(
                    onTap: () => widget.vm.toggleFavorite(ch),
                    child: Padding(
                      padding: const EdgeInsets.all(AppSpace.xs),
                      child: Icon(
                        isFav ? Icons.star : Icons.star_border,
                        color: isFav
                            ? AppColors.accentLight
                            : AppColors.textMuted,
                        size: 18,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpace.xs),

                  // Номер канала
                  SizedBox(
                    width: 30,
                    child: Text(
                      '${ch.number}',
                      style: TextStyle(
                        color: isPlaying
                            ? AppColors.green
                            : AppColors.textMuted,
                        fontWeight: FontWeight.bold,
                        fontSize: AppType.body,
                      ),
                    ),
                  ),

                  // Логотип канала
                  Padding(
                    padding: const EdgeInsets.only(right: AppSpace.md),
                    child: ChannelLogo(
                      ch,
                      width: 34,
                      height: 24,
                      placeholder: const SizedBox(width: 34),
                    ),
                  ),

                  // Название и текущая передача
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Flexible(
                              child: Text(
                                ch.name,
                                style: TextStyle(
                                  color: isPlaying
                                      ? AppColors.accentLight
                                      : AppColors.text,
                                  fontWeight: isPlaying || isSelected
                                      ? FontWeight.bold
                                      : FontWeight.w500,
                                  fontSize: AppType.body,
                                ),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            if (isPlaying) ...[
                              const SizedBox(width: AppSpace.sm),
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: AppSpace.xs,
                                  vertical: 1,
                                ),
                                decoration: BoxDecoration(
                                  color: AppColors.green.withValues(alpha: 0.2),
                                  borderRadius: BorderRadius.circular(
                                    AppRadius.chip,
                                  ),
                                  border: Border.all(
                                    color: AppColors.green,
                                    width: 0.8,
                                  ),
                                ),
                                child: Text(
                                  L10n.t.liveBadge,
                                  style: const TextStyle(
                                    color: AppColors.green,
                                    fontSize: AppType.caption,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                        if (prog != null) ...[
                          const SizedBox(height: AppSpace.xs),
                          Text(
                            prog.title,
                            style: const TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: AppType.caption,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ],
                    ),
                  ),

                  // Источники
                  if (ch.variants.length > 1)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpace.sm,
                        vertical: AppSpace.xs,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.circular(AppRadius.chip),
                        border: Border.all(color: AppColors.border, width: 0.8),
                      ),
                      child: Text(
                        L10n.t.sourcesShort(ch.variants.length),
                        style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: AppType.caption,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildEpgDetailPane(Channel channel, String? activeKey) {
    final isPlaying = channel.key == activeKey;
    final currentProg = widget.vm.epgService.getCurrentProgramme(channel);
    final schedule = widget.vm.epgService.getSchedule(channel);
    final query = widget.vm.searchQuery.trim().toLowerCase();
    final displayedProg =
        _selectedProgramme ??
        (query.isEmpty
            ? null
            : schedule
                  .where((p) => p.title.toLowerCase().contains(query))
                  .firstOrNull) ??
        currentProg;
    final archive = widget.vm.playbackInfo.archive;
    final canStart =
        displayedProg != null &&
        channel.variants.any(
          (v) =>
              StreamResolver.catchupUrl(
                v,
                displayedProg.start,
                displayedProg.stop,
              ) !=
              null,
        );

    return Column(
      children: [
        // Шапка выбранного канала
        Container(
          padding: const EdgeInsets.all(AppSpace.lg),
          color: AppColors.surface,
          child: Row(
            children: [
              ChannelLogo(channel, width: 56, height: 40),
              const SizedBox(width: AppSpace.lg),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      channel.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: AppType.title,
                        fontWeight: FontWeight.bold,
                        color: AppColors.text,
                      ),
                    ),
                    const SizedBox(height: AppSpace.xs),
                    Text(
                      L10n.t.sourcesAvailable(channel.variants.length),
                      style: const TextStyle(
                        color: AppColors.textMuted,
                        fontSize: AppType.caption,
                      ),
                    ),
                  ],
                ),
              ),
              ElevatedButton.icon(
                style: ElevatedButton.styleFrom(
                  backgroundColor: isPlaying
                      ? AppColors.green
                      : AppColors.accent,
                  foregroundColor: AppColors.onAccent,
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpace.xl,
                    vertical: AppSpace.md,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppRadius.control),
                  ),
                ),
                icon: Icon(isPlaying ? Icons.play_arrow : Icons.tv, size: 18),
                label: Text(
                  isPlaying && archive == null
                      ? L10n.t.onAir
                      : L10n.t.backToLive,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: AppType.body,
                  ),
                ),
                onPressed: () {
                  widget.vm.play(channel);
                  widget.vm.closeOverlay();
                },
              ),
            ],
          ),
        ),

        const Divider(color: AppColors.border, height: 1),

        Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            TextButton.icon(
              icon: const Icon(Icons.schedule, size: 16),
              label: Text(L10n.t.now),
              onPressed: currentProg == null
                  ? null
                  : () {
                      setState(() => _selectedProgramme = currentProg);
                      if (_scheduleScrollCtrl.hasClients) {
                        _scheduleScrollCtrl.jumpTo(
                          (schedule.indexOf(currentProg) * 38.0).clamp(
                            0.0,
                            _scheduleScrollCtrl.position.maxScrollExtent,
                          ),
                        );
                      }
                    },
            ),
            if (canStart)
              TextButton.icon(
                icon: const Icon(Icons.replay, size: 16),
                label: Text(L10n.t.fromStart),
                onPressed: () {
                  widget.vm.play(channel, archive: displayedProg);
                  widget.vm.closeOverlay();
                },
              ),
            const SizedBox(width: AppSpace.md),
          ],
        ),

        // Список источников канала с индикатором работоспособности
        if (channel.variants.isNotEmpty)
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpace.lg,
              vertical: AppSpace.md,
            ),
            color: AppColors.surfaceLight.withValues(alpha: 0.3),
            child: Row(
              children: [
                Text(
                  L10n.t.sourcesLabel,
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: AppType.caption,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(width: AppSpace.sm),
                Expanded(
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    children: List.generate(channel.variants.length, (i) {
                      final v = channel.variants[i];
                      final playback = widget.vm.playbackInfo;
                      final isCurrent =
                          isPlaying &&
                          playback
                                  .channel
                                  ?.variants[playback.variantIndex]
                                  .url ==
                              v.url;
                      final (dotColor, statusText) = switch (widget
                          .vm
                          .streamChecker
                          .status(v.url)) {
                        StreamHealth.playing => (
                          AppColors.green,
                          L10n.t.streamPlayingConfirmed,
                        ),
                        StreamHealth.unavailable => (
                          AppColors.red,
                          L10n.t.streamUnavailable,
                        ),
                        StreamHealth.reachable => (
                          AppColors.textMuted,
                          L10n.t.streamAvailable,
                        ),
                        null => (AppColors.textMuted, L10n.t.streamNotChecked),
                      };

                      return InkWell(
                        onTap: () {
                          widget.vm.play(
                            channel,
                            variantIndex: i,
                            archive: isPlaying ? archive : null,
                          );
                        },
                        borderRadius: BorderRadius.circular(AppRadius.control),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpace.sm,
                            vertical: AppSpace.xs,
                          ),
                          decoration: BoxDecoration(
                            color: isCurrent
                                ? AppColors.accent.withValues(alpha: 0.25)
                                : AppColors.surface,
                            borderRadius: BorderRadius.circular(
                              AppRadius.control,
                            ),
                            border: Border.all(
                              color: isCurrent
                                  ? AppColors.accentLight
                                  : AppColors.border,
                              width: isCurrent ? 1.2 : 0.8,
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 7,
                                height: 7,
                                decoration: BoxDecoration(
                                  color: dotColor,
                                  shape: BoxShape.circle,
                                ),
                              ),
                              const SizedBox(width: AppSpace.sm),
                              Text(
                                '${v.sourceName} ($statusText)',
                                style: TextStyle(
                                  color: isCurrent
                                      ? AppColors.accentLight
                                      : AppColors.text,
                                  fontSize: AppType.caption,
                                  fontWeight: isCurrent
                                      ? FontWeight.bold
                                      : FontWeight.normal,
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    }),
                  ),
                ),
              ],
            ),
          ),

        const Divider(color: AppColors.border, height: 1),

        // Карточка подробного описания выбранной передачи
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(AppSpace.lg),
          color: AppColors.surface.withValues(alpha: 0.4),
          child: displayedProg == null
              ? Text(
                  L10n.t.noProgrammeInfo,
                  style: const TextStyle(
                    color: AppColors.textMuted,
                    fontSize: AppType.body,
                  ),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          '${_formatTime(displayedProg.start)} — ${_formatTime(displayedProg.stop)}',
                          style: TextStyle(
                            color: AppColors.accentLight,
                            fontSize: AppType.body,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        if (displayedProg == currentProg) ...[
                          const SizedBox(width: AppSpace.md),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppSpace.sm,
                              vertical: AppSpace.xs,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.green.withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(
                                AppRadius.chip,
                              ),
                              border: Border.all(
                                color: AppColors.green,
                                width: 0.8,
                              ),
                            ),
                            child: Text(
                              L10n.t.onAirNowUpper,
                              style: const TextStyle(
                                color: AppColors.green,
                                fontSize: AppType.caption,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                        if (displayedProg.category != null) ...[
                          const Spacer(),
                          Text(
                            displayedProg.category!,
                            style: const TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: AppType.caption,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: AppSpace.sm),
                    Text(
                      displayedProg.title,
                      style: const TextStyle(
                        fontSize: AppType.title,
                        fontWeight: FontWeight.bold,
                        color: AppColors.text,
                      ),
                    ),
                    const SizedBox(height: AppSpace.sm),
                    // Описание передачи (desc)
                    Text(
                      (displayedProg.description != null &&
                              displayedProg.description!.isNotEmpty)
                          ? displayedProg.description!
                          : L10n.t.noDescription,
                      style: TextStyle(
                        fontSize: AppType.body,
                        height: 1.45,
                        color:
                            (displayedProg.description != null &&
                                displayedProg.description!.isNotEmpty)
                            ? AppColors.textSecondary
                            : AppColors.textMuted,
                      ),
                      maxLines: 4,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
        ),

        const Divider(color: AppColors.border, height: 1),

        // Список всех программ на сегодня (прокручиваемый телегид)
        Expanded(
          child: schedule.isEmpty
              ? Center(
                  child: Text(
                    L10n.t.guideNotLoaded,
                    style: const TextStyle(color: AppColors.textMuted),
                  ),
                )
              : ListView.builder(
                  controller: _scheduleScrollCtrl,
                  itemExtent: 38,
                  padding: const EdgeInsets.symmetric(vertical: AppSpace.xs),
                  itemCount: schedule.length,
                  itemBuilder: (context, i) {
                    final p = schedule[i];
                    final isLive = p == currentProg;
                    final isSel = p == displayedProg;
                    final canArchive =
                        p.stop.isBefore(DateTime.now()) &&
                        channel.variants.any(
                          (v) =>
                              StreamResolver.catchupUrl(v, p.start, p.stop) !=
                              null,
                        );

                    return Material(
                      color: isSel
                          ? AppColors.surfaceHover
                          : (isLive
                                ? AppColors.surfaceLight.withValues(alpha: 0.5)
                                : Colors.transparent),
                      child: InkWell(
                        onDoubleTap: canArchive
                            ? () {
                                widget.vm.play(channel, archive: p);
                                widget.vm.closeOverlay();
                              }
                            : null,
                        onTap: () {
                          setState(() => _selectedProgramme = p);
                        },
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpace.lg,
                            vertical: AppSpace.sm,
                          ),
                          child: Row(
                            children: [
                              SizedBox(
                                width: 55,
                                child: Text(
                                  _formatTime(p.start),
                                  style: TextStyle(
                                    color: isLive
                                        ? AppColors.green
                                        : AppColors.textMuted,
                                    fontWeight: isLive || isSel
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    fontSize: AppType.body,
                                  ),
                                ),
                              ),
                              const SizedBox(width: AppSpace.sm),
                              Expanded(
                                child: Text(
                                  p.title,
                                  style: TextStyle(
                                    color: isLive
                                        ? AppColors.accentLight
                                        : AppColors.text,
                                    fontWeight: isLive
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    fontSize: AppType.body,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              if (canArchive)
                                Icon(
                                  Icons.history,
                                  size: 15,
                                  color: AppColors.accentLight,
                                ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  String _formatTime(DateTime dt) {
    final h = dt.hour.toString().padLeft(2, '0');
    final m = dt.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }
}
