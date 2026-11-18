import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/models/channel.dart';
import 'package:simpletv/data/models/settings_model.dart';
import 'package:simpletv/data/services/epg_service.dart';

void main() {
  test(
    'XMLTV falls back by display name and replaces the last guide',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      var title = 'Первая передача';
      server.listen((request) async {
        if (request.uri.path == '/empty') {
          request.response.write('<tv/>');
        } else {
          request.response.add(
            gzip.encode(
              utf8.encode('''
<tv>
  <channel id="pervy"><display-name>Первый канал</display-name></channel>
  <programme start="20261108190000 +0300" stop="20261108200000 +0300" channel="pervy">
    <title>$title</title>
  </programme>
</tv>
'''),
            ),
          );
        }
        await request.response.close();
      });

      final service = EpgService();
      const channel = Channel(
        key: 'первый канал hd',
        name: 'Первый канал HD',
        group: '',
        epgId: 'provider-specific-id',
        variants: [],
      );
      final url = 'http://127.0.0.1:${server.port}';
      final empty = EpgSourceConfig(
        id: 'empty',
        name: 'Empty',
        url: '$url/empty',
      );
      final guide = EpgSourceConfig(
        id: 'guide',
        name: 'Guide',
        url: '$url/guide',
      );

      await service.updateEpg([empty, guide], [channel]);
      final programme = service.getSchedule(channel).single;
      expect(programme.title, title);
      expect(programme.start.toUtc(), DateTime.utc(2026, 11, 8, 16));
      title = 'Обновлённая передача';
      await service.updateEpg([guide], [channel]);
      expect(service.getSchedule(channel).single.title, title);
      await service.updateEpg([empty], [channel]);
      expect(service.getSchedule(channel).single.title, title);
    },
  );

  test('XMLTV merges source gaps and restores the same guide after restart', () async {
    final cache = await Directory.systemTemp.createTemp('simpletv-epg-test');
    addTearDown(() => cache.delete(recursive: true));
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    String programme(String start, String stop, String title, String id) =>
        '<programme start="20261109${start}00 +0000" stop="20261109${stop}00 +0000" channel="$id">'
        '<title>$title</title></programme>';
    server.listen((request) async {
      final primary = request.uri.path == '/primary';
      final id = primary ? 'one' : 'two';
      request.response.write(
        '<tv><channel id="$id"><display-name>Канал</display-name></channel>'
        '${programme("1200", "1230", primary ? "Основная" : "Дубль", id)}'
        '${primary ? "" : programme("1230", "1300", "Дополнение", id)}</tv>',
      );
      await request.response.close();
    });
    const channel = Channel(
      key: 'канал',
      name: 'Канал',
      group: '',
      variants: [],
    );
    final sources = [
      for (final id in ['primary', 'secondary'])
        EpgSourceConfig(
          id: id,
          name: id,
          url: 'http://127.0.0.1:${server.port}/$id',
        ),
    ];
    final service = EpgService(cacheDirectory: cache);
    await service.updateEpg(sources, [channel]);
    expect(service.getSchedule(channel).map((p) => p.title), [
      'Основная',
      'Дополнение',
    ]);

    final restarted = EpgService(cacheDirectory: cache);
    await restarted.restore(sources, [channel]);
    expect(restarted.getSchedule(channel).map((p) => p.title), [
      'Основная',
      'Дополнение',
    ]);
  });
}
