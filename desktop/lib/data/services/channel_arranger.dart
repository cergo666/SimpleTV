import '../models/channel.dart';
import 'text_helper.dart';

class ArrangerOptions {
  final bool hideSdDuplicates;
  final bool hideTimezones;
  final bool standardOrder;
  final bool mergeOtt;
  final bool hideAdult;
  final bool preferMaxQuality;

  const ArrangerOptions({
    this.hideSdDuplicates = true,
    this.hideTimezones = true,
    this.standardOrder = true,
    this.mergeOtt = true,
    this.hideAdult = true,
    this.preferMaxQuality = true,
  });
}

class ChannelArranger {
  static final List<List<String>> _standard = [
    ['первый канал', 'первый', '1 канал'],
    ['россия 1', 'россия'],
    ['матч тв', 'матч', 'матч!'],
    ['нтв'],
    ['пятый канал', '5 канал', 'петербург 5 канал'],
    ['россия к', 'россия культура', 'культура'],
    ['россия 24'],
    ['карусель'],
    ['отр'],
    ['тв центр', 'твц', 'тв центр москва'],
    ['рен тв', 'рен'],
    ['спас'],
    ['стс'],
    ['домашний'],
    ['тв 3', 'тв3'],
    ['пятница', 'пятница!'],
    ['звезда'],
    ['мир'],
    ['тнт'],
    ['муз тв'],
  ].map((list) => list.map((a) => _looseKey(a)).toList()).toList();

  static final _tzRegex = RegExp(
    r'(\s|\()[+−\-]\s?\d{1,2}(\s?ч\.?)?\)?(\s|$)|'
    r'\(\s*(восток|орбита|дальний восток|сибирь|урал)\s*\)|'
    r'\bорбита\b|\butc\s?[+−\-]\s?\d',
    caseSensitive: false,
  );

  static final _ottRegex = RegExp(r'\b(ott|отт)\b', caseSensitive: false);
  static final _ottMarkerRegex = RegExp(
    r'\s*[(\[]?\s*(ott|отт)\s*[)\]]?',
    caseSensitive: false,
  );

  static final _adultRegex = RegExp(
    r'\b(18\+|xxx|brazzers|hustler|babes|playboy|exxxotica|dorcel|penthouse|candy|erox|erotika|erotic|эротика|эрокс|шалун|flirt)\b|'
    r'русская\s*ночь|ночной\s*клуб|red\s*lips|blue\s*hustler|barely\s*legal',
    caseSensitive: false,
  );

  static final _adultGroupRegex = RegExp(
    r'взросл|18\+|xxx|эротик|adult|ночн|night|секс|porn|порно',
    caseSensitive: false,
  );

  static bool isAdult(String name, String? group) {
    if (group != null && _adultGroupRegex.hasMatch(group)) return true;
    return _adultRegex.hasMatch(name);
  }

  static bool isOtherTimezone(String name) => _tzRegex.hasMatch(name);

  static bool isUhd(String name) {
    final k = _channelKey(name);
    return RegExp(r'\b(4k|uhd|2160p?|8k)\b').hasMatch(k);
  }

  static bool isHd(String name) {
    final k = _channelKey(name);
    return isUhd(name) || RegExp(r'\b(hd|fhd|1080p?|720p?)\b').hasMatch(k);
  }

  static bool isOtt(String name) => _ottRegex.hasMatch(name);

  static String cleanOtt(String name) => name
      .replaceAll(_ottMarkerRegex, ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();

  static String _channelKey(String name) => TextHelper.channelKey(name);

  static String _looseKey(String name) => TextHelper.looseKey(name);

  static int _variantScore(StreamVariant v) {
    final u = v.url.toLowerCase();
    final s = v.sourceName.toLowerCase();
    var score = 100;
    if (u.contains('4k') ||
        u.contains('uhd') ||
        u.contains('2160') ||
        s.contains('4k') ||
        s.contains('uhd')) {
      score = 400;
    } else if (u.contains('1080') ||
        u.contains('fhd') ||
        s.contains('1080') ||
        s.contains('fhd')) {
      score = 300;
    } else if (u.contains('720') || u.contains('hd') || s.contains('hd')) {
      score = 200;
    }
    return score;
  }

  static int _sourcePriority(StreamVariant variant) {
    // Приоритет по kind, а не по имени: переименование источника не должно
    // менять порядок вариантов и ломать Beeline-архив.
    if (variant.kind == StreamKind.beeline) return 0;
    final source = variant.sourceName.toLowerCase();
    final isWink =
        variant.kind == StreamKind.wink ||
        source == 'wink tv' ||
        source == 'wink tv (ott)';
    if (isWink) {
      return variant.isOtt || source.endsWith(' (ott)') ? 1 : 2;
    }
    return 3;
  }

  static final _sdPath = RegExp(
    r'/(?:ch_[^/]*sd|sd)(?:/|$)',
    caseSensitive: false,
  );

  static bool _isExplicitSd(StreamVariant variant) =>
      _sdPath.hasMatch(Uri.tryParse(variant.url)?.path ?? '');

  static List<Channel> arrange(List<Channel> list, ArrangerOptions options) {
    var out = list;
    if (options.hideAdult) {
      out = out.where((c) => !isAdult(c.name, c.group)).toList();
    }
    if (options.hideTimezones) {
      out = out.where((c) => !isOtherTimezone(c.name)).toList();
    }
    if (options.mergeOtt) {
      out = _mergeOtt(out);
    }
    if (options.hideSdDuplicates) {
      out = _hideSd(out);
    }
    if (options.standardOrder) {
      out = _order(out, options.preferMaxQuality);
    }

    out = out.map((channel) {
      var variants = channel.variants;
      if (options.hideSdDuplicates && isHd(channel.name)) {
        final nonSd = variants
            .where((variant) => !_isExplicitSd(variant))
            .toList();
        if (nonSd.isNotEmpty) variants = nonSd;
      }
      final ordered = variants.indexed.toList()
        ..sort((a, b) {
          final priority = _sourcePriority(a.$2)
              .compareTo(_sourcePriority(b.$2));
          if (priority != 0) return priority;
          final quality = options.preferMaxQuality
              ? _variantScore(b.$2).compareTo(_variantScore(a.$2))
              : 0;
          return quality != 0 ? quality : a.$1.compareTo(b.$1);
        });
      return channel.copyWith(
        variants: ordered.map((entry) => entry.$2).toList(),
      );
    }).toList();

    return out.asMap().entries.map((e) {
      return e.value.copyWith(number: e.key + 1);
    }).toList();
  }

  static List<Channel> _mergeOtt(List<Channel> list) {
    final nonOttByKey = <String, Channel>{};
    for (final ch in list) {
      if (!isOtt(ch.name)) {
        nonOttByKey[_channelKey(ch.name)] = ch;
      }
    }
    if (nonOttByKey.isEmpty) return list;

    final absorbed = <String>{};
    final extra = <String, List<StreamVariant>>{};

    for (final ch in list) {
      if (!isOtt(ch.name)) continue;
      final cleanName = cleanOtt(ch.name);
      final baseKey = _channelKey(cleanName);
      final target = nonOttByKey[baseKey];
      if (target == null) continue;

      absorbed.add(ch.key);
      extra
          .putIfAbsent(target.key, () => [])
          .addAll(
            ch.variants.map(
              (v) => StreamVariant(
                url: v.url,
                sourceName: '${v.sourceName} (OTT)',
                kind: v.kind,
                userAgent: v.userAgent,
                referer: v.referer,
                clearKey: v.clearKey,
                catchup: v.catchup,
                catchupDays: v.catchupDays,
                isOtt: true,
              ),
            ),
          );
    }

    return list.where((ch) => !absorbed.contains(ch.key)).map((ch) {
      final additional = extra[ch.key];
      if (additional == null || additional.isEmpty) return ch;
      return ch.copyWith(variants: [...ch.variants, ...additional]);
    }).toList();
  }

  static List<Channel> _hideSd(List<Channel> list) {
    final byLoose = <String, List<Channel>>{};
    for (final ch in list) {
      byLoose.putIfAbsent(_looseKey(ch.name), () => []).add(ch);
    }

    final absorbed = <String>{};
    final extra = <String, List<StreamVariant>>{};

    for (final group in byLoose.values) {
      if (group.length < 2) continue;
      final hd = group.where((c) => isHd(c.name)).toList();
      if (hd.isEmpty) continue;
      final best = hd.firstWhere((c) => !isUhd(c.name), orElse: () => hd.first);
      final sd = group.where((c) => !isHd(c.name)).toList();
      if (sd.isEmpty) continue;

      for (final s in sd) {
        absorbed.add(s.key);
        extra.putIfAbsent(best.key, () => []).addAll(s.variants);
      }
    }

    return list.where((ch) => !absorbed.contains(ch.key)).map((ch) {
      final additional = extra[ch.key];
      if (additional == null || additional.isEmpty) return ch;
      final seen = <String>{...ch.variants.map((v) => v.url)};
      final added = <StreamVariant>[];
      for (final v in additional) {
        if (!seen.contains(v.url)) {
          seen.add(v.url);
          added.add(v);
        }
      }
      return ch.copyWith(variants: [...ch.variants, ...added]);
    }).toList();
  }

  static List<Channel> _order(List<Channel> list, bool preferMaxQuality) {
    final prioritized = <Channel>[];
    final remaining = List<Channel>.from(list);

    for (final aliases in _standard) {
      final candidateIndices = <int>[];
      for (var i = 0; i < remaining.length; i++) {
        final lk = _looseKey(remaining[i].name);
        if (aliases.contains(lk)) {
          candidateIndices.add(i);
        }
      }

      if (candidateIndices.isNotEmpty) {
        if (preferMaxQuality) {
          candidateIndices.sort((a, b) {
            final hdA = isHd(remaining[a].name) ? 1 : 0;
            final hdB = isHd(remaining[b].name) ? 1 : 0;
            return hdB.compareTo(hdA); // HD версия первой!
          });
        }
        final chosenIdx = candidateIndices.first;
        prioritized.add(remaining.removeAt(chosenIdx));
      }
    }

    return [...prioritized, ...remaining];
  }
}
