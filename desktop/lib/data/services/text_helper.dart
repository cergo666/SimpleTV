class TextHelper {
  static const Map<String, String> superscript = {
    'ᴴ': 'h',
    'ᴰ': 'd',
    'ᴼ': 'o',
    'ᴿ': 'r',
    'ᴵ': 'i',
    'ᴳ': 'g',
    'ᵁ': 'u',
    'ᴷ': 'k',
    'ᴬ': 'a',
    'ᴮ': 'b',
    'ᴱ': 'e',
    'ᴹ': 'm',
    'ᴺ': 'n',
    'ᴾ': 'p',
    'ᵀ': 't',
    'ᵂ': 'w',
    '⁺': '+',
    '⁴': '4',
  };

  /// Унифицированный ключ канала для дедупликации и объединения источников.
  /// "Первый канал ᴴᴰ" -> "первый канал hd"
  /// "Первый канал HD" -> "первый канал hd"
  /// "ТВ-3" -> "тв 3"
  /// "ТВ3" -> "тв 3"
  /// ".black ᴴᴰ" -> "black hd"
  static String channelKey(String name) {
    final sb = StringBuffer();
    for (var i = 0; i < name.length; i++) {
      final char = name[i];
      final sup = superscript[char];
      if (sup != null) {
        sb.write(sup);
      } else {
        final lower = char.toLowerCase();
        if (lower == 'ё') {
          sb.write('е');
        } else if (RegExp(r'[a-zA-Z0-9а-яА-Я+]').hasMatch(char)) {
          sb.write(lower);
        } else {
          sb.write(' ');
        }
      }
    }
    var str = sb.toString();
    // "ᴴᴰ" даёт "hd" вплотную к имени — отделяем, если слиплось
    str = str.replaceAllMapped(
      RegExp(r'([^ ])(hd|fhd|uhd)$'),
      (m) => '${m[1]} ${m[2]}',
    );
    // Разделяем буквы и цифры пробелом: "тв3" -> "тв 3", "24тв" -> "24 тв"
    str = str.replaceAllMapped(
      RegExp(r'([а-яa-z])(\d)'),
      (m) => '${m[1]} ${m[2]}',
    );
    str = str.replaceAllMapped(
      RegExp(r'(\d)([а-яa-z])'),
      (m) => '${m[1]} ${m[2]}',
    );
    return str.replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  /// Грубый ключ для EPG и поиска базовой версии канала (без HD/UHD/4K/OTT/SD).
  static String looseKey(String name) {
    return channelKey(name)
        .replaceAll(
          RegExp(r'\b(hd|fhd|uhd|4k|orig|ott|отт|sd|50|hevc|h265)\b'),
          ' ',
        )
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
  }
}
