import 'dart:async';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:path_provider/path_provider.dart';
import 'package:window_manager/window_manager.dart';

import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';
import '../widgets/desktop_app_bar.dart';
import '../widgets/info_bar.dart';
import '../widgets/playlist_overlay.dart';
import '../widgets/browse_home.dart';
import '../widgets/settings_overlay.dart';
import '../widgets/stats_overlay.dart';
import '../widgets/stream_overlay.dart';
import '../widgets/update_dialog.dart';

class MainScreen extends StatefulWidget {
  final PlayerViewModel vm;

  const MainScreen({super.key, required this.vm});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> with WindowListener {
  final FocusNode _focusNode = FocusNode();
  bool _showFullscreenControls = false;
  Timer? _fullscreenControlsTimer;
  bool _closing = false;
  bool _dropActive = false;
  Future<void> _pendingImport = Future.value();

  Future<void> _importPlaylists(DropDetails details) async {
    if (!mounted || _closing) return;
    final files = <(String, String)>[];
    final Directory support;
    try {
      support = await getApplicationSupportDirectory();
    } on Exception {
      if (mounted && !_closing) widget.vm.showToast(L10n.t.importFailed);
      return;
    }
    final directory = Directory.fromUri(support.uri.resolve('playlists/'));
    for (final item in details.items.whereType<DropFileItem>()) {
      if (!RegExp(r'\.m3u8?$', caseSensitive: false).hasMatch(item.name)) {
        continue;
      }
      // Один сбойный файл не должен отменять импорт остальных
      try {
        const maxSize = 16 * 1024 * 1024;
        if ((await item.file.length() ?? 0) > maxSize) continue;
        final bytes = await item.file.readAsBytes();
        if (bytes.length > maxSize) continue;
        await directory.create(recursive: true);
        final file = File.fromUri(
          directory.uri.resolve('${sha256.convert(bytes)}.m3u'),
        );
        if (!await file.exists()) await file.writeAsBytes(bytes);
        files.add((
          item.name.replaceFirst(RegExp(r'\.m3u8?$', caseSensitive: false), ''),
          file.uri.toString(),
        ));
      } catch (_) {
        if (mounted) widget.vm.showToast(L10n.t.importFailed);
      }
    }
    if (!mounted || _closing) return;
    if (files.isEmpty) {
      widget.vm.showToast(L10n.t.dropHint);
      return;
    }
    widget.vm.addCustomSources(files);
    await pruneImportedPlaylists(directory, {
      for (final source in widget.vm.settings.sources) ...source.urls,
    });
    if (!mounted || _closing) return;
    await widget.vm.refreshPlaylists();
  }

  @override
  void initState() {
    super.initState();
    windowManager.addListener(this);
    _focusNode.requestFocus();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && !_closing) unawaited(widget.vm.init());
    });
  }

  @override
  void dispose() {
    windowManager.removeListener(this);
    _fullscreenControlsTimer?.cancel();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  void onWindowClose() {
    if (_closing) return;
    _closing = true;
    unawaited(_closeWindow());
  }

  Future<void> _closeWindow() async {
    try {
      await widget.vm.shutdown().timeout(const Duration(seconds: 4));
    } catch (error) {
      stderr.writeln('[SimpleTV] Player shutdown: ${error.runtimeType}');
      exit(1);
    }
    // Destroying the GTK window can abort in Flutter's implicit-view teardown
    // even after libmpv releases its video output and event callback.
    exit(0);
  }

  void _onMouseMove() {
    if (!widget.vm.isFullscreen) return;
    if (!_showFullscreenControls) {
      setState(() => _showFullscreenControls = true);
    }
    _fullscreenControlsTimer?.cancel();
    _fullscreenControlsTimer = Timer(const Duration(seconds: 3), () {
      if (mounted &&
          widget.vm.isFullscreen &&
          widget.vm.overlay == OverlayType.none) {
        setState(() => _showFullscreenControls = false);
      }
    });
  }

  void _showContextMenu(TapUpDetails details) {
    final vm = widget.vm;
    final info = vm.playbackInfo;
    final ch = info.channel;
    final overlay = Overlay.of(context).context.findRenderObject() as RenderBox;

    showMenu<String>(
      context: context,
      position: RelativeRect.fromRect(
        details.globalPosition & const Size(40, 40),
        Offset.zero & overlay.size,
      ),
      color: AppColors.surface,
      elevation: 10,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.control),
        side: const BorderSide(color: AppColors.border),
      ),
      items: [
        PopupMenuItem<String>(
          value: 'playlist',
          child: Row(
            children: [
              Icon(Icons.list_alt, size: 18, color: AppColors.accentLight),
              const SizedBox(width: AppSpace.md),
              Text(L10n.t.menuGuide),
            ],
          ),
        ),
        if (ch != null && ch.variants.length > 1)
          PopupMenuItem<String>(
            value: 'next_source',
            child: Row(
              children: [
                Icon(Icons.swap_horiz, size: 18, color: AppColors.accentLight),
                const SizedBox(width: AppSpace.md),
                Text(L10n.t.menuNextSource),
              ],
            ),
          ),
        const PopupMenuDivider(),
        PopupMenuItem<String>(
          value: 'zap_next',
          child: Row(
            children: [
              const Icon(
                Icons.arrow_upward,
                size: 18,
                color: AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpace.md),
              Text(L10n.t.menuNextChannel),
            ],
          ),
        ),
        PopupMenuItem<String>(
          value: 'zap_prev',
          child: Row(
            children: [
              const Icon(
                Icons.arrow_downward,
                size: 18,
                color: AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpace.md),
              Text(L10n.t.menuPrevChannel),
            ],
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem<String>(
          value: 'stats',
          child: Row(
            children: [
              const Icon(
                Icons.query_stats,
                size: 18,
                color: AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpace.md),
              Text(L10n.t.menuStats),
            ],
          ),
        ),
        PopupMenuItem<String>(
          value: 'settings',
          child: Row(
            children: [
              const Icon(
                Icons.settings,
                size: 18,
                color: AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpace.md),
              Text(L10n.t.menuSettings),
            ],
          ),
        ),
        PopupMenuItem<String>(
          value: 'fullscreen',
          child: Row(
            children: [
              const Icon(
                Icons.fullscreen,
                size: 18,
                color: AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpace.md),
              Text(L10n.t.menuFullscreen),
            ],
          ),
        ),
      ],
    ).then((val) {
      if (val == null) return;
      switch (val) {
        case 'playlist':
          vm.setOverlay(OverlayType.playlist);
          break;
        case 'next_source':
          vm.nextVariant();
          break;
        case 'zap_next':
          vm.zapNext();
          break;
        case 'zap_prev':
          vm.zapPrev();
          break;
        case 'stats':
          vm.toggleOverlay(OverlayType.stats);
          break;
        case 'settings':
          vm.toggleOverlay(OverlayType.settings);
          break;
        case 'fullscreen':
          vm.toggleFullscreen();
          break;
      }
    });
  }

  void _handleKeyEvent(KeyEvent event) {
    if (event is! KeyDownEvent) return;

    final key = event.logicalKey;
    if (key != LogicalKeyboardKey.escape && isEditingText()) return;
    final vm = widget.vm;
    if (vm.compactMode && key == LogicalKeyboardKey.escape) {
      unawaited(vm.toggleCompact());
      return;
    }

    if ((vm.overlay == OverlayType.playlist ||
            vm.overlay == OverlayType.home) &&
        (key == LogicalKeyboardKey.arrowUp ||
            key == LogicalKeyboardKey.arrowDown ||
            key == LogicalKeyboardKey.pageUp ||
            key == LogicalKeyboardKey.pageDown ||
            key == LogicalKeyboardKey.space)) {
      return;
    }

    // Цифры 0-9
    if (key == LogicalKeyboardKey.digit0 || key == LogicalKeyboardKey.numpad0) {
      vm.onDigit('0');
      return;
    }
    if (key == LogicalKeyboardKey.digit1 || key == LogicalKeyboardKey.numpad1) {
      vm.onDigit('1');
      return;
    }
    if (key == LogicalKeyboardKey.digit2 || key == LogicalKeyboardKey.numpad2) {
      vm.onDigit('2');
      return;
    }
    if (key == LogicalKeyboardKey.digit3 || key == LogicalKeyboardKey.numpad3) {
      vm.onDigit('3');
      return;
    }
    if (key == LogicalKeyboardKey.digit4 || key == LogicalKeyboardKey.numpad4) {
      vm.onDigit('4');
      return;
    }
    if (key == LogicalKeyboardKey.digit5 || key == LogicalKeyboardKey.numpad5) {
      vm.onDigit('5');
      return;
    }
    if (key == LogicalKeyboardKey.digit6 || key == LogicalKeyboardKey.numpad6) {
      vm.onDigit('6');
      return;
    }
    if (key == LogicalKeyboardKey.digit7 || key == LogicalKeyboardKey.numpad7) {
      vm.onDigit('7');
      return;
    }
    if (key == LogicalKeyboardKey.digit8 || key == LogicalKeyboardKey.numpad8) {
      vm.onDigit('8');
      return;
    }
    if (key == LogicalKeyboardKey.digit9 || key == LogicalKeyboardKey.numpad9) {
      vm.onDigit('9');
      return;
    }

    if (key == LogicalKeyboardKey.arrowUp || key == LogicalKeyboardKey.pageUp) {
      vm.zapNext();
    } else if (key == LogicalKeyboardKey.arrowDown ||
        key == LogicalKeyboardKey.pageDown) {
      vm.zapPrev();
    } else if (key == LogicalKeyboardKey.space) {
      vm.playerService.togglePlayPause();
    } else if (key == LogicalKeyboardKey.keyM) {
      vm.toggleMute();
    } else if (key == LogicalKeyboardKey.keyF ||
        key == LogicalKeyboardKey.f11) {
      vm.toggleFullscreen();
    } else if (key == LogicalKeyboardKey.enter) {
      if (vm.overlay == OverlayType.none) {
        vm.setOverlay(OverlayType.playlist);
      }
    } else if (key == LogicalKeyboardKey.escape) {
      if (vm.overlay != OverlayType.none) {
        vm.closeOverlay();
      } else if (vm.isFullscreen) {
        vm.toggleFullscreen();
      }
    } else if (key == LogicalKeyboardKey.keyP ||
        key == LogicalKeyboardKey.keyL ||
        key == LogicalKeyboardKey.f2) {
      vm.toggleOverlay(OverlayType.playlist);
    } else if (key == LogicalKeyboardKey.keyG) {
      if (vm.nowOnly && vm.overlay == OverlayType.playlist) {
        vm.closeOverlay();
      } else {
        if (!vm.nowOnly) vm.toggleNow();
        vm.setOverlay(OverlayType.playlist);
      }
    } else if (key == LogicalKeyboardKey.keyR) {
      vm.playPreviousChannel();
    } else if (key == LogicalKeyboardKey.keyS) {
      vm.toggleOverlay(OverlayType.settings);
    } else if (key == LogicalKeyboardKey.keyV ||
        key == LogicalKeyboardKey.keyQ) {
      vm.toggleOverlay(OverlayType.stream);
    } else if (key == LogicalKeyboardKey.keyI) {
      vm.toggleInfoBar();
    } else if (key == LogicalKeyboardKey.tab) {
      vm.toggleOverlay(OverlayType.stats);
    } else if (key == LogicalKeyboardKey.equal ||
        key == LogicalKeyboardKey.add) {
      vm.changeVolume(5);
    } else if (key == LogicalKeyboardKey.minus) {
      vm.changeVolume(-5);
    }
  }

  @override
  Widget build(BuildContext context) {
    return DropTarget(
      acceptedKinds: const {DropItemKind.file},
      onDrop: (details) => _pendingImport = _pendingImport.whenComplete(
        () => _importPlaylists(details),
      ),
      onEnter: (_) => setState(() => _dropActive = true),
      onLeave: () => setState(() => _dropActive = false),
      child: _buildPlayer(),
    );
  }

  Widget _buildPlayer() => AnimatedBuilder(
    animation: widget.vm,
    builder: (context, _) {
      final vm = widget.vm;
      final info = vm.playbackInfo;

      return KeyboardListener(
        focusNode: _focusNode,
        autofocus: true,
        onKeyEvent: _handleKeyEvent,
        child: MouseRegion(
          onHover: (_) => _onMouseMove(),
          child: Scaffold(
            backgroundColor: Colors.black,
            body: Column(
              children: [
                // Верхняя панель управления десктопной версии
                if (vm.compactMode)
                  Row(
                    children: [
                      const SizedBox(width: AppSpace.md),
                      Expanded(
                        child: Text(
                          info.channel?.name ?? 'SimpleTV',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.history),
                        tooltip: L10n.t.tooltipLastChannel,
                        onPressed: vm.previousChannel == null
                            ? null
                            : vm.playPreviousChannel,
                      ),
                      IconButton(
                        icon: const Icon(Icons.volume_up),
                        tooltip: L10n.t.tooltipSound,
                        onPressed: vm.toggleMute,
                      ),
                      IconButton(
                        icon: const Icon(Icons.open_in_full),
                        tooltip: L10n.t.tooltipNormalWindow,
                        onPressed: vm.toggleCompact,
                      ),
                    ],
                  )
                else if (!vm.isFullscreen || _showFullscreenControls)
                  DesktopAppBar(vm: vm),

                // Основная видеоповерхность и оверлеи
                Expanded(
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      // Видеоповерхность mpv (GPU Texture)
                      GestureDetector(
                        onTap: () {
                          if (vm.overlay != OverlayType.none) {
                            vm.closeOverlay();
                          } else {
                            if (vm.isInfoBarVisible) {
                              vm.hideInfoBar();
                            } else {
                              vm.showInfoBar(temporary: true);
                            }
                          }
                        },
                        onDoubleTap: () => vm.toggleFullscreen(),
                        onSecondaryTapUp: _showContextMenu,
                        child: Listener(
                          onPointerSignal: (event) {
                            if (event is PointerScrollEvent) {
                              vm.changeVolume(-event.scrollDelta.dy / 20);
                            }
                          },
                          child: Center(
                            child: vm.playerService.isInitialized
                                ? Video(
                                    controller: vm.playerService.controller,
                                    controls: NoVideoControls,
                                  )
                                : const SizedBox.shrink(),
                          ),
                        ),
                      ),

                      // Спиннер буферизации
                      if (info.isBuffering && info.channel != null)
                        Center(
                          child: Container(
                            padding: const EdgeInsets.all(AppSpace.xl),
                            decoration: BoxDecoration(
                              color: Colors.black45,
                              borderRadius: BorderRadius.circular(
                                AppRadius.card,
                              ),
                            ),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                CircularProgressIndicator(
                                  color: AppColors.accentLight,
                                  strokeWidth: 3.5,
                                ),
                                const SizedBox(height: AppSpace.md),
                                Text(
                                  L10n.t.connecting(
                                    info.channel!.variants.isNotEmpty
                                        ? info
                                              .channel!
                                              .variants[info.variantIndex.clamp(
                                                0,
                                                info.channel!.variants.length -
                                                    1,
                                              )]
                                              .sourceName
                                        : '',
                                    info.variantIndex + 1,
                                    info.channel!.variants.length,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),

                      // Ошибка воспроизведения
                      if (info.error != null && !info.isBuffering)
                        Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppSpace.xl,
                              vertical: AppSpace.xl,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.overlayBackground,
                              borderRadius: BorderRadius.circular(
                                AppRadius.card,
                              ),
                              border: Border.all(
                                color: AppColors.red.withValues(alpha: 0.5),
                              ),
                            ),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(
                                  Icons.error_outline,
                                  color: AppColors.red,
                                  size: 40,
                                ),
                                const SizedBox(height: AppSpace.md),
                                Text(
                                  L10n.t.playbackError,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: AppType.subtitle,
                                  ),
                                ),
                                const SizedBox(height: AppSpace.sm),
                                Text(
                                  info.error!,
                                  style: const TextStyle(
                                    color: AppColors.textSecondary,
                                    fontSize: AppType.body,
                                  ),
                                ),
                                const SizedBox(height: AppSpace.lg),
                                Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    ElevatedButton(
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: AppColors.accent,
                                        foregroundColor: AppColors.onAccent,
                                      ),
                                      onPressed: () {
                                        if (info.channel != null) {
                                          vm.play(
                                            info.channel!,
                                            variantIndex: info.variantIndex,
                                            archive: info.archive,
                                          );
                                        }
                                      },
                                      child: Text(L10n.t.retry),
                                    ),
                                    if ((info.channel?.variants.length ?? 0) >
                                        1) ...[
                                      const SizedBox(width: AppSpace.md),
                                      OutlinedButton(
                                        style: OutlinedButton.styleFrom(
                                          foregroundColor: AppColors.text,
                                        ),
                                        onPressed: () => vm.nextVariant(),
                                        child: Text(L10n.t.nextSource),
                                      ),
                                    ],
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),

                      // Инфобар
                      if (vm.isInfoBarVisible && !vm.compactMode)
                        InfoBar(vm: vm),

                      // Оверлей плейлиста (двухпанельный десктопный телегид)
                      if (!vm.compactMode && vm.overlay == OverlayType.home)
                        BrowseHome(vm: vm),

                      if (!vm.compactMode && vm.overlay == OverlayType.playlist)
                        PlaylistOverlay(key: ValueKey(vm.nowOnly), vm: vm),

                      // Оверлей настроек
                      if (!vm.compactMode && vm.overlay == OverlayType.settings)
                        SettingsOverlay(vm: vm),

                      // Оверлей переключения источников
                      if (!vm.compactMode && vm.overlay == OverlayType.stream)
                        StreamOverlay(vm: vm),

                      // Оверлей технической статистики
                      if (!vm.compactMode && vm.overlay == OverlayType.stats)
                        StatsOverlay(vm: vm),

                      if (vm.update != null) UpdateDialog(vm: vm),

                      if (_dropActive)
                        Positioned.fill(
                          child: IgnorePointer(
                            child: ColoredBox(
                              color: Colors.black54,
                              child: Center(
                                child: Text(
                                  L10n.t.dropToAdd,
                                  style: Theme.of(context).textTheme.titleLarge,
                                ),
                              ),
                            ),
                          ),
                        ),

                      // Быстрый ввод номера канала
                      if (vm.quickDigits.isNotEmpty)
                        Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppSpace.xxl,
                              vertical: AppSpace.lg,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black87,
                              borderRadius: BorderRadius.circular(
                                AppRadius.card,
                              ),
                              border: Border.all(
                                color: AppColors.accent,
                                width: 2,
                              ),
                            ),
                            child: Text(
                              vm.quickDigits,
                              style: const TextStyle(
                                fontSize: AppType.hero,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                          ),
                        ),

                      // Индикатор громкости
                      if (vm.volumeOverlay != null)
                        Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppSpace.xl,
                              vertical: AppSpace.lg,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black87,
                              borderRadius: BorderRadius.circular(
                                AppRadius.panel,
                              ),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  vm.isMuted
                                      ? Icons.volume_off
                                      : (vm.volumeOverlay! > 50
                                            ? Icons.volume_up
                                            : Icons.volume_down),
                                  color: Colors.white,
                                  size: 28,
                                ),
                                const SizedBox(width: AppSpace.md),
                                Text(
                                  '${vm.volumeOverlay!.round()}%',
                                  style: const TextStyle(
                                    fontSize: AppType.title,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.white,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),

                      // Toast уведомление
                      if (vm.toast != null)
                        Align(
                          alignment: Alignment.topCenter,
                          child: Container(
                            margin: const EdgeInsets.only(top: AppSpace.xl),
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppSpace.xl,
                              vertical: AppSpace.md,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.overlayBackground,
                              borderRadius: BorderRadius.circular(
                                AppRadius.card,
                              ),
                              border: Border.all(color: AppColors.border),
                            ),
                            child: Text(
                              vm.toast!,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: AppType.body,
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}

bool isEditingText() =>
    FocusManager.instance.primaryFocus?.context
        ?.findAncestorStateOfType<EditableTextState>() !=
    null;

Future<void> pruneImportedPlaylists(
  Directory directory,
  Set<String> referenced,
) async {
  // Settings refer directly to imported files, so only prune unused copies.
  try {
    final orphaned = await directory
        .list(followLinks: false)
        .where(
          (file) => file is File && !referenced.contains(file.uri.toString()),
        )
        .cast<File>()
        .toList();
    final excess = orphaned.length - 50;
    if (excess <= 0) return;
    orphaned.sort(
      (a, b) => a.lastModifiedSync().compareTo(b.lastModifiedSync()),
    );
    for (final file in orphaned.take(excess)) {
      await file.delete();
    }
  } on FileSystemException {
    // Cleanup is optional, including when the directory has yet to be created.
  }
}
