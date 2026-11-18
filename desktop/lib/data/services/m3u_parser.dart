import '../models/channel.dart';
import '../models/settings_model.dart';
import 'logo_service.dart';
import 'stream_resolver.dart';
import 'public_tv.dart';
import 'text_helper.dart';

class ParsedSourceChannel {
  final String rawName;
  final String normalizedName;
  final String group;
  final String? logoUrl;
  final String? epgId;
  final StreamVariant variant;
  final int sourceSort;

  const ParsedSourceChannel({
    required this.rawName,
    required this.normalizedName,
    required this.group,
    this.logoUrl,
    this.epgId,
    required this.variant,
    this.sourceSort = 50,
  });
  Map<String, dynamic> toJson() => {
    'name': rawName,
    'key': normalizedName,
    'group': group,
    'logoUrl': logoUrl,
    'epgId': epgId,
    'variant': variant.toJson(),
    'sourceSort': sourceSort,
  };

  factory ParsedSourceChannel.fromJson(Map<String, dynamic> json) =>
      ParsedSourceChannel(
        rawName: json['name'] as String,
        normalizedName: json['key'] as String,
        group: json['group'] as String,
        logoUrl: json['logoUrl'] as String?,
        epgId: json['epgId'] as String?,
        variant: StreamVariant.fromJson(
          json['variant'] as Map<String, dynamic>,
        ),
        sourceSort: json['sourceSort'] as int,
      );
}

class M3uParser {
  static final _attrRegex = RegExp(r'([A-Za-z0-9_\-]+)\s*=\s*"([^"]*)"');

  static List<ParsedSourceChannel> parse(
    String text,
    SourceConfig source, {
    bool skipWebPages = true,
  }) {
    final out = <ParsedSourceChannel>[];
    Map<String, String> header = {};

    String? extinf;
    String? group;
    String? vlcUa;
    String? vlcRef;

    final lines = text.split(RegExp(r'\r?\n'));
    for (final raw in lines) {
      final line = raw.trim();
      if (line.isEmpty) continue;

      if (line.startsWith('#EXTM3U')) {
        header = _attrs(line);
      } else if (line.startsWith('#EXTINF')) {
        extinf = line;
        group = null;
        vlcUa = null;
        vlcRef = null;
      } else if (line.startsWith('#EXTGRP:')) {
        group = line.substring(8).trim();
      } else if (line.startsWith('#EXTVLCOPT:')) {
        final opt = line.substring(11).trim();
        if (opt.startsWith('http-user-agent=')) {
          vlcUa = opt.substring(16).trim();
        } else if (opt.startsWith('http-referrer=')) {
          vlcRef = opt.substring(14).trim();
        }
      } else if (line.startsWith('#')) {
        // ignore other comments
      } else {
        if (extinf == null) continue;
        final inf = extinf;
        extinf = null;

        final a = _attrs(_headPart(inf));
        var name = _namePart(inf);
        if (name.isEmpty) name = a['tvg-name'] ?? '';
        if (name.isEmpty) continue;

        final ch = _buildChannel(
          name: name,
          url: line,
          a: a,
          header: header,
          source: source,
          group: a['group-title'] ?? group,
          vlcUa: vlcUa,
          vlcRef: vlcRef,
          skipWebPages: skipWebPages,
        );
        if (ch != null) out.add(ch);
      }
    }

    return out;
  }

  static Map<String, String> _attrs(String s) {
    final map = <String, String>{};
    for (final m in _attrRegex.allMatches(s)) {
      map[m.group(1)!.toLowerCase()] = m.group(2)!;
    }
    return map;
  }

  static String _headPart(String inf) {
    final idx = _nameComma(inf);
    return idx < 0 ? inf : inf.substring(0, idx);
  }

  static String _namePart(String inf) {
    final idx = _nameComma(inf);
    return idx < 0 ? '' : inf.substring(idx + 1).trim();
  }

  static int _nameComma(String inf) {
    var inQuotes = false;
    for (var i = 0; i < inf.length; i++) {
      final c = inf[i];
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        return i;
      }
    }
    return -1;
  }

  static ParsedSourceChannel? _buildChannel({
    required String name,
    required String url,
    required Map<String, String> a,
    required Map<String, String> header,
    required SourceConfig source,
    String? group,
    String? vlcUa,
    String? vlcRef,
    required bool skipWebPages,
  }) {
    var address = url;
    var ua = vlcUa;
    var ref = vlcRef;

    // Kodi style: url|User-Agent=...&Referer=...
    if (address.contains('|')) {
      final pipeIdx = address.indexOf('|');
      final tail = address.substring(pipeIdx + 1);
      address = address.substring(0, pipeIdx);
      for (final kv in tail.split('&')) {
        final eq = kv.indexOf('=');
        if (eq != -1) {
          final k = kv.substring(0, eq).trim().toLowerCase();
          final rawValue = kv.substring(eq + 1).trim();
          // Одна кривая %-последовательность не должна ронять весь плейлист.
          String v;
          try {
            v = Uri.decodeComponent(rawValue);
          } catch (_) {
            v = rawValue;
          }
          if (k == 'user-agent') ua = v;
          if (k == 'referer') ref = v;
        }
      }
    }

    if (skipWebPages &&
        !PublicTv.needsResolution(address) &&
        StreamResolver.isWebPage(address)) {
      return null;
    }

    final resolved = StreamResolver.resolve(address, source.kind);
    final playUrl = resolved.url;
    if (skipWebPages &&
        !PublicTv.needsResolution(playUrl) &&
        StreamResolver.isWebPage(playUrl)) {
      return null;
    }

    final catchup =
        a['catchup-source'] ??
        a['catchup'] ??
        header['catchup-source'] ??
        header['catchup'];
    String? archiveAttr(String name) => a[name] ?? header[name];
    final catchupDays =
        double.tryParse(archiveAttr('catchup-days') ?? '') ??
        double.tryParse(archiveAttr('tvg-rec') ?? '') ??
        double.tryParse(archiveAttr('timeshift') ?? '') ??
        (double.tryParse(archiveAttr('catchup-minutes') ?? '') ?? 0) / 1440;

    final cleanName = name
        .replaceAll('ᴴᴰ', ' HD')
        .replaceAll('ᴴ', 'H')
        .replaceAll('ᴰ', 'D')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    final normalized = TextHelper.channelKey(cleanName);

    return ParsedSourceChannel(
      rawName: cleanName,
      normalizedName: normalized,
      group: group?.trim().isNotEmpty == true ? group!.trim() : 'Общие',
      logoUrl: (a['tvg-logo'] != null && a['tvg-logo']!.trim().isNotEmpty)
          ? a['tvg-logo']!.trim()
          : LogoService.findLogo(cleanName),
      epgId: a['tvg-id'] ?? a['tvg-name'],
      sourceSort: source.sort,
      variant: StreamVariant(
        url: address,
        sourceName: source.name,
        kind: resolved.kind,
        userAgent:
            ua ?? resolved.options['http-user-agent'] ?? source.streamUserAgent,
        referer: ref ?? resolved.options['http-referrer'],
        clearKey: resolved.clearKey,
        catchup: catchup,
        catchupDays: catchupDays,
        isOtt:
            name.toUpperCase().contains(' OTT') ||
            name.toUpperCase().contains(' (OTT)'),
      ),
    );
  }
}
