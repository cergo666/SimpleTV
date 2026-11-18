import 'dart:io';

import 'package:simpletv/data/services/packaged_playlist.dart';

void main(List<String> args) {
  final source = Directory.fromUri(
    Platform.script.resolve('../assets/playlists/'),
  );
  final assets = Directory(args.single).absolute.uri;
  for (final file in source.listSync().whereType<File>()) {
    final name = file.uri.pathSegments.last;
    if (!name.endsWith('.m3u')) continue;
    final asset = File.fromUri(assets.resolve('assets/playlists/$name'));
    if (!asset.existsSync()) {
      throw StateError('Missing Flutter playlist asset ${asset.path}');
    }
    asset.writeAsBytesSync(packPlaylist(file.readAsBytesSync()), flush: true);
  }
}
