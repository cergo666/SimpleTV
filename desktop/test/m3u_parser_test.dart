import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/models/settings_model.dart';
import 'package:simpletv/data/services/m3u_parser.dart';

/// Reads the same fixture as the Kotlin M3uParserTest so both parsers stay aligned.
void main() {
  const source = SourceConfig(id: 'fixture', name: 'Fixture', urls: []);

  List<ParsedSourceChannel> parseFixture() {
    final text = File('../testdata/m3u/sample.m3u').readAsStringSync();
    return M3uParser.parse(text, source, skipWebPages: false);
  }

  test('parses the shared fixture', () {
    final channels = parseFixture();
    expect(channels.map((c) => c.rawName), [
      'Channel One',
      'Movie Two',
      'Channel Three',
    ]);
    expect(channels.map((c) => c.group), ['News', 'Movies', 'Sport']);
    expect(channels.map((c) => c.epgId), ['one', null, null]);
  });

  test('keeps VLC options, pipe headers and catchup', () {
    final channels = parseFixture();

    expect(channels[0].variant.url, 'https://example.test/one.m3u8');
    expect(channels[0].variant.userAgent, 'Agent/1.0');
    expect(channels[0].variant.referer, 'https://example.test/ref');
    expect(channels[0].variant.catchupDays, 3);

    expect(channels[1].variant.url, 'https://example.test/two.m3u8');
    expect(channels[1].variant.userAgent, 'PipeAgent');
    expect(channels[1].variant.referer, 'https://example.test/pipe');
    expect(channels[1].variant.catchupDays, 0);

    expect(channels[2].variant.userAgent, isNull);
  });
}
