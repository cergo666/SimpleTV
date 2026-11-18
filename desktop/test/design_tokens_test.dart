import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/ui/theme/theme.dart';

double _luminance(int rgb) {
  double channel(int value) {
    final v = value / 255;
    return v <= 0.03928
        ? v / 12.92
        : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
  }

  final r = channel((rgb >> 16) & 0xFF);
  final g = channel((rgb >> 8) & 0xFF);
  final b = channel(rgb & 0xFF);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

double _contrast(int foreground, int background) {
  final a = _luminance(foreground);
  final b = _luminance(background);
  return (math.max(a, b) + 0.05) / (math.min(a, b) + 0.05);
}

int _rgb(int argb) => argb & 0xFFFFFF;

void main() {
  test('accent text stays readable on the brand fill', () {
    expect(
      _contrast(
        _rgb(AppColors.onAccent.toARGB32()),
        _rgb(AppColors.accent.toARGB32()),
      ),
      greaterThanOrEqualTo(4.5),
    );
  });

  test('muted text is readable on both desktop surfaces', () {
    final muted = _rgb(AppColors.textMuted.toARGB32());
    expect(
      _contrast(muted, _rgb(AppColors.background.toARGB32())),
      greaterThanOrEqualTo(4.5),
    );
    expect(
      _contrast(muted, _rgb(AppColors.surface.toARGB32())),
      greaterThanOrEqualTo(4.5),
    );
  });

  test('type and radius tokens stay on one scale', () {
    expect(
      {
        AppType.caption,
        AppType.body,
        AppType.subtitle,
        AppType.title,
        AppType.hero,
      },
      {12.0, 14.0, 16.0, 20.0, 48.0},
    );
    expect(
      {AppRadius.chip, AppRadius.control, AppRadius.panel, AppRadius.card},
      {4.0, 8.0, 12.0, 16.0},
    );
  });

  test('every accent preset keeps readable text on both gradient stops', () {
    expect(
      accentOptions.map((option) => option.id).toSet().length,
      accentOptions.length,
    );
    for (final option in accentOptions) {
      expect(
        _contrast(
          _rgb(option.onAccent.toARGB32()),
          _rgb(option.accent.toARGB32()),
        ),
        greaterThanOrEqualTo(4.5),
        reason: '${option.id}: text on accent',
      );
      expect(
        _contrast(
          _rgb(option.onAccent.toARGB32()),
          _rgb(option.gradientEnd.toARGB32()),
        ),
        greaterThanOrEqualTo(4.5),
        reason: '${option.id}: text on gradient end',
      );
    }
  });

  test('theme controller switches the active accent', () {
    AppThemeController.apply('orange');
    expect(AppColors.accent, const Color(0xFFFF6B3D));
    expect(AppColors.onAccent, const Color(0xFF1A0B04));

    AppThemeController.apply('violet');
    expect(AppColors.accent, const Color(0xFF5B4FE8));
    expect(AppColors.onAccent, const Color(0xFFFFFFFF));
  });
}
