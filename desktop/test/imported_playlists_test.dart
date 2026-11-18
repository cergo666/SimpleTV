import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/ui/views/main_screen.dart';

void main() {
  test('cleanup keeps the oldest playlist referenced by settings', () async {
    final directory = await Directory.systemTemp.createTemp('simpletv-import');
    addTearDown(() => directory.delete(recursive: true));
    final files = <File>[];
    for (var i = 0; i < 52; i++) {
      final file = File.fromUri(directory.uri.resolve('$i.m3u'));
      await file.writeAsString('#EXTM3U');
      await file.setLastModified(DateTime.utc(2026, 1, 1, 0, i));
      files.add(file);
    }
    await pruneImportedPlaylists(directory, {files.first.uri.toString()});
    expect(await files.first.exists(), isTrue);
    expect(await files[1].exists(), isFalse);
    expect(await directory.list().length, 51);
  });
}
