import 'dart:io';
import 'dart:typed_data';

// This format keeps bundled playlists out of plain-text asset scans.
// It provides no secrecy once the client decodes the playlist.
const _format = [0x53, 0x54, 0x56, 1];
final _codec = ZLibCodec(raw: true, level: 9);

List<int> packPlaylist(List<int> bytes) => [
  ..._format,
  ..._codec.encode(bytes),
];

List<int> unpackPlaylist(Uint8List bytes) {
  if (bytes.length < _format.length) return bytes;
  for (var i = 0; i < _format.length; i++) {
    if (bytes[i] != _format[i]) return bytes;
  }
  return _codec.decode(Uint8List.sublistView(bytes, _format.length));
}
