import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import '../models/channel.dart';
import '../models/settings_model.dart';
import 'stream_resolver.dart';
import 'public_tv.dart';

enum StreamHealth { reachable, unavailable, playing }

class StreamCheckerService {
  StreamCheckerService({DateTime Function()? clock})
    : _clock = clock ?? DateTime.now;

  final DateTime Function() _clock;
  final _health = <String, StreamHealth>{};
  final _recordedAt = <String, DateTime>{};
  int _generation = 0;

  /// Время жизни записей — как в core/StreamChecker: истёкшие считаются
  /// неизвестными и перепроверяются, иначе «рестарт скана» ничего не даёт.
  static const _unavailableTtl = Duration(minutes: 5);
  static const _reachableTtl = Duration(minutes: 15);
  static const _playingTtl = Duration(minutes: 30);

  StreamHealth? status(String url) {
    final health = _health[url];
    if (health == null) return null;
    final recorded = _recordedAt[url];
    final ttl = switch (health) {
      StreamHealth.unavailable => _unavailableTtl,
      StreamHealth.reachable => _reachableTtl,
      StreamHealth.playing => _playingTtl,
    };
    if (recorded == null || _clock().difference(recorded) > ttl) {
      _health.remove(url);
      _recordedAt.remove(url);
      return null;
    }
    return health;
  }

  void recordPlayback(String url, {required bool success}) {
    _health[url] = success ? StreamHealth.playing : StreamHealth.unavailable;
    _recordedAt[url] = _clock();
  }

  void cancelProbe() => _generation++;

  Future<void> checkVariant(
    StreamVariant variant, {
    String? defaultUserAgent,
  }) async {
    // status() also expires stale entries; containsKey would keep skipping
    // URLs whose TTL ran out but whose status was never read by the UI.
    if (status(variant.url) != null) return;
    if (PublicTv.needsResolution(variant.url)) return;
    final generation = _generation;
    final client = HttpClient()
      ..connectionTimeout = const Duration(milliseconds: 2500);
    var health = StreamHealth.unavailable;
    try {
      final resolved = StreamResolver.resolve(variant.url, variant.kind);
      final uri = Uri.tryParse(resolved.url);
      if (uri == null || !const {'http', 'https'}.contains(uri.scheme)) return;
      final kind = resolved.kind != StreamKind.generic
          ? resolved.kind
          : variant.kind;
      final userAgent =
          variant.userAgent ??
          switch (kind) {
            StreamKind.wink => UserAgents.winkTizen,
            StreamKind.beeline => UserAgents.beelineManifest,
            StreamKind.generic => defaultUserAgent ?? UserAgents.defaultGlobal,
          };
      final headers = {
        HttpHeaders.userAgentHeader: userAgent,
        if (variant.referer != null)
          HttpHeaders.refererHeader: variant.referer!,
      };
      await _checkResources(
        client,
        uri,
        headers,
      ).timeout(const Duration(seconds: 6));
      health = StreamHealth.reachable;
    } on Exception {
      // A timed-out or rejected request is a negative observation for this session.
    } finally {
      client.close(force: true);
    }
    if (generation == _generation) {
      _health.putIfAbsent(variant.url, () => health);
      _recordedAt.putIfAbsent(variant.url, _clock);
    }
  }

  static Future<void> _checkResources(
    HttpClient client,
    Uri uri,
    Map<String, String> headers,
  ) async {
    var (base, body) = await _fetch(client, uri, headers, playlist: true);
    var text = utf8.decode(body, allowMalformed: true).trimLeft();
    if (!text.startsWith('#EXTM3U')) return;
    final child = _mediaPlaylist.firstMatch(text)?.group(1);
    if (child != null) {
      (base, body) = await _fetch(
        client,
        base.resolve(child.trim()),
        headers,
        playlist: true,
      );
      text = utf8.decode(body, allowMalformed: true).trimLeft();
      if (!text.startsWith('#EXTM3U')) {
        throw const FormatException('Invalid HLS playlist');
      }
    }
    final keyTags = _keyTag.allMatches(text);
    if (keyTags.isEmpty) return;
    final key = keyTags.last.group(1)!;
    if (!_aes128.hasMatch(key)) return;
    final format = _keyFormat.firstMatch(key)?.group(1);
    if (format != null && format != 'identity') return;
    final keyUrl = _keyUri.firstMatch(key)?.group(1);
    if (keyUrl == null) throw const FormatException('Missing HLS key URI');
    final keyUri = base.resolve(keyUrl);
    if (keyUri.scheme == 'data') return;
    final (_, bytes) = await _fetch(client, keyUri, headers, playlist: false);
    if (bytes.length != 16) {
      throw const FormatException('Invalid HLS key length');
    }
  }

  static final _mediaPlaylist = RegExp(
    r'^#EXT-X-STREAM-INF:[^\r\n]*\r?\n([^#\r\n][^\r\n]*)',
    multiLine: true,
  );
  static final _keyTag = RegExp(r'^#EXT-X-KEY:([^\r\n]*)', multiLine: true);
  static final _aes128 = RegExp(r'(?:^|,)METHOD=AES-128(?:,|$)');
  static final _keyFormat = RegExp(r'(?:^|,)KEYFORMAT="([^"]+)"');
  static final _keyUri = RegExp(r'(?:^|,)URI="([^"]+)"');

  static Future<(Uri, Uint8List)> _fetch(
    HttpClient client,
    Uri uri,
    Map<String, String> headers, {
    required bool playlist,
  }) async {
    if (!const {'http', 'https'}.contains(uri.scheme)) {
      throw const FormatException('Unsupported stream protocol');
    }
    final request = await client.getUrl(uri);
    headers.forEach(request.headers.set);
    final response = await request.close();
    if (response.statusCode < 200 ||
        response.statusCode >= 300 ||
        response.headers.contentType?.mimeType == 'text/html') {
      throw const FormatException('Stream request failed');
    }
    final base = response.redirects.fold(
      uri,
      (url, redirect) => url.resolveUri(redirect.location),
    );
    final limit = playlist ? 65536 : 17;
    final bytes = BytesBuilder(copy: false);
    await for (final chunk in response) {
      final remaining = limit - bytes.length;
      bytes.add(chunk.length > remaining ? chunk.sublist(0, remaining) : chunk);
      if (bytes.length == limit) break;
      if (playlist &&
          bytes.length >= 16 &&
          !utf8
              .decode(bytes.toBytes().take(16).toList(), allowMalformed: true)
              .trimLeft()
              .startsWith('#EXTM3U')) {
        break;
      }
    }
    if (bytes.isEmpty) throw const FormatException('Empty stream response');
    final data = bytes.takeBytes();
    final prefix = utf8
        .decode(data.take(16).toList(), allowMalformed: true)
        .trimLeft()
        .toLowerCase();
    if (prefix.startsWith('<html') || prefix.startsWith('<!doctype html')) {
      throw const FormatException('HTML response');
    }
    return (base, data);
  }

  Future<void> probeAll(
    List<Channel> channels, {
    String? defaultUserAgent,
    void Function(Channel channel)? onChannelUpdated,
  }) async {
    final generation = _generation;
    final urls = <String>{
      for (final channel in channels)
        for (final variant in channel.variants) variant.url,
    };
    if (generation == _generation) {
      _health.removeWhere((url, _) => !urls.contains(url));
      _recordedAt.removeWhere((url, _) => !urls.contains(url));
    }
    var index = 0;
    Future<void> worker() async {
      while (generation == _generation && index < channels.length) {
        final channel = channels[index++];
        for (final variant in channel.variants) {
          if (generation != _generation) return;
          await checkVariant(variant, defaultUserAgent: defaultUserAgent);
        }
        if (generation == _generation) onChannelUpdated?.call(channel);
      }
    }

    await Future.wait(List.generate(12, (_) => worker()));
  }
}
