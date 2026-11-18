import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/l10n/l10n.dart';

/// gen-l10n silently falls back to the template for missing keys; this test
/// makes a forgotten English translation fail CI instead.
void main() {
  Set<String> messageKeys(String path) {
    final json =
        jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;
    return json.keys.where((key) => !key.startsWith('@')).toSet();
  }

  test('Russian and English ARB files contain the same messages', () {
    final ru = messageKeys('lib/l10n/app_ru.arb');
    final en = messageKeys('lib/l10n/app_en.arb');

    expect(en.difference(ru), isEmpty, reason: 'Only in English');
    expect(ru.difference(en), isEmpty, reason: 'Only in Russian');
  });

  test('channel count uses Russian plural forms including 21', () {
    expect(L10n.t.channelsCount(1), '1 канал');
    expect(L10n.t.channelsCount(2), '2 канала');
    expect(L10n.t.channelsCount(5), '5 каналов');
    expect(L10n.t.channelsCount(11), '11 каналов');
    expect(L10n.t.channelsCount(21), '21 канал');
    expect(L10n.t.channelsCount(22), '22 канала');
  });
}
