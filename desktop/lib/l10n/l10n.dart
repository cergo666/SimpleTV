import 'package:flutter/widgets.dart';

import 'app_localizations.dart';

/// Global access to the current translations.
///
/// Widgets should normally use `AppLocalizations.of(context)`, but playback,
/// playlist and EPG services also produce user-facing messages outside the
/// widget tree. They use [L10n.t]; [update] is called from the MaterialApp
/// builder whenever the active locale changes.
class L10n {
  L10n._();

  static AppLocalizations _current = lookupAppLocalizations(const Locale('ru'));

  static AppLocalizations get t => _current;

  static void update(BuildContext context) {
    _current = AppLocalizations.of(context);
  }
}

/// Localized display name for built-in EPG sources. The persisted name stays
/// unchanged so cached settings are not migrated.
String epgSourceLabel(String id, String fallback) => switch (id) {
  'iptvx_lite' => L10n.t.epgIptvxLite,
  'epgpw' => L10n.t.epgPwRu,
  _ => fallback,
};
