import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:window_manager/window_manager.dart';

import '../../data/models/channel.dart';
import '../../data/models/settings_model.dart';
import '../../data/repositories/playlist_repository.dart';
import '../../data/repositories/settings_repository.dart';
import '../../data/services/epg_service.dart';
import '../../data/services/player_service.dart';
import '../../data/services/stream_checker_service.dart';
import '../../data/services/stream_resolver.dart';
import '../../data/services/update_service.dart';
import '../../l10n/l10n.dart';
import '../theme/theme.dart';

enum OverlayType { none, home, playlist, settings, stream, stats }

/// Нейтральные ключи фильтров списка. Значения фильтров не зависят от языка
/// интерфейса: виджеты переводят их через L10n при отображении.
abstract final class FilterKeys {
  static const all = 'key:all';
  static const favourites = 'key:favourites';
  static const recent = 'key:recent';
  static const allSources = 'key:all-sources';
}

class PlayerViewModel extends ChangeNotifier {
  final SettingsRepository settings = SettingsRepository();
  final PlaylistRepository playlistRepo = PlaylistRepository();
  final EpgService epgService;
  final PlayerService playerService;
  final StreamCheckerService streamChecker = StreamCheckerService();

  PlayerViewModel({PlayerService? playerService, EpgService? epgService})
    : playerService = playerService ?? PlayerService(),
      epgService = epgService ?? EpgService() {
    if (!this.playerService.isInitialized) this.playerService.initialize();
    _playerSubscription = this.playerService.stateStream.listen(
      _onPlaybackInfo,
    );
  }

  late final StreamSubscription<PlaybackInfo> _playerSubscription;

  void setAccent(String id) {
    settings.accent = id;
    AppThemeController.apply(id);
    notifyListeners();
  }

  OverlayType _overlay = OverlayType.none;
  OverlayType get overlay => _overlay;

  List<Channel> _channels = [];
  List<Channel> get channels => _channels;

  (String, DateTime)? guideCursor;
  Channel? _previewChannel;
  Channel? get previewChannel =>
      _previewChannel ??
      _playbackInfo.channel ??
      (_channels.isNotEmpty ? _channels.first : null);

  void setPreviewChannel(Channel? ch) {
    _previewChannel = ch;
    notifyListeners();
  }

  String _selectedGroup = FilterKeys.all;
  String get selectedGroup => _selectedGroup;

  String _searchQuery = '';
  String get searchQuery => _searchQuery;
  bool _nowOnly = false;
  bool get nowOnly => _nowOnly;

  List<String> _recent = [];
  List<Channel> get recentChannels {
    final byKey = {for (final channel in _channels) channel.key: channel};
    return _recent.map((key) => byKey[key]).nonNulls.toList();
  }

  Channel? get previousChannel => recentChannels
      .where((channel) => channel.key != _playbackInfo.channel?.key)
      .firstOrNull;

  void playPreviousChannel() {
    final channel = previousChannel;
    if (channel != null) play(channel);
  }

  Set<String> _favorites = {};
  Set<String> get favorites => _favorites;

  bool _isInfoBarVisible = false;
  bool get isInfoBarVisible => _isInfoBarVisible;

  String? _toast;
  String? get toast => _toast;

  final UpdateService updateService = UpdateService();
  AppUpdate? _update;
  AppUpdate? get update => _update;
  String? _updateBusy;
  String? get updateBusy => _updateBusy;
  double? _updateProgress;
  double? get updateProgress => _updateProgress;
  bool _updateCheckInFlight = false;

  String _quickDigits = '';
  String get quickDigits => _quickDigits;

  double? _volumeOverlay;
  double? get volumeOverlay => _volumeOverlay;

  bool _isMuted = false;
  bool get isMuted => _isMuted;
  double _prevVolume = 100.0;

  bool _isFullscreen = false;
  bool get isFullscreen => _isFullscreen;
  Rect? _normalWindow;
  bool _changingWindow = false;
  bool get compactMode => _normalWindow != null;

  PlaybackInfo _playbackInfo = const PlaybackInfo();
  PlaybackInfo get playbackInfo => _playbackInfo;

  Programme? get currentProgramme =>
      epgService.getCurrentProgramme(_playbackInfo.channel);
  Programme? get nextProgramme =>
      epgService.getNextProgramme(_playbackInfo.channel);

  Timer? _infoBarTimer;
  Timer? _toastTimer;
  Timer? _quickDigitsTimer;
  Timer? _volumeTimer;
  Timer? _epgRefreshTimer;
  Timer? _zapDebounceTimer;
  Timer? _stallTimer;
  int _attemptId = 0;
  bool _hasPlaybackProgress = false;
  final Set<int> _failedVariantsForCurrent = {};

  void reapplyArrangerOptions() {
    _channels = playlistRepo.arrange(settings.arrangerOptions);
    _previewChannel = null;
    notifyListeners();
  }

  String _selectedSource = FilterKeys.allSources;
  String get selectedSource => _selectedSource;

  List<String> get sourceNames {
    final list = <String>[FilterKeys.allSources];
    for (final s in settings.sources) {
      if (s.enabled) list.add(s.name);
    }
    return list;
  }

  void selectSource(String src) {
    _selectedSource = src;
    _previewChannel = null;
    notifyListeners();
  }

  void toggleSource(String sourceId, bool enabled) {
    settings.sources = settings.sources.map((s) {
      if (s.id == sourceId) {
        return SourceConfig(
          id: s.id,
          name: s.name,
          urls: s.urls,
          kind: s.kind,
          enabled: enabled,
          builtIn: s.builtIn,
          playlistUserAgent: s.playlistUserAgent,
          streamUserAgent: s.streamUserAgent,
          sort: s.sort,
        );
      }
      return s;
    }).toList();
    _previewChannel = null;
    notifyListeners();
  }

  void addCustomSource(String name, String url, {String? userAgent}) {
    addCustomSources([(name, url)], userAgent: userAgent);
  }

  void addCustomSources(List<(String, String)> files, {String? userAgent}) {
    if (files.isEmpty) return;
    final sources = [...settings.sources];
    final stamp = DateTime.now().microsecondsSinceEpoch;
    final ua = userAgent?.trim();
    // Одинаковые отображаемые имена коллизируют в фильтре источников —
    // делаем их уникальными на входе.
    final taken = sources.map((s) => s.name).toSet();
    String uniqueName(String base) {
      var candidate = base;
      var n = 2;
      while (taken.contains(candidate)) {
        candidate = '$base ($n)';
        n++;
      }
      taken.add(candidate);
      return candidate;
    }

    for (final (name, url) in files) {
      if (sources.any((source) => source.urls.contains(url.trim()))) continue;
      final base = name.trim().isEmpty ? L10n.t.customSource : name.trim();
      sources.add(
        SourceConfig(
          id: 'custom_${stamp}_${sources.length}',
          name: uniqueName(base),
          urls: [url.trim()],
          kind: StreamKind.generic,
          enabled: true,
          builtIn: false,
          sort: 20 + sources.length,
          playlistUserAgent: ua?.isNotEmpty == true ? ua : null,
          streamUserAgent: ua?.isNotEmpty == true ? ua : null,
        ),
      );
    }
    settings.sources = sources;
    notifyListeners();
  }

  void removeCustomSource(String sourceId) {
    settings.sources = settings.sources.where((s) => s.id != sourceId).toList();
    notifyListeners();
  }

  List<String> get groups {
    final set = <String>{
      FilterKeys.all,
      FilterKeys.favourites,
      if (_recent.isNotEmpty) FilterKeys.recent,
    };
    for (final c in _channels) {
      if (c.group.isNotEmpty) set.add(c.group);
    }
    return set.toList();
  }

  List<Channel> get filteredChannels {
    var list = _channels;

    if (_selectedSource != FilterKeys.allSources) {
      // Точное совпадение вместо startsWith: префиксы («Wink» vs «Wink TV»)
      // и суффикс « (OTT)» давали чужие каналы.
      final ottSuffix = RegExp(r' \(OTT\)$');
      list = list
          .where(
            (c) => c.variants.any(
              (v) =>
                  v.sourceName.replaceFirst(ottSuffix, '') == _selectedSource,
            ),
          )
          .toList();
    }

    if (_selectedGroup == FilterKeys.favourites) {
      list = list.where((c) => _favorites.contains(c.key)).toList();
    } else if (_selectedGroup == FilterKeys.recent) {
      final visible = list.map((channel) => channel.key).toSet();
      list = recentChannels
          .where((channel) => visible.contains(channel.key))
          .toList();
    } else if (_selectedGroup != FilterKeys.all) {
      list = list.where((c) => c.group == _selectedGroup).toList();
    }

    if (_searchQuery.isNotEmpty) {
      final q = _searchQuery.toLowerCase();
      list = list
          .where(
            (c) =>
                c.name.toLowerCase().contains(q) ||
                epgService
                    .getSchedule(c)
                    .any((p) => p.title.toLowerCase().contains(q)),
          )
          .toList();
    }
    if (_nowOnly) {
      list = list
          .where((channel) => epgService.getCurrentProgramme(channel) != null)
          .toList();
    }
    return list;
  }

  bool _hasInitialized = false;
  bool _disposed = false;

  Future<void> init() async {
    if (_hasInitialized || _disposed) return;
    _hasInitialized = true;

    await settings.init();
    if (_disposed) return;
    AppThemeController.apply(settings.accent);
    if (settings.showHomeAtStartup) setOverlay(OverlayType.home);
    _favorites = settings.favorites;
    _recent = settings.recentChannels;
    // 1. Мгновенно загружаем кэшированные каналы (<5 мс!)
    final cached = await playlistRepo.loadCached(
      settings.sources,
      settings.arrangerOptions,
    );
    if (_disposed) return;
    if (cached.isNotEmpty) {
      _channels = cached;
      notifyListeners();

      // Даём окну и Direct3D текстуре завершить первый layout (250мс)
      await Future.delayed(const Duration(milliseconds: 250));
      if (_disposed) return;

      final lastKey = settings.lastChannel;
      final target = _channels.firstWhere(
        (c) => c.key == lastKey,
        orElse: () => _channels.first,
      );
      play(target, immediate: true);
      await epgService.restore(settings.epgSources, _channels);
      if (_disposed) return;
      notifyListeners();
    }

    // 2. В фоне обновляем/загружаем актуальные плейлисты
    await _loadPlaylists(cached.isEmpty);
    if (_disposed) return;
    _epgRefreshTimer = Timer.periodic(const Duration(minutes: 30), (_) {
      unawaited(refreshEpg(showProgress: false, force: false));
    });
    unawaited(refreshEpg(force: false));
    unawaited(
      Future<void>.delayed(const Duration(seconds: 3), () => checkForUpdates()),
    );
  }

  Future<void> refreshPlaylists() async {
    await _loadPlaylists(true);
    if (!_disposed) unawaited(refreshEpg(showProgress: false));
  }

  Future<void> _loadPlaylists(bool playFirstWhenDone) async {
    if (_disposed) return;
    // refreshAll уже коалесирует параллельные вызовы в один _refresh,
    // поэтому вместо тихого return ждём общий результат.
    await playlistRepo.refreshAll(
      settings.sources,
      settings.arrangerOptions,
      onProgress: (msg) {
        if (!_disposed) showToast(msg);
      },
    );
    if (_disposed) return;
    final channels = await playlistRepo.loadCached(
      settings.sources,
      settings.arrangerOptions,
    );
    if (_disposed) return;
    _channels = channels;
    notifyListeners();
    if (_channels.isNotEmpty) {
      if (playFirstWhenDone && _playbackInfo.channel == null) {
        final lastKey = settings.lastChannel;
        final target = _channels.firstWhere(
          (c) => c.key == lastKey,
          orElse: () => _channels.first,
        );
        play(target, immediate: true);
      }

      // Restart the background scan for the refreshed channel list.
      streamChecker.cancelProbe();
      streamChecker.probeAll(
        _channels,
        defaultUserAgent: settings.globalUserAgent,
        onChannelUpdated: (c) {
          _onProbeProgress();
        },
      );
    }
  }

  Timer? _probeNotifyTimer;

  /// Сканер дергает callback на каждый канал (сотни rebuild PlaylistOverlay),
  /// поэтому склеиваем уведомления в одно за 500 мс.
  void _onProbeProgress() {
    if (_disposed || _probeNotifyTimer?.isActive == true) return;
    _probeNotifyTimer = Timer(const Duration(milliseconds: 500), () {
      if (!_disposed) notifyListeners();
    });
  }

  Future<void> refreshEpg({bool showProgress = true, bool force = true}) async {
    await epgService.updateEpg(
      settings.epgSources,
      _channels,
      maxAge: force ? null : Duration(hours: settings.epgRefreshHours),
      onProgress: showProgress
          ? (msg) {
              if (!_disposed) showToast(msg);
            }
          : null,
    );
    if (!_disposed) notifyListeners();
  }

  void play(
    Channel channel, {
    int? variantIndex,
    Programme? archive,
    bool immediate = true,
  }) {
    if (_disposed) return;
    if (channel.variants.isEmpty) {
      showToast(L10n.t.noStreams(channel.name));
      return;
    }
    // The background prober can reorder the catalogue while this channel plays.
    channel = channel.copyWith(variants: List.unmodifiable(channel.variants));
    final preferred = settings.getPreferredVariant(channel.key);
    // Запомненный рабочий вариант важнее эвристики: сначала он, потом Beeline.
    final remembered = channel.variants.indexWhere(
      (variant) => variant.url == preferred && !_unavailable(variant),
    );
    final beeline = channel.variants.indexWhere(
      (variant) => variant.kind == StreamKind.beeline && !_unavailable(variant),
    );
    final available = channel.variants.indexWhere(
      (variant) => !_unavailable(variant),
    );
    final archiveIndex = archive == null
        ? null
        : channel.variants.indexWhere(
            (variant) =>
                StreamResolver.catchupUrl(
                  variant,
                  archive.start,
                  archive.stop,
                ) !=
                null,
          );
    final targetIdx =
        variantIndex ??
        archiveIndex ??
        [remembered, beeline, available, 0].firstWhere((index) => index >= 0);
    if (targetIdx < 0 ||
        targetIdx >= channel.variants.length ||
        (archive != null &&
            StreamResolver.catchupUrl(
                  channel.variants[targetIdx],
                  archive.start,
                  archive.stop,
                ) ==
                null)) {
      showToast(L10n.t.archiveUnavailable);
      return;
    }
    settings.lastChannel = channel.key;
    if (_recent.firstOrNull != channel.key) {
      _recent = [
        channel.key,
        ..._recent.where((key) => key != channel.key),
      ].take(12).toList();
      settings.recentChannels = _recent;
    }
    _failedVariantsForCurrent.clear();

    _stallTimer?.cancel();
    final attemptId = ++_attemptId;
    _playbackInfo = PlaybackInfo(
      attemptId: attemptId,
      channel: channel,
      variantIndex: targetIdx,
      archive: archive,
      isBuffering: true,
    );
    showInfoBar(temporary: true);
    notifyListeners();

    _zapDebounceTimer?.cancel();
    if (immediate) {
      _playActual(channel, targetIdx, attemptId, archive);
    } else {
      _zapDebounceTimer = Timer(const Duration(milliseconds: 280), () {
        _playActual(channel, targetIdx, attemptId, archive);
      });
    }
  }

  bool _unavailable(StreamVariant variant) =>
      streamChecker.status(variant.url) == StreamHealth.unavailable;

  void _playActual(
    Channel channel,
    int variantIndex,
    int attemptId,
    Programme? archive,
  ) {
    _hasPlaybackProgress = false;
    _armWatchdog(attemptId);
    playerService.cacheSeconds = settings.streamCacheSeconds;
    playerService.play(
      channel,
      attemptId: attemptId,
      variantIndex: variantIndex,
      archive: archive,
      defaultUserAgent: settings.globalUserAgent,
    );
  }

  void _onPlaybackInfo(PlaybackInfo info) {
    if (_disposed) return;
    final channel = info.channel;
    if (info.attemptId != _attemptId || channel == null) return;
    final previous = _playbackInfo;
    _playbackInfo = info;
    final error = info.error;
    if (error != null) {
      _handleStreamFailure(channel, info.variantIndex, error);
    } else if (info.position > Duration.zero &&
        info.position != previous.position) {
      if (!_hasPlaybackProgress) {
        _hasPlaybackProgress = true;
        final variant = channel.variants[info.variantIndex];
        if (info.archive == null) {
          streamChecker.recordPlayback(variant.url, success: true);
          settings.setPreferredVariant(channel.key, variant.url);
        }
      }
      _armWatchdog(info.attemptId);
    } else if (_hasPlaybackProgress &&
        (info.isPlaying != previous.isPlaying ||
            info.isBuffering != previous.isBuffering)) {
      _armWatchdog(info.attemptId);
    }
    notifyListeners();
  }

  void _armWatchdog(int attemptId) {
    _stallTimer?.cancel();
    _stallTimer = Timer(const Duration(seconds: 12), () {
      if (_disposed) return;
      final info = _playbackInfo;
      final channel = info.channel;
      if (attemptId != _attemptId || channel == null) return;
      if (_hasPlaybackProgress && !info.isPlaying && !info.isBuffering) return;
      _handleStreamFailure(
        channel,
        info.variantIndex,
        L10n.t.playbackTimeout,
        timedOut: true,
      );
    });
  }

  void _handleStreamFailure(
    Channel channel,
    int failedIndex,
    String reason, {
    bool timedOut = false,
  }) {
    _stallTimer?.cancel();
    if (_disposed ||
        channel.variants.isEmpty ||
        _failedVariantsForCurrent.contains(failedIndex)) {
      return;
    }
    _failedVariantsForCurrent.add(failedIndex);
    playerService.reportAttemptFailure(
      _playbackInfo.attemptId,
      timedOut: timedOut,
    );
    if (_playbackInfo.archive == null) {
      streamChecker.recordPlayback(
        channel.variants[failedIndex].url,
        success: false,
      );
    }

    final archive = _playbackInfo.archive;
    if (channel.variants.length > 1) {
      int nextIdx = -1;
      for (var step = 1; step < channel.variants.length; step++) {
        final candidate = (failedIndex + step) % channel.variants.length;
        if (archive != null &&
            StreamResolver.catchupUrl(
                  channel.variants[candidate],
                  archive.start,
                  archive.stop,
                ) ==
                null) {
          continue;
        }
        if (!_failedVariantsForCurrent.contains(candidate)) {
          if (nextIdx < 0) nextIdx = candidate;
          if (!_unavailable(channel.variants[candidate])) {
            nextIdx = candidate;
            break;
          }
        }
      }

      if (nextIdx != -1) {
        final failedVar = channel.variants[failedIndex];
        final nextVar = channel.variants[nextIdx];
        showToast(
          L10n.t.variantFailed(
            channel.name,
            failedVar.sourceName,
            nextVar.sourceName,
          ),
        );
        final attemptId = ++_attemptId;
        _playbackInfo = PlaybackInfo(
          attemptId: attemptId,
          channel: channel,
          variantIndex: nextIdx,
          isBuffering: true,
          archive: archive,
        );
        notifyListeners();
        _playActual(channel, nextIdx, attemptId, archive);
        return;
      }
    }

    _attemptId++;
    _playbackInfo = _playbackInfo.copyWith(
      attemptId: _attemptId,
      isPlaying: false,
      isBuffering: false,
      error: archive == null ? reason : L10n.t.archiveUnavailableSources,
    );
    showToast(L10n.t.allSourcesDown(channel.name, channel.variants.length));
  }

  void selectVariant(int index) {
    final ch = _playbackInfo.channel;
    if (_disposed || ch == null || index < 0 || index >= ch.variants.length) {
      return;
    }
    final v = ch.variants[index];
    final archive = _playbackInfo.archive;
    if (archive != null &&
        StreamResolver.catchupUrl(v, archive.start, archive.stop) == null) {
      showToast(L10n.t.archiveUnavailable);
      return;
    }
    showToast(L10n.t.sourceSelected(v.sourceName));
    play(ch, variantIndex: index, archive: archive);
  }

  void zapNext() {
    final delta = settings.reverseZap ? -1 : 1;
    _zapRelative(delta);
  }

  void zapPrev() {
    final delta = settings.reverseZap ? 1 : -1;
    _zapRelative(delta);
  }

  void _zapRelative(int delta) {
    if (_channels.isEmpty) return;
    final curKey = _playbackInfo.channel?.key ?? settings.lastChannel;
    final curIdx = _channels.indexWhere((c) => c.key == curKey);
    final nextIdx = (curIdx < 0 ? 0 : curIdx + delta) % _channels.length;
    final targetIdx = nextIdx < 0 ? nextIdx + _channels.length : nextIdx;
    play(_channels[targetIdx], immediate: false);
  }

  void nextVariant() => _switchVariant(1);

  void prevVariant() => _switchVariant(-1);

  void _switchVariant(int direction) {
    final ch = _playbackInfo.channel;
    if (ch == null || ch.variants.length < 2) return;
    final archive = _playbackInfo.archive;
    for (var step = 1; step < ch.variants.length; step++) {
      final index =
          (_playbackInfo.variantIndex + step * direction) % ch.variants.length;
      if (archive != null &&
          StreamResolver.catchupUrl(
                ch.variants[index],
                archive.start,
                archive.stop,
              ) ==
              null) {
        continue;
      }
      selectVariant(index);
      return;
    }
    showToast(L10n.t.noOtherArchive);
  }

  void toggleFavorite(Channel channel) {
    final favs = Set<String>.from(_favorites);
    final added = !favs.contains(channel.key);
    if (added) {
      favs.add(channel.key);
    } else {
      favs.remove(channel.key);
    }
    _favorites = favs;
    settings.favorites = favs;
    showToast(
      added
          ? L10n.t.favouriteAdded(channel.name)
          : L10n.t.favouriteRemoved(channel.name),
    );
    notifyListeners();
  }

  void selectGroup(String group) {
    _selectedGroup = group;
    _previewChannel = null;
    notifyListeners();
  }

  /// Для тестов: подставить каналы без загрузки плейлистов и сети.
  @visibleForTesting
  void debugSetChannels(List<Channel> channels) {
    _channels = channels;
    notifyListeners();
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  void toggleNow() {
    _nowOnly = !_nowOnly;
    _previewChannel = null;
    notifyListeners();
  }

  void onDigit(String digit) {
    if (_quickDigits.length > 4) return;
    _quickDigits += digit;
    notifyListeners();

    _quickDigitsTimer?.cancel();
    _quickDigitsTimer = Timer(
      const Duration(milliseconds: 1500),
      _executeQuickDigits,
    );
  }

  void _executeQuickDigits() {
    if (_disposed || _quickDigits.isEmpty) return;
    final num = int.tryParse(_quickDigits);
    _quickDigits = '';
    _quickDigitsTimer?.cancel();
    notifyListeners();

    if (num == null || _channels.isEmpty) return;
    final target = _channels.firstWhere(
      (c) => c.number == num,
      orElse: () => (num >= 1 && num <= _channels.length)
          ? _channels[num - 1]
          : _channels.first,
    );
    play(target, immediate: false);
  }

  void setVolume(double volume) {
    if (_disposed) return;
    final next = volume.clamp(0.0, 100.0);
    playerService.setVolume(next);
    if (next > 0) _prevVolume = next;
    _isMuted = next == 0;
    _volumeOverlay = next;
    notifyListeners();

    _volumeTimer?.cancel();
    _volumeTimer = Timer(const Duration(milliseconds: 1500), () {
      if (_disposed) return;
      _volumeOverlay = null;
      notifyListeners();
    });
  }

  void changeVolume(double delta) {
    if (_disposed) return;
    setVolume(playerService.volume + delta);
  }

  void toggleMute() {
    if (_disposed) return;
    if (_isMuted) {
      playerService.setVolume(_prevVolume > 0 ? _prevVolume : 50.0);
      _isMuted = false;
      showToast(L10n.t.soundOn);
    } else {
      _prevVolume = playerService.volume;
      playerService.setVolume(0);
      _isMuted = true;
      showToast(L10n.t.soundOff);
    }
    notifyListeners();
  }

  void setAspectRatio(String? ratio) {
    if (_disposed) return;
    settings.aspectRatio = ratio;
    playerService.setAspectRatio(ratio);
    showToast(L10n.t.aspectFormat(ratio ?? L10n.t.original));
    notifyListeners();
  }

  void setOverlay(OverlayType type) {
    if (compactMode && type != OverlayType.none) return;
    _overlay = type;
    if (type != OverlayType.none) {
      showInfoBar(temporary: false);
    }
    notifyListeners();
  }

  void closeOverlay() {
    _overlay = OverlayType.none;
    notifyListeners();
  }

  void toggleOverlay(OverlayType type) {
    if (_overlay == type) {
      closeOverlay();
    } else {
      setOverlay(type);
    }
  }

  void toggleInfoBar() {
    if (_isInfoBarVisible) {
      hideInfoBar();
    } else if (_playbackInfo.channel != null) {
      showInfoBar(temporary: false);
    }
  }

  void showInfoBar({bool temporary = true}) {
    _isInfoBarVisible = true;
    _infoBarTimer?.cancel();
    if (temporary) {
      _infoBarTimer = Timer(const Duration(seconds: 5), () {
        if (_disposed) return;
        if (_overlay == OverlayType.none) {
          _isInfoBarVisible = false;
          notifyListeners();
        }
      });
    }
    notifyListeners();
  }

  void hideInfoBar() {
    _infoBarTimer?.cancel();
    _isInfoBarVisible = false;
    notifyListeners();
  }

  Future<void> toggleFullscreen() async {
    if (compactMode || _changingWindow) return;
    _changingWindow = true;
    try {
      _isFullscreen = !_isFullscreen;
      await windowManager.setFullScreen(_isFullscreen);
    } finally {
      _changingWindow = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<void> toggleCompact() async {
    if (_changingWindow) return;
    _changingWindow = true;
    try {
      final normal = _normalWindow;
      if (normal == null) {
        if (_isFullscreen) await toggleFullscreen();
        if (await windowManager.isMaximized()) await windowManager.unmaximize();
        _normalWindow = await windowManager.getBounds();
        closeOverlay();
        await windowManager.setMinimumSize(const Size(320, 220));
        await windowManager.setSize(const Size(420, 275));
        await windowManager.setAlwaysOnTop(true);
      } else {
        await windowManager.setAlwaysOnTop(false);
        await windowManager.setBounds(normal);
        await windowManager.setMinimumSize(const Size(640, 360));
        _normalWindow = null;
      }
    } finally {
      _changingWindow = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<void> checkForUpdates({bool manual = false}) async {
    if (_disposed || _updateCheckInFlight) return;
    _updateCheckInFlight = true;
    if (manual) {
      _updateBusy = L10n.t.updateChecking;
      notifyListeners();
    }
    try {
      final info = await updateService.check();
      if (_disposed) return;
      if (info != null) {
        _update = info;
      } else if (manual) {
        showToast(L10n.t.updateLatest);
      }
    } catch (error) {
      if (manual && !_disposed) {
        showToast(L10n.t.updateError(error.toString()));
      }
    } finally {
      _updateCheckInFlight = false;
      if (!_disposed) {
        _updateBusy = null;
        notifyListeners();
      }
    }
  }

  Future<void> downloadAndInstallUpdate() async {
    final info = _update;
    if (_disposed || info == null) return;
    _updateBusy = L10n.t.updateDownloading(info.version);
    _updateProgress = 0;
    notifyListeners();
    try {
      final started = await updateService.install(
        info,
        onProgress: (progress) {
          if (_disposed) return;
          _updateProgress = progress;
          notifyListeners();
        },
      );
      if (!_disposed && !started) {
        _updateBusy = null;
        _updateProgress = null;
        notifyListeners();
        showToast(L10n.t.updateInstallFailed);
      }
    } catch (error) {
      if (_disposed) return;
      _updateBusy = null;
      _updateProgress = null;
      notifyListeners();
      showToast(L10n.t.updateError(error.toString()));
    }
  }

  void dismissUpdate() {
    if (_disposed) return;
    _update = null;
    _updateBusy = null;
    _updateProgress = null;
    notifyListeners();
  }

  void showToast(String message) {
    if (_disposed) return;
    _toast = message;
    notifyListeners();
    _toastTimer?.cancel();
    _toastTimer = Timer(const Duration(seconds: 3), () {
      if (_disposed) return;
      if (_toast == message) {
        _toast = null;
        notifyListeners();
      }
    });
  }

  void refresh() => notifyListeners();

  Future<void> shutdown() {
    dispose();
    return playerService.shutdown();
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _infoBarTimer?.cancel();
    _toastTimer?.cancel();
    _quickDigitsTimer?.cancel();
    _volumeTimer?.cancel();
    _epgRefreshTimer?.cancel();
    _zapDebounceTimer?.cancel();
    _stallTimer?.cancel();
    _probeNotifyTimer?.cancel();
    streamChecker.cancelProbe();
    _playerSubscription.cancel();
    updateService.dispose();
    playerService.dispose();
    super.dispose();
  }
}
