import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/models/channel.dart';
import 'package:simpletv/data/services/stream_checker_service.dart';

void main() {
  test('HLS probe follows relative playlists and checks the AES key', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    var keyAccessible = false;
    server.listen((request) async {
      final response = request.response;
      switch (request.uri.path) {
        case '/start':
          response.statusCode = HttpStatus.found;
          response.headers.set(HttpHeaders.locationHeader, '/hls/master');
        case '/hls/master':
          response.write(
            '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\nmedia\n',
          );
        case '/hls/media':
          response.write(
            '#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key"\n'
            '#EXTINF:6,\nsegment.ts\n',
          );
        case '/hls/key':
          if (keyAccessible &&
              request.headers.value(HttpHeaders.userAgentHeader) ==
                  'SimpleTV test') {
            response.add(List.filled(16, 1));
          } else {
            response.statusCode = HttpStatus.forbidden;
          }
        default:
          response.statusCode = HttpStatus.notFound;
      }
      await response.close();
    });

    final url = 'http://127.0.0.1:${server.port}/start';
    final variant = StreamVariant(
      url: url,
      sourceName: 'Test',
      userAgent: 'SimpleTV test',
    );
    final checker = StreamCheckerService();
    await checker.checkVariant(variant);
    expect(checker.status(url), StreamHealth.unavailable);

    keyAccessible = true;
    final retry = StreamCheckerService();
    await retry.checkVariant(variant);
    expect(retry.status(url), StreamHealth.reachable);
    retry.recordPlayback(url, success: true);
    expect(retry.status(url), StreamHealth.playing);
  });

  test('expired health is re-probed before anyone reads status()', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    server.listen((request) async {
      request.response.write('not a playlist');
      await request.response.close();
    });

    var now = DateTime(2026, 1, 1);
    final checker = StreamCheckerService(clock: () => now);
    final variant = StreamVariant(
      url: 'http://127.0.0.1:${server.port}/stream',
      sourceName: 'Test',
    );

    checker.recordPlayback(variant.url, success: false);
    expect(checker.status(variant.url), StreamHealth.unavailable);

    now = now.add(const Duration(minutes: 6));
    await checker.checkVariant(variant);
    expect(checker.status(variant.url), StreamHealth.reachable);
  });

  test('probeAll forgets health for variants that left the playlist', () async {
    final checker = StreamCheckerService(clock: () => DateTime(2026, 1, 1));
    const oldUrl = 'http://127.0.0.1:1/old.m3u8';
    const currentUrl = 'http://127.0.0.1:1/current.m3u8';
    checker.recordPlayback(oldUrl, success: true);
    checker.recordPlayback(currentUrl, success: true);

    await checker.probeAll([
      const Channel(
        key: 'current',
        name: 'Current',
        group: 'Test',
        variants: [StreamVariant(url: currentUrl, sourceName: 'Test')],
      ),
    ]);

    expect(checker.status(oldUrl), isNull);
    expect(checker.status(currentUrl), StreamHealth.playing);
  });
}
