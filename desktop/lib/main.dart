import 'dart:io';

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:path_provider/path_provider.dart';
import 'package:window_manager/window_manager.dart';

import 'data/services/logo_service.dart';
import 'data/services/epg_service.dart';
import 'l10n/app_localizations.dart';
import 'l10n/l10n.dart';
import 'ui/theme/theme.dart';
import 'ui/view_models/player_view_model.dart';
import 'ui/views/main_screen.dart';

void main() async {
  FlutterError.onError = (details) {
    FlutterError.dumpErrorToConsole(details);
  };

  WidgetsFlutterBinding.ensureInitialized();
  try {
    await LogoService.loadCatalog();
  } catch (_) {
    // Без каталога логотипов стартуем дальше: логотипы подтянутся из EPG.
  }
  final bundledMpv = File.fromUri(
    File(Platform.resolvedExecutable).uri.resolve('lib/libmpv.so.2'),
  );
  MediaKit.ensureInitialized(
    libmpv: Platform.isLinux && bundledMpv.existsSync()
        ? bundledMpv.path
        : null,
  );
  await windowManager.ensureInitialized();
  // На Linux закрытие окна оставляет живой mpv без явного shutdown; на Windows
  // таким же образом гарантируем чистое освобождение плеера перед выходом.
  await windowManager.setPreventClose(true);

  const windowOptions = WindowOptions(
    size: Size(1280, 720),
    minimumSize: Size(960, 600),
    center: true,
    backgroundColor: Colors.black,
    skipTaskbar: false,
    title: 'SimpleTV',
  );

  await windowManager.waitUntilReadyToShow(windowOptions, () async {
    await windowManager.show();
    await windowManager.focus();
  });

  Directory? support;
  try {
    support = await getApplicationSupportDirectory();
  } on Exception {
    // Playback and the in-memory guide are still available.
  }
  final vm = PlayerViewModel(epgService: EpgService(cacheDirectory: support));
  runApp(SimpleTvApp(vm: vm));
}

class SimpleTvApp extends StatelessWidget {
  final PlayerViewModel vm;

  const SimpleTvApp({super.key, required this.vm});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<AccentOption>(
      valueListenable: AppThemeController.notifier,
      builder: (context, _, _) => MaterialApp(
        title: 'SimpleTV',
        debugShowCheckedModeBanner: false,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) {
          L10n.update(context);
          return child ?? const SizedBox.shrink();
        },
        theme: buildAppTheme(),
        home: MainScreen(vm: vm),
      ),
    );
  }
}
