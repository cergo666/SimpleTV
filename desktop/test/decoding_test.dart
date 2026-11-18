import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/repositories/playlist_repository.dart';
import 'package:simpletv/data/services/epg_service.dart';

void main() {
  group('parseXmltvDate', () {
    test('applies a positive offset east of UTC', () {
      final dt = parseXmltvDate('20261108190000 +0300');
      expect(dt!.isUtc, isFalse);
      expect(dt.toUtc(), DateTime.utc(2026, 11, 8, 16));
    });

    test('applies a negative offset west of UTC', () {
      final dt = parseXmltvDate('20261108190000 -0500');
      expect(dt!.toUtc(), DateTime.utc(2026, 11, 9, 0));
    });

    test('localises dates without an offset', () {
      final dt = parseXmltvDate('20261108190000');
      expect(dt!.toUtc(), DateTime.utc(2026, 11, 8, 19));
    });

    test('rejects malformed values', () {
      expect(parseXmltvDate(null), isNull);
      expect(parseXmltvDate(''), isNull);
      expect(parseXmltvDate('2026110819'), isNull);
      expect(parseXmltvDate('not-a-date'), isNull);
    });

    test('accepts surrounding whitespace', () {
      expect(parseXmltvDate('  20261108190000 +0300 '), isNotNull);
    });
  });

  group('PlaylistRepository.decodePlaylistBytes', () {
    test('keeps UTF-8 text intact', () {
      const text = '#EXTM3U\n#EXTINF:-1,Первый канал';
      expect(PlaylistRepository.decodePlaylistBytes(utf8.encode(text)), text);
    });

    test('strips a UTF-8 BOM', () {
      const text = '#EXTM3U\n#EXTINF:-1,Первый канал';
      final bytes = <int>[0xEF, 0xBB, 0xBF, ...utf8.encode(text)];
      expect(PlaylistRepository.decodePlaylistBytes(bytes), text);
    });

    test('falls back to windows-1251 when UTF-8 decoding breaks', () {
      const text = '#EXTINF:-1,Каналы игры ТВ';
      final win1251 = text.codeUnits.map((unit) {
        if (unit < 128) return unit;
        // Раскладка windows-1251: Ё=0xA8, ё=0xB8, А..я = 0xC0..0xFF
        const table =
            'АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдежзийклмнопрстуфхцчшщъыьэюя';
        final index = table.indexOf(String.fromCharCode(unit));
        return index >= 0 ? 0xC0 + index : unit;
      }).toList();
      expect(PlaylistRepository.decodePlaylistBytes(win1251), text);
    });
  });
}
