import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/services/stream_resolver.dart';

void main() {
  test('DASH segments use the final manifest base after redirects', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    final requests = <String?>[];
    server.listen((request) async {
      requests.add(request.headers.value(HttpHeaders.userAgentHeader));
      switch (request.uri.path) {
        case '/live/index.mpd':
          await request.response.redirect(
            Uri.parse('/cdn/session/manifest?x=1'),
          );
        case '/cdn/session/manifest':
          request.response.write('<MPD/>');
          await request.response.close();
        default:
          request.response.statusCode = HttpStatus.notFound;
          await request.response.close();
      }
    });
    final base = Uri.http('127.0.0.1:${server.port}');
    final manifest = base.resolve('/live/index.mpd');
    const headers = {'User-Agent': 'SimpleTV redirect test'};
    final result = await StreamResolver.followManifestRedirects(
      manifest,
      headers,
    );
    expect(result, base.resolve('/cdn/session/manifest?x=1'));
    expect(
      result.resolve('segment.m4s'),
      base.resolve('/cdn/session/segment.m4s'),
    );
    expect(requests, ['SimpleTV redirect test', 'SimpleTV redirect test']);

    final missing = base.resolve('/missing.mpd');
    expect(
      await StreamResolver.followManifestRedirects(missing, headers),
      missing,
    );
  });
}
