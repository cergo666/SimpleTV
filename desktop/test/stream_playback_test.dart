import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:media_kit/media_kit.dart';

void main() {
  final override = Platform.environment['SIMPLETV_TEST_LIBMPV'];
  final library =
      override ??
      (Platform.isWindows
          ? r'build\windows\x64\runner\Release\libmpv-2.dll'
          : null);
  final hasLibrary = library != null && File(library).existsSync();

  test(
    'mpv decodes a video stream served over local HTTP',
    () async {
      expect(hasLibrary, isTrue, reason: 'Missing libmpv: $library');
      MediaKit.ensureInitialized(libmpv: File(library!).absolute.path);
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));

      const width = 64;
      const height = 48;
      final frame = List<int>.filled(width * height * 3 ~/ 2, 128);
      final video = [
        ...ascii.encode('YUV4MPEG2 W64 H48 F25:1 Ip A1:1 C420jpeg\n'),
        for (var index = 0; index < 100; index++) ...[
          ...ascii.encode('FRAME\n'),
          ...frame,
        ],
      ];
      server.listen((request) async {
        request.response.contentLength = video.length;
        request.response.add(video);
        await request.response.close();
      });

      final player = Player();
      addTearDown(player.dispose);
      final platform = player.platform as NativePlayer;
      await platform.setProperty('vo', 'null');
      await platform.setProperty('vid', 'auto');
      await player.open(Media('http://127.0.0.1:${server.port}/sample.y4m'));
      if (player.state.position == Duration.zero) {
        await player.stream.position
            .firstWhere((position) => position > Duration.zero)
            .timeout(const Duration(seconds: 10));
      }
      if (player.state.width == null) {
        await player.stream.width
            .firstWhere((value) => value != null)
            .timeout(const Duration(seconds: 10));
      }
      expect(player.state.width, width);
      expect(player.state.height, height);
      expect(
        await platform.getProperty('current-tracks/video/codec'),
        'rawvideo',
      );
      expect(double.parse(await platform.getProperty('container-fps')), 25);
    },
    tags: 'native-playback',
    skip: override == null && !Platform.isWindows
        ? 'Set SIMPLETV_TEST_LIBMPV to run on this platform.'
        : false,
  );
}
