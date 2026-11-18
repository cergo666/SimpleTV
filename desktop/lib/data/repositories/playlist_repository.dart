import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart' show FlutterError;
import 'package:flutter/services.dart' show rootBundle;
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import '../../l10n/l10n.dart';
import '../models/channel.dart';
import '../models/settings_model.dart';
import '../services/channel_arranger.dart';
import '../services/m3u_parser.dart';
import '../services/packaged_playlist.dart';
import '../services/public_tv.dart';
import '../services/stream_resolver.dart';

typedef SourceRefreshStatus = ({DateTime? lastSuccess, bool failed});

class PlaylistRepository {
  PlaylistRepository({this.cacheDirectory});

  final Directory? cacheDirectory;
  final _sourceChannels = <String, List<ParsedSourceChannel>>{};
  final _bundledChannels = <String, List<ParsedSourceChannel>>{};
  final _sourceStatuses = <String, SourceRefreshStatus>{};
  Future<void>? _cacheLoad;
  String? _cacheKey;
  Future<List<Channel>>? _refresh;
  List<Channel> _mergedChannels = [];

  SourceRefreshStatus? sourceStatus(String id) => _sourceStatuses[id];

  bool get isLoading => _refresh != null;

  static String decodePlaylistBytes(List<int> bytes) {
    var raw = bytes;
    if (raw.length >= 3 && raw[0] == 0xEF && raw[1] == 0xBB && raw[2] == 0xBF) {
      raw = raw.sublist(3);
    }
    try {
      final utf = utf8.decode(raw);
      final bad = '\uFFFD'.allMatches(utf).length;
      if (bad > 10 && bad > utf.length / 200) {
        return _decodeWin1251(raw);
      }
      return utf;
    } catch (_) {
      return _decodeWin1251(raw);
    }
  }

  static const _win1251Extra = <int, String>{
    0x80: 'Ђ',
    0x81: 'Ѓ',
    0x82: '‚',
    0x83: 'ѓ',
    0x84: '„',
    0x85: '…',
    0x86: '†',
    0x87: '‡',
    0x88: '€',
    0x89: '‰',
    0x8A: 'Љ',
    0x8B: '‹',
    0x8C: 'Њ',
    0x8D: 'Ќ',
    0x8E: 'Ћ',
    0x8F: 'Џ',
    0x90: 'ђ',
    0x91: '‘',
    0x92: '’',
    0x93: '“',
    0x94: '”',
    0x95: '•',
    0x96: '–',
    0x97: '—',
    0x98: '',
    0x99: '™',
    0x9A: 'љ',
    0x9B: '›',
    0x9C: 'њ',
    0x9D: 'ќ',
    0x9E: 'ћ',
    0x9F: 'џ',
    0xA0: ' ',
    0xA1: 'Ў',
    0xA2: 'ў',
    0xA3: 'Ј',
    0xA4: '¤',
    0xA5: 'Ґ',
    0xA6: '¦',
    0xA7: '§',
    0xA8: 'Ё',
    0xA9: '©',
    0xAA: 'Є',
    0xAB: '«',
    0xAC: '¬',
    0xAD: '­',
    0xAE: '®',
    0xAF: 'Ї',
    0xB0: '°',
    0xB1: '±',
    0xB2: 'І',
    0xB3: 'і',
    0xB4: 'ґ',
    0xB5: 'µ',
    0xB6: '¶',
    0xB7: '·',
    0xB8: 'ё',
    0xB9: '№',
    0xBA: 'є',
    0xBB: '»',
    0xBC: 'ј',
    0xBD: 'Ѕ',
    0xBE: 'ѕ',
    0xBF: 'ї',
  };

  static String _decodeWin1251(List<int> bytes) {
    const win1251 =
        'АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдежзийклмнопрстуфхцчшщъыьэюя';
    final sb = StringBuffer();
    for (final b in bytes) {
      if (b < 128) {
        sb.writeCharCode(b);
      } else if (b >= 192 && b <= 255) {
        sb.write(win1251[b - 192]);
      } else if (b >= 128) {
        sb.write(_win1251Extra[b] ?? ' ');
      } else {
        sb.writeCharCode(b);
      }
    }
    return sb.toString();
  }

  Future<Directory> _getCacheDirectory() async {
    if (cacheDirectory case final dir?) return dir;
    final exeDir = File(Platform.resolvedExecutable).parent;
    final portableDir = Directory.fromUri(exeDir.uri.resolve('cache/'));
    if (await portableDir.exists()) return portableDir;

    final appDir = await getApplicationSupportDirectory();
    return Directory.fromUri(appDir.uri.resolve('cache/'));
  }

  Future<void> _readCache(List<SourceConfig> sources) async {
    await _readSnapshots(sources);
    for (final source in sources.where((s) => s.enabled && _isBundled(s))) {
      _bundledChannels[source.id] = await _loadSource(
        source,
        urls: source.urls.where(_isAsset),
      );
    }
  }

  static bool _isAsset(String url) => url.startsWith('asset://');

  static bool _isBundled(SourceConfig source) =>
      source.builtIn && source.urls.any(_isAsset);

  Future<void> _readSnapshots(List<SourceConfig> sources) async {
    try {
      final dir = await _getCacheDirectory();
      final file = File.fromUri(dir.uri.resolve('playlist_sources.json'));
      if (await file.exists()) {
        final saved =
            jsonDecode(await file.readAsString()) as Map<String, dynamic>;
        for (final entry in saved.entries) {
          final snapshot = entry.value as Map<String, dynamic>;
          final source = sources.where((s) => s.id == entry.key).firstOrNull;
          if (source != null &&
              _isBundled(source) &&
              snapshot['onlineOnly'] != true) {
            continue;
          }
          final lastSuccess = snapshot['lastSuccess'] as String?;
          _sourceStatuses[entry.key] = (
            lastSuccess: lastSuccess == null
                ? null
                : DateTime.tryParse(lastSuccess),
            failed: snapshot['failed'] == true,
          );
          _sourceChannels[entry.key] = (snapshot['channels'] as List)
              .map(
                (item) =>
                    ParsedSourceChannel.fromJson(item as Map<String, dynamic>),
              )
              .toList();
        }
        return;
      }
    } catch (_) {
      _sourceStatuses.clear();
      _sourceChannels.clear();
    }

    // Older releases stored the merged list. Recover variants with an
    // unambiguous source name until each source can be refreshed.
    try {
      final dir = await _getCacheDirectory();
      final legacy = File.fromUri(dir.uri.resolve('channels_cache.json'));
      if (!await legacy.exists()) return;
      final saved = jsonDecode(await legacy.readAsString()) as List;
      final byName = <String, List<SourceConfig>>{};
      for (final source in sources) {
        byName.putIfAbsent(source.name, () => []).add(source);
      }
      for (final item in saved) {
        final channel = Channel.fromJson(item as Map<String, dynamic>);
        for (final variant in channel.variants) {
          final matches =
              byName[variant.sourceName.replaceFirst(RegExp(r' \(OTT\)$'), '')];
          if (matches == null || matches.length != 1) continue;
          final source = matches.single;
          if (_isBundled(source)) continue;
          _sourceChannels
              .putIfAbsent(source.id, () => [])
              .add(
                ParsedSourceChannel(
                  rawName: channel.name,
                  normalizedName: channel.key,
                  group: channel.group,
                  logoUrl: channel.logoUrl,
                  epgId: channel.epgId,
                  variant: variant,
                  sourceSort: source.sort,
                ),
              );
        }
      }
    } catch (_) {
      _sourceChannels.clear();
    }
  }

  Future<void> _ensureCache(List<SourceConfig> sources) {
    // Ключ инвалидируется при add/remove источников, иначе новый источник
    // никогда не прочитается из снапшота (будет виден только после refresh).
    final key = sources
        .map((s) => '${s.id}=${s.enabled}:${s.urls.join(',')}')
        .join(';');
    if (_cacheLoad == null || _cacheKey != key) {
      _cacheKey = key;
      _cacheLoad = _readCache(sources);
    }
    return _cacheLoad!;
  }

  Future<List<Channel>> loadCached(
    List<SourceConfig> sources,
    ArrangerOptions options,
  ) async {
    await _ensureCache(sources);
    return _mergeSources(sources, options);
  }

  Future<List<Channel>> refreshAll(
    List<SourceConfig> sources,
    ArrangerOptions options, {
    void Function(String msg)? onProgress,
  }) {
    return _refresh ??= _refreshAll(
      sources,
      options,
      onProgress,
    ).whenComplete(() => _refresh = null);
  }

  Future<List<Channel>> _refreshAll(
    List<SourceConfig> sources,
    ArrangerOptions options,
    void Function(String msg)? onProgress,
  ) async {
    await _ensureCache(sources);
    final sortedSources = [...sources]
      ..sort((a, b) => a.sort.compareTo(b.sort));
    for (final source in sortedSources.where((s) => s.enabled)) {
      onProgress?.call(L10n.t.loadingPlaylist(source.name));
      final bundled = _isBundled(source);
      final urls = bundled
          ? source.urls.where((url) => !_isAsset(url))
          : source.urls;
      if (urls.isEmpty && bundled) {
        _sourceStatuses[source.id] = (
          lastSuccess: DateTime.now(),
          failed: false,
        );
        continue;
      }
      final channels = await _loadSource(source, urls: urls);
      if (channels.isNotEmpty) {
        _sourceChannels[source.id] = channels;
        _sourceStatuses[source.id] = (
          lastSuccess: DateTime.now(),
          failed: false,
        );
      } else {
        final lastSuccess = _sourceStatuses[source.id]?.lastSuccess;
        _sourceStatuses[source.id] = (lastSuccess: lastSuccess, failed: true);
        if (!bundled && _sourceChannels[source.id]?.isNotEmpty != true) {
          final fallback = await _loadAsset('playlists/${source.id}.m3u');
          final parsed = _parsePlaylist(fallback, source);
          if (parsed.isNotEmpty) _sourceChannels[source.id] = parsed;
        }
      }
    }

    try {
      final dir = await _getCacheDirectory();
      await dir.create(recursive: true);
      final file = File.fromUri(dir.uri.resolve('playlist_sources.json'));
      final tmp = File('${file.path}.tmp');
      await tmp.writeAsString(
        jsonEncode({
          for (final source in sources)
            if (_sourceChannels.containsKey(source.id) ||
                _sourceStatuses.containsKey(source.id))
              source.id: {
                if (_isBundled(source)) 'onlineOnly': true,
                'lastSuccess': _sourceStatuses[source.id]?.lastSuccess
                    ?.toIso8601String(),
                'failed': _sourceStatuses[source.id]?.failed ?? false,
                'channels': _sourceChannels[source.id] ?? const [],
              },
        }),
        flush: true,
      );
      try {
        await tmp.rename(file.path);
      } on FileSystemException {
        // Windows не переименовывает поверх существующего файла.
        await file.delete();
        await tmp.rename(file.path);
      }
    } on FileSystemException {
      onProgress?.call(L10n.t.cacheSaveFailed);
    }
    return _mergeSources(sources, options);
  }

  Future<List<ParsedSourceChannel>> _loadSource(
    SourceConfig source, {
    required Iterable<String> urls,
  }) async {
    for (final url in urls) {
      try {
        List<int>? bytes;
        if (url.startsWith('asset://')) {
          bytes = await _loadAsset(url.substring('asset://'.length));
        } else if (url.startsWith('http://') || url.startsWith('https://')) {
          final res = await http
              .get(
                Uri.parse(url),
                headers: {
                  if (source.playlistUserAgent != null)
                    'User-Agent': source.playlistUserAgent!,
                },
              )
              .timeout(const Duration(seconds: 15));
          if (res.statusCode == 200) {
            final body = decodePlaylistBytes(res.bodyBytes);
            bytes = utf8.encode(
              source.builtIn && url.startsWith('https://')
                  ? PublicTv.catalog(source.id, body)
                  : body,
            );
          }
        } else {
          final path = url.startsWith('file://')
              ? Uri.parse(url).toFilePath()
              : url;
          bytes = await File(path).readAsBytes();
        }
        final parsed = _parsePlaylist(bytes, source);
        if (parsed.isNotEmpty) return parsed;
      } catch (_) {
        // Try the next URL before falling back to the cached source.
      }
    }
    return [];
  }

  List<ParsedSourceChannel> _channelsFor(SourceConfig source) {
    final updates = _sourceChannels[source.id] ?? const [];
    if (!_isBundled(source)) return updates;

    String endpoint(ParsedSourceChannel channel) {
      final variant = channel.variant;
      return StreamResolver.resolve(variant.url, variant.kind).url;
    }

    final bundled =
        _bundledChannels[source.id] ?? const <ParsedSourceChannel>[];
    final merged = <String, ParsedSourceChannel>{
      for (final channel in bundled) endpoint(channel): channel,
    };
    final cineramaMetadata = <String, ParsedSourceChannel>{
      for (final channel in bundled)
        if (PublicTv.cineramaChannelId(channel.variant.url)
            case final String id)
          id: channel,
    };
    for (final update in updates) {
      final key = endpoint(update);
      final existing =
          merged[key] ??
          cineramaMetadata[PublicTv.cineramaChannelId(update.variant.url)];
      final variant = update.variant;
      merged[key] = existing == null
          ? update
          : ParsedSourceChannel(
              rawName: existing.rawName,
              normalizedName: existing.normalizedName,
              group: existing.group,
              logoUrl: existing.logoUrl ?? update.logoUrl,
              epgId: existing.epgId ?? update.epgId,
              sourceSort: existing.sourceSort,
              variant: StreamVariant(
                url: variant.url,
                sourceName: variant.sourceName,
                kind: variant.kind,
                userAgent: variant.userAgent,
                referer: variant.referer,
                clearKey: variant.clearKey ?? existing.variant.clearKey,
                catchup: variant.catchup ?? existing.variant.catchup,
                catchupDays: (variant.catchupDays ?? 0) > 0
                    ? variant.catchupDays
                    : existing.variant.catchupDays,
                isOtt: variant.isOtt || existing.variant.isOtt,
              ),
            );
    }
    return merged.values.toList();
  }

  static List<ParsedSourceChannel> _parsePlaylist(
    List<int>? bytes,
    SourceConfig source,
  ) {
    if (bytes == null || bytes.isEmpty) return [];
    final body = decodePlaylistBytes(bytes);
    if (!body.trimLeft().startsWith('#EXTM3U')) return [];
    return M3uParser.parse(body, source);
  }

  List<Channel> _mergeSources(
    List<SourceConfig> sources,
    ArrangerOptions options,
  ) {
    final sortedSources = [...sources]
      ..sort((a, b) => a.sort.compareTo(b.sort));
    final grouped = <String, List<ParsedSourceChannel>>{};
    for (final source in sortedSources.where((source) => source.enabled)) {
      for (final p in _channelsFor(source)) {
        // Keep the saved channel key when displaying the corrected HD label.
        final k =
            (p.variant.kind == StreamKind.beeline ||
                    const {'smotrim', 'vitrina'}.contains(source.id)) &&
                p.normalizedName == 'россия к hd'
            ? 'россия к'
            : p.normalizedName.toLowerCase();
        grouped.putIfAbsent(k, () => []).add(p);
      }
    }

    final merged = <Channel>[];
    for (final entry in grouped.entries) {
      final items = entry.value;
      if (items.isEmpty) continue;
      final first = items.first;
      final group = first.group == 'Билайн'
          ? items
                .firstWhere(
                  (item) =>
                      item.group != 'Билайн' && item.group != 'Новые каналы',
                  orElse: () => first,
                )
                .group
          : first.group;

      final variants = <StreamVariant>[];
      final seenUrls = <String>{};
      for (final it in items) {
        if (!seenUrls.contains(it.variant.url)) {
          seenUrls.add(it.variant.url);
          variants.add(it.variant);
        }
      }

      merged.add(
        Channel(
          key: entry.key,
          name: first.rawName,
          group: group,
          logoUrl: items.map((item) => item.logoUrl).nonNulls.firstOrNull,
          epgId: items.map((item) => item.epgId).nonNulls.firstOrNull,
          variants: variants,
        ),
      );
    }

    _mergedChannels = merged;
    return arrange(options);
  }

  List<Channel> arrange(ArrangerOptions options) {
    return ChannelArranger.arrange(_mergedChannels, options);
  }

  static Future<List<int>?> _loadAsset(String path) async {
    try {
      final asset = path.startsWith('assets/') ? path : 'assets/$path';
      final data = await rootBundle.load(asset);
      final bytes = data.buffer.asUint8List(
        data.offsetInBytes,
        data.lengthInBytes,
      );
      return unpackPlaylist(bytes);
    } on FlutterError {
      return null;
    } on FormatException {
      return null;
    }
  }
}
