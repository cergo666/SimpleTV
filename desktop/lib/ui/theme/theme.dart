import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// Пресет акцента. Id сохраняется в настройках и совпадает с Android-клиентом.
class AccentOption {
  final String id;
  final Color accent;
  final Color accentSoft;
  final Color gradientEnd;
  final Color onAccent;

  const AccentOption({
    required this.id,
    required this.accent,
    required this.accentSoft,
    required this.gradientEnd,
    required this.onAccent,
  });
}

const accentOptions = <AccentOption>[
  AccentOption(
    id: 'violet',
    accent: Color(0xFF5B4FE8),
    accentSoft: Color(0xFF9D91FF),
    gradientEnd: Color(0xFF6D5DF6),
    onAccent: Colors.white,
  ),
  AccentOption(
    id: 'orange',
    accent: Color(0xFFFF6B3D),
    accentSoft: Color(0xFFFFB347),
    gradientEnd: Color(0xFFFFB347),
    onAccent: Color(0xFF1A0B04),
  ),
  AccentOption(
    id: 'blue',
    accent: Color(0xFF2563EB),
    accentSoft: Color(0xFF60A5FA),
    gradientEnd: Color(0xFF1D4ED8),
    onAccent: Colors.white,
  ),
  AccentOption(
    id: 'teal',
    accent: Color(0xFF0F766E),
    accentSoft: Color(0xFF5EEAD4),
    gradientEnd: Color(0xFF115E59),
    onAccent: Colors.white,
  ),
  AccentOption(
    id: 'pink',
    accent: Color(0xFFDB2777),
    accentSoft: Color(0xFFF9A8D4),
    gradientEnd: Color(0xFFBE185D),
    onAccent: Colors.white,
  ),
  AccentOption(
    id: 'red',
    accent: Color(0xFFDC2626),
    accentSoft: Color(0xFFFCA5A5),
    gradientEnd: Color(0xFFB91C1C),
    onAccent: Colors.white,
  ),
];

AccentOption accentById(String? id) => accentOptions.firstWhere(
  (option) => option.id == id,
  orElse: () => accentOptions.first,
);

class AppThemeController {
  static final ValueNotifier<AccentOption> notifier = ValueNotifier(
    accentOptions.first,
  );

  static AccentOption get current => notifier.value;

  static void apply(String? id) {
    notifier.value = accentById(id);
  }
}

class AppColors {
  static const Color background = Color(0xFF080C14);
  static const Color surface = Color(0xFF111726);
  static const Color surfaceLight = Color(0xFF1A2238);
  static const Color surfaceHover = Color(0xFF242E4A);

  /// Единственный интерактивный акцент. Значение берётся из AppThemeController.
  static Color get accent => AppThemeController.current.accent;
  static Color get accentLight => AppThemeController.current.accentSoft;
  static Color get gradientEnd => AppThemeController.current.gradientEnd;

  /// Текст и иконки на акцентной заливке. Для каждого пресета свой контраст.
  static Color get onAccent => AppThemeController.current.onAccent;

  static const Color text = Color(0xFFF8FAFC);
  static const Color textSecondary = Color(0xFF94A3B8);
  static const Color textMuted = Color(0xFF7E8CA3);
  static const Color overlayBackground = Color(0xF2080C14);
  static const Color red = Color(0xFFEF4444);
  static const Color green = Color(0xFF10B981);
  static const Color border = Color(0xFF1E293B);
}

/// Одна шкала радиусов: 4 / 8 / 12 / 16.
class AppRadius {
  static const double chip = 4;
  static const double control = 8;
  static const double panel = 12;
  static const double card = 16;
}

/// Четыре размера текста; 48 — только крупные цифры ввода.
class AppType {
  static const double caption = 12;
  static const double body = 14;
  static const double subtitle = 16;
  static const double title = 20;
  static const double hero = 48;
}

/// Сетка отступов 4 / 8 / 12 / 16 / 24 / 32.
class AppSpace {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Шрифт выбирается по платформе: одного универсального шрифта,
/// который есть и в Windows, и в Linux, нет.
String? _platformFontFamily() {
  return switch (defaultTargetPlatform) {
    TargetPlatform.windows => 'Segoe UI',
    TargetPlatform.linux => 'Ubuntu',
    _ => null,
  };
}

ThemeData buildAppTheme() {
  return ThemeData(
    brightness: Brightness.dark,
    scaffoldBackgroundColor: AppColors.background,
    primaryColor: AppColors.accent,
    colorScheme: ColorScheme.dark(
      primary: AppColors.accent,
      onPrimary: AppColors.onAccent,
      surface: AppColors.surface,
      onSurface: AppColors.text,
    ),
    fontFamily: _platformFontFamily(),
    fontFamilyFallback: const [
      'Segoe UI',
      'Ubuntu',
      'Cantarell',
      'Noto Sans',
      'DejaVu Sans',
    ],
    textTheme: const TextTheme(
      bodyLarge: TextStyle(color: AppColors.text, fontSize: AppType.subtitle),
      bodyMedium: TextStyle(color: AppColors.text, fontSize: AppType.body),
      bodySmall: TextStyle(
        color: AppColors.textSecondary,
        fontSize: AppType.caption,
      ),
      titleLarge: TextStyle(
        color: AppColors.text,
        fontSize: AppType.title,
        fontWeight: FontWeight.bold,
      ),
      titleMedium: TextStyle(
        color: AppColors.text,
        fontSize: AppType.subtitle,
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}
