import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/settings_model.dart';
import '../services/channel_arranger.dart';

class SettingsRepository {
  SharedPreferences? _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    // Auto-migrate saved sources to ensure fresh working URLs and proper defaults
    final current = sources;
    _prefs?.setString(
      'sources',
      jsonEncode(current.map((e) => e.toJson()).toList()),
    );
  }

  List<SourceConfig> get sources {
    final str = _prefs?.getString('sources');
    if (str == null) return Defaults.sources;
    try {
      final saved = (jsonDecode(str) as List<dynamic>)
          .map((e) => SourceConfig.fromJson(e as Map<String, dynamic>))
          .toList();
      final byId = {for (final s in saved) s.id: s};
      final builtInIds = Defaults.sources.map((d) => d.id).toSet();
      final merged = <SourceConfig>[
        for (final d in Defaults.sources)
          byId[d.id] == null
              ? d
              : SourceConfig(
                  id: d.id,
                  name: d.name,
                  urls: d.urls,
                  kind: d.kind,
                  enabled: byId[d.id]!.enabled,
                  builtIn: true,
                  playlistUserAgent: d.playlistUserAgent,
                  streamUserAgent:
                      byId[d.id]!.streamUserAgent ?? d.streamUserAgent,
                  sort: d.sort,
                ),
        ...saved.where((s) => !s.builtIn && !builtInIds.contains(s.id)),
      ]..sort((a, b) => a.sort.compareTo(b.sort));
      return merged;
    } catch (_) {
      return Defaults.sources;
    }
  }

  set sources(List<SourceConfig> list) {
    _prefs?.setString(
      'sources',
      jsonEncode(list.map((e) => e.toJson()).toList()),
    );
  }

  List<EpgSourceConfig> get epgSources {
    final str = _prefs?.getString('epg_sources');
    if (str != null) {
      try {
        final list = jsonDecode(str) as List<dynamic>;
        return list
            .map((e) => EpgSourceConfig.fromJson(e as Map<String, dynamic>))
            .where((source) => source.id != 'anzo')
            .toList();
      } catch (_) {}
    }
    return Defaults.epgSources;
  }

  set epgSources(List<EpgSourceConfig> list) {
    _prefs?.setString(
      'epg_sources',
      jsonEncode(list.map((e) => e.toJson()).toList()),
    );
  }

  Set<String> get favorites {
    final list = _prefs?.getStringList('favorites') ?? [];
    return list.toSet();
  }

  set favorites(Set<String> set) {
    _prefs?.setStringList('favorites', set.toList());
  }

  bool get showHomeAtStartup =>
      _prefs?.getBool('show_home_at_startup') ?? false;
  set showHomeAtStartup(bool value) =>
      _prefs?.setBool('show_home_at_startup', value);

  String? get lastChannel => _prefs?.getString('last_channel');
  set lastChannel(String? v) => v != null
      ? _prefs?.setString('last_channel', v)
      : _prefs?.remove('last_channel');

  List<String> get recentChannels =>
      _prefs?.getStringList('recent_channels') ?? [];
  set recentChannels(List<String> value) =>
      _prefs?.setStringList('recent_channels', value);

  int get epgRefreshHours => _prefs?.getInt('epg_refresh_hours') ?? 24;
  set epgRefreshHours(int value) => _prefs?.setInt('epg_refresh_hours', value);

  String get accent => _prefs?.getString('accent') ?? 'violet';
  set accent(String value) => _prefs?.setString('accent', value);

  int get streamCacheSeconds => _prefs?.getInt('stream_cache_seconds') ?? 20;
  set streamCacheSeconds(int value) =>
      _prefs?.setInt('stream_cache_seconds', value);

  bool get reverseZap => _prefs?.getBool('reverse_zap') ?? false;
  set reverseZap(bool v) => _prefs?.setBool('reverse_zap', v);

  bool get hideSdDuplicates => _prefs?.getBool('hide_sd') ?? true;
  set hideSdDuplicates(bool v) => _prefs?.setBool('hide_sd', v);

  bool get hideTimezones => _prefs?.getBool('hide_tz') ?? true;
  set hideTimezones(bool v) => _prefs?.setBool('hide_tz', v);

  bool get standardOrder => _prefs?.getBool('standard_order') ?? true;
  set standardOrder(bool v) => _prefs?.setBool('standard_order', v);

  bool get mergeOtt => _prefs?.getBool('merge_ott') ?? true;
  set mergeOtt(bool v) => _prefs?.setBool('merge_ott', v);

  bool get hideAdult => _prefs?.getBool('hide_adult') ?? true;
  set hideAdult(bool v) => _prefs?.setBool('hide_adult', v);

  bool get preferMaxQuality => _prefs?.getBool('prefer_max_quality') ?? true;
  set preferMaxQuality(bool v) => _prefs?.setBool('prefer_max_quality', v);

  String get globalUserAgent =>
      _prefs?.getString('ua') ?? UserAgents.defaultGlobal;

  String? get aspectRatio {
    final v = _prefs?.getString('aspect_ratio');
    // Унаследованное сохранённое «Исходный» эквивалентно null
    return (v == null || v == 'Исходный') ? null : v;
  }

  set aspectRatio(String? v) => v != null
      ? _prefs?.setString('aspect_ratio', v)
      : _prefs?.remove('aspect_ratio');

  String? getPreferredVariant(String channelKey) {
    return _prefs?.getString('pref_src_$channelKey');
  }

  void setPreferredVariant(String channelKey, String url) {
    _prefs?.setString('pref_src_$channelKey', url);
  }

  ArrangerOptions get arrangerOptions => ArrangerOptions(
    hideSdDuplicates: hideSdDuplicates,
    hideTimezones: hideTimezones,
    standardOrder: standardOrder,
    mergeOtt: mergeOtt,
    hideAdult: hideAdult,
    preferMaxQuality: preferMaxQuality,
  );
}
