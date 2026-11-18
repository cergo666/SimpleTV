import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:crypto/crypto.dart';
import 'package:xml/xml.dart';
import 'package:xml/xml_events.dart';

import '../models/channel.dart';
import '../../l10n/l10n.dart';
import '../models/settings_model.dart';
import 'text_helper.dart';

typedef _EpgIndex = ({
  Map<String, List<Programme>> programmes,
  Map<String, String> names,
  Map<String, String> looseNames,
});

class EpgService {
  EpgService({this.cacheDirectory});

  final Directory? cacheDirectory;
  _EpgIndex? _index;
  Future<void>? _refresh;

  Programme? getCurrentProgramme(Channel? channel) {
    final now = DateTime.now();
    return getSchedule(channel)
        .where((p) => !now.isBefore(p.start) && now.isBefore(p.stop))
        .firstOrNull;
  }

  Programme? getNextProgramme(Channel? channel) {
    final now = DateTime.now();
    return getSchedule(channel).where((p) => p.start.isAfter(now)).firstOrNull;
  }

  List<Programme> getSchedule(Channel? channel) {
    final index = _index;
    if (index == null || channel == null) return const [];
    return _schedule(index, channel);
  }

  static List<Programme> _schedule(_EpgIndex index, Channel channel) {
    final id = channel.epgId?.trim().toLowerCase();
    return index.programmes[channel.key] ??
        index.programmes[id] ??
        index.programmes[index.names[TextHelper.channelKey(
          channel.epgId ?? '',
        )]] ??
        index.programmes[index.names[TextHelper.channelKey(channel.name)]] ??
        index.programmes[index.looseNames[TextHelper.looseKey(channel.name)]] ??
        const [];
  }

  Future<void> restore(
    List<EpgSourceConfig> sources,
    List<Channel> channels,
  ) async {
    final dir = cacheDirectory;
    if (dir == null) return;
    final indexes = await _readSources(dir, sources);
    if (_index == null && indexes.isNotEmpty) {
      final merged = await _mergeOffThread(indexes, channels);
      _index ??= merged;
    }
  }

  static File _snapshot(Directory dir, EpgSourceConfig source) => File.fromUri(
    dir.uri.resolve('epg-${sha256.convert(utf8.encode(source.url))}.json'),
  );

  static Future<List<_EpgIndex>> _readSources(
    Directory dir,
    List<EpgSourceConfig> sources,
  ) => Isolate.run(() async {
    final indexes = <_EpgIndex>[];
    for (final source in sources.where((source) => source.enabled)) {
      final index = await _readSnapshot(_snapshot(dir, source));
      if (index != null) indexes.add(index);
    }
    return indexes;
  });

  static Future<_EpgIndex?> _readSnapshot(File file) async {
    try {
      final json =
          jsonDecode(await file.readAsString()) as Map<String, dynamic>;
      return (
        names: Map<String, String>.from(json['names'] as Map),
        looseNames: Map<String, String>.from(json['looseNames'] as Map),
        programmes: (json['programmes'] as Map<String, dynamic>).map(
          (key, value) => MapEntry(
            key,
            (value as List)
                .map((row) => Programme.fromJson(row as Map<String, dynamic>))
                .toList(),
          ),
        ),
      );
    } catch (_) {
      return null;
    }
  }

  static Future<void> _writeSnapshot(File file, _EpgIndex index) async {
    try {
      await file.parent.create(recursive: true);
      final temp = File('${file.path}.tmp');
      await temp.writeAsString(
        jsonEncode({
          'names': index.names,
          'looseNames': index.looseNames,
          'programmes': index.programmes,
        }),
        flush: true,
      );
      await temp.rename(file.path);
    } on FileSystemException {
      // The refreshed guide remains available for this session.
    }
  }

  static _EpgIndex _merge(List<_EpgIndex> indexes, List<Channel> channels) {
    final programmes = <String, List<Programme>>{};
    for (final channel in channels) {
      final byStart = <DateTime, Programme>{};
      for (final index in indexes) {
        final existing = byStart.values.toList()
          ..sort((a, b) => a.start.compareTo(b.start));
        var cursor = 0;
        for (final item in _schedule(index, channel)) {
          // Higher-priority guides own any time range they already cover.
          while (cursor < existing.length &&
              !existing[cursor].stop.isAfter(item.start)) {
            cursor++;
          }
          if (cursor < existing.length &&
              existing[cursor].start.isBefore(item.stop)) {
            continue;
          }
          byStart.putIfAbsent(item.start, () => item);
        }
      }
      if (byStart.isNotEmpty) {
        programmes[channel.key] = byStart.values.toList()
          ..sort((a, b) => a.start.compareTo(b.start));
      }
    }
    return (programmes: programmes, names: const {}, looseNames: const {});
  }

  static Future<_EpgIndex> _mergeOffThread(
    List<_EpgIndex> indexes,
    List<Channel> channels,
  ) => Isolate.run(() => _merge(indexes, channels));

  static Future<_EpgIndex?> _readOffThread(File file) =>
      Isolate.run(() => _readSnapshot(file));

  static Future<void> _writeOffThread(File file, _EpgIndex index) =>
      Isolate.run(() => _writeSnapshot(file, index));

  Future<void> updateEpg(
    List<EpgSourceConfig> sources,
    List<Channel> channels, {
    void Function(String)? onProgress,
    Duration? maxAge,
  }) => _refresh ??= _update(
    sources,
    channels,
    onProgress,
    maxAge,
  ).whenComplete(() => _refresh = null);

  Future<void> _update(
    List<EpgSourceConfig> sources,
    List<Channel> channels,
    void Function(String)? onProgress,
    Duration? maxAge,
  ) async {
    if (channels.isEmpty) return;
    final dir = cacheDirectory;
    final indexes = <_EpgIndex>[];
    var refreshed = false;
    for (final source in sources.where((source) => source.enabled)) {
      final file = dir == null ? null : _snapshot(dir, source);
      try {
        if (maxAge != null &&
            file != null &&
            DateTime.now().difference((await file.stat()).modified) < maxAge) {
          final cached = await _readOffThread(file);
          if (cached != null) {
            indexes.add(cached);
            continue;
          }
        }
        onProgress?.call(
          L10n.t.loadingGuide(epgSourceLabel(source.id, source.name)),
        );
        final index = await _loadOffThread(source.url, channels);
        if (index.programmes.isEmpty) {
          throw const FormatException('Empty guide');
        }
        indexes.add(index);
        refreshed = true;
        if (file != null) await _writeOffThread(file, index);
      } on Exception {
        if (file != null) {
          final cached = await _readOffThread(file);
          if (cached != null) indexes.add(cached);
        }
      }
    }
    if (indexes.isNotEmpty) {
      _index = await _mergeOffThread(indexes, channels);
      if (refreshed) onProgress?.call(L10n.t.guideUpdated);
    } else if (sources.any((source) => source.enabled)) {
      onProgress?.call(L10n.t.guideUpdateFailed);
    } else {
      _index = null;
    }
  }
}

Future<_EpgIndex> _loadOffThread(String url, List<Channel> channels) =>
    Isolate.run(() => _readXmltv(url, channels));

Future<_EpgIndex> _readXmltv(String url, List<Channel> channels) async {
  final client = http.Client();
  final Uint8List bytes;
  try {
    bytes = await _download(client, url).timeout(const Duration(seconds: 90));
  } finally {
    client.close();
  }

  final wantedIds = {for (final ch in channels) ch.epgId?.trim().toLowerCase()};
  final wantedNames = {
    for (final ch in channels) TextHelper.channelKey(ch.name),
    for (final ch in channels)
      if (ch.epgId != null) TextHelper.channelKey(ch.epgId!),
  };
  final wantedLoose = {for (final ch in channels) TextHelper.looseKey(ch.name)};
  final names = <String, String>{};
  final looseNames = <String, String>{};
  final programmes = <String, List<Programme>>{};
  final matchingIds = <String>{...wantedIds.nonNulls};
  final chunks = Stream<List<int>>.fromIterable(_chunks(bytes));
  final isGzip = bytes.length >= 2 && bytes[0] == 0x1f && bytes[1] == 0x8b;
  final records = (isGzip ? chunks.transform(gzip.decoder) : chunks)
      .transform(const Utf8Decoder(allowMalformed: true))
      .toXmlEvents(validateNesting: true)
      .selectSubtreeEvents(
        (event) => event.name == 'channel' || event.name == 'programme',
      )
      .toXmlNodes();

  await for (final batch in records) {
    for (final node in batch.whereType<XmlElement>()) {
      if (node.name.local == 'channel') {
        final id = node.getAttribute('id')?.trim().toLowerCase();
        if (id == null || id.isEmpty) continue;
        for (final displayName in node.findElements('display-name')) {
          final name = TextHelper.channelKey(displayName.innerText);
          final loose = TextHelper.looseKey(displayName.innerText);
          if (wantedNames.contains(name)) {
            names.putIfAbsent(name, () => id);
            matchingIds.add(id);
          }
          if (wantedLoose.contains(loose)) {
            looseNames.putIfAbsent(loose, () => id);
            matchingIds.add(id);
          }
        }
        continue;
      }
      final id = node.getAttribute('channel')?.trim().toLowerCase();
      if (id == null || !matchingIds.contains(id)) continue;
      final title = node.findElements('title').firstOrNull?.innerText;
      final start = parseXmltvDate(node.getAttribute('start'));
      final stop = parseXmltvDate(node.getAttribute('stop'));
      if (title == null ||
          title.isEmpty ||
          start == null ||
          stop == null ||
          !stop.isAfter(start)) {
        continue;
      }
      programmes
          .putIfAbsent(id, () => [])
          .add(
            Programme(
              channelId: id,
              title: title,
              description: node.findElements('desc').firstOrNull?.innerText,
              start: start,
              stop: stop,
              category: node.findElements('category').firstOrNull?.innerText,
            ),
          );
    }
  }
  for (final list in programmes.values) {
    list.sort((a, b) => a.start.compareTo(b.start));
  }
  return (programmes: programmes, names: names, looseNames: looseNames);
}

Future<Uint8List> _download(http.Client client, String url) async {
  final request = http.Request('GET', Uri.parse(url));
  final response = await client
      .send(request)
      .timeout(const Duration(seconds: 15));
  if (response.statusCode != 200) {
    throw HttpException('XMLTV HTTP ${response.statusCode}');
  }
  final bytes = BytesBuilder(copy: false);
  await for (final chunk in response.stream.timeout(
    const Duration(seconds: 20),
  )) {
    bytes.add(chunk);
    if (bytes.length > 64 * 1024 * 1024) {
      throw const FormatException('XMLTV download is too large');
    }
  }
  return bytes.takeBytes();
}

Iterable<Uint8List> _chunks(Uint8List bytes) sync* {
  const size = 64 * 1024;
  for (var offset = 0; offset < bytes.length; offset += size) {
    final end = offset + size;
    yield Uint8List.sublistView(
      bytes,
      offset,
      end < bytes.length ? end : bytes.length,
    );
  }
}

final _xmltvDate = RegExp(
  r'^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-])(\d{2})(\d{2}))?$',
);

DateTime? parseXmltvDate(String? value) {
  final match = _xmltvDate.firstMatch(value?.trim() ?? '');
  if (match == null) return null;
  int part(int i) => int.parse(match[i]!);
  final utc = DateTime.utc(
    part(1),
    part(2),
    part(3),
    part(4),
    part(5),
    part(6),
  );
  if (match[7] == null) return utc.toLocal();
  final offset = Duration(hours: part(8), minutes: part(9));
  return (match[7] == '-' ? utc.add(offset) : utc.subtract(offset)).toLocal();
}
