import 'package:flutter/material.dart';

import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';
import 'channel_logo.dart';

class DesktopAppBar extends StatelessWidget {
  final PlayerViewModel vm;

  const DesktopAppBar({super.key, required this.vm});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) =>
          _buildToolbar(context, compact: constraints.maxWidth < 1200),
    );
  }

  Widget _buildToolbar(BuildContext context, {required bool compact}) {
    final info = vm.playbackInfo;
    final ch = info.channel;
    final prog = vm.currentProgramme;
    final currentVol = vm.playerService.volume;
    final canZap = vm.channels.length > 1;
    const toolbarButtonSize = BoxConstraints.tightFor(width: 34, height: 40);
    final toolbarButtonStyle = IconButton.styleFrom(
      fixedSize: const Size(34, 40),
      minimumSize: Size.zero,
      padding: EdgeInsets.zero,
      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
    );
    final sourceLabel = ch == null || ch.variants.isEmpty
        ? null
        : '${ch.variants[info.variantIndex.clamp(0, ch.variants.length - 1)].sourceName} (${info.variantIndex + 1}/${ch.variants.length})';

    return Container(
      height: 48,
      padding: const EdgeInsets.symmetric(horizontal: AppSpace.md),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.94),
        border: const Border(
          bottom: BorderSide(color: AppColors.border, width: 1),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.5),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          // Логотип приложения
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(AppSpace.xs),
                decoration: BoxDecoration(
                  color: AppColors.accent,
                  borderRadius: BorderRadius.circular(AppRadius.control),
                ),
                child: Icon(Icons.tv, size: 18, color: AppColors.onAccent),
              ),
              if (!compact) const SizedBox(width: AppSpace.sm),
              if (!compact)
                const Text(
                  'SimpleTV',
                  style: TextStyle(
                    color: AppColors.text,
                    fontWeight: FontWeight.bold,
                    fontSize: AppType.subtitle,
                    letterSpacing: 0.5,
                  ),
                ),
            ],
          ),

          const SizedBox(width: AppSpace.lg),
          const VerticalDivider(
            color: AppColors.border,
            indent: 10,
            endIndent: 10,
            width: 1,
          ),
          const SizedBox(width: AppSpace.md),

          // Кнопка: [Плейлист и ТВ-Гид]
          if (compact)
            IconButton(
              constraints: toolbarButtonSize,
              style: toolbarButtonStyle,
              tooltip: L10n.t.channelsGuideTooltip,
              icon: const Icon(Icons.list_alt, size: 18),
              onPressed: () => vm.toggleOverlay(OverlayType.playlist),
            )
          else
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: vm.overlay == OverlayType.playlist
                    ? AppColors.accent
                    : AppColors.surfaceLight,
                foregroundColor: vm.overlay == OverlayType.playlist
                    ? AppColors.onAccent
                    : AppColors.text,
                elevation: 0,
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpace.md,
                  vertical: AppSpace.sm,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(AppRadius.control),
                  side: BorderSide(
                    color: vm.overlay == OverlayType.playlist
                        ? AppColors.accentLight
                        : AppColors.border,
                  ),
                ),
              ),
              icon: const Icon(Icons.list_alt, size: 16),
              label: Text(
                L10n.t.channelsGuide,
                style: const TextStyle(
                  fontSize: AppType.body,
                  fontWeight: FontWeight.w600,
                ),
              ),
              onPressed: () => vm.toggleOverlay(OverlayType.playlist),
            ),

          const SizedBox(width: AppSpace.sm),

          IconButton(
            constraints: toolbarButtonSize,
            style: toolbarButtonStyle,
            tooltip: L10n.t.browseHome,
            icon: const Icon(Icons.home_outlined, size: 20),
            onPressed: () => vm.toggleOverlay(OverlayType.home),
          ),
          IconButton(
            constraints: toolbarButtonSize,
            style: toolbarButtonStyle,
            tooltip: L10n.t.prevChannelTooltip,
            icon: const Icon(Icons.chevron_left, size: 20),
            onPressed: canZap ? vm.zapPrev : null,
          ),
          IconButton(
            constraints: toolbarButtonSize,
            style: toolbarButtonStyle,
            tooltip: L10n.t.nextChannelTooltip,
            icon: const Icon(Icons.chevron_right, size: 20),
            onPressed: canZap ? vm.zapNext : null,
          ),
          IconButton(
            constraints: toolbarButtonSize,
            style: toolbarButtonStyle,
            tooltip: L10n.t.channelInfoTooltip,
            icon: const Icon(Icons.info_outline, size: 18),
            onPressed: ch == null ? null : vm.toggleInfoBar,
          ),

          // Информация о текущем канале (в центре)
          Expanded(
            child: ch == null
                ? Center(
                    child: Text(
                      L10n.t.channelNotSelected,
                      style: TextStyle(
                        color: AppColors.textMuted,
                        fontSize: AppType.body,
                      ),
                    ),
                  )
                : Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(right: AppSpace.sm),
                        child: ChannelLogo(ch, width: 28, height: 20),
                      ),
                      Flexible(
                        child: Text(
                          '${ch.number > 0 ? "${ch.number}. " : ""}${ch.name}',
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: AppType.body,
                            color: AppColors.text,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (prog != null) ...[
                        const Text(
                          ' • ',
                          style: TextStyle(color: AppColors.textMuted),
                        ),
                        Flexible(
                          child: Text(
                            prog.title,
                            style: const TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: AppType.body,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ],
                  ),
          ),

          // Селектор источника: [Источник: Beeline (1/2) ▾]
          if (sourceLabel != null) ...[
            Tooltip(
              message: L10n.t.sourceLabel(sourceLabel),
              child: InkWell(
                onTap: () => vm.nextVariant(),
                borderRadius: BorderRadius.circular(AppRadius.control),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpace.md,
                    vertical: AppSpace.xs,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceLight,
                    borderRadius: BorderRadius.circular(AppRadius.control),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.bolt, size: 14, color: AppColors.accentLight),
                      if (!compact) const SizedBox(width: AppSpace.xs),
                      if (!compact)
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 130),
                          child: Text(
                            sourceLabel,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: AppColors.text,
                              fontSize: AppType.caption,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      const SizedBox(width: AppSpace.xs),
                      const Icon(
                        Icons.swap_horiz,
                        size: 14,
                        color: AppColors.textSecondary,
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(width: AppSpace.sm),
          ],

          // Соотношение сторон
          PopupMenuButton<String?>(
            tooltip: L10n.t.aspectTooltip,
            initialValue: vm.settings.aspectRatio,
            onSelected: (val) => vm.setAspectRatio(val),
            itemBuilder: (context) => [
              PopupMenuItem(value: null, child: Text(L10n.t.aspectOriginal)),
              PopupMenuItem(value: '16:9', child: Text(L10n.t.aspect169)),
              PopupMenuItem(value: '4:3', child: Text(L10n.t.aspect43)),
              PopupMenuItem(value: '16:10', child: Text(L10n.t.aspect1610)),
              PopupMenuItem(value: '21:9', child: Text(L10n.t.aspect219)),
            ],
            child: Builder(
              builder: (context) {
                // Геттер настроек уже нормализует legacy-значение «Исходный» в null.
                final label = vm.settings.aspectRatio ?? L10n.t.aspectOriginal;
                return Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpace.sm,
                    vertical: AppSpace.xs,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceLight,
                    borderRadius: BorderRadius.circular(AppRadius.control),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.aspect_ratio,
                        size: 14,
                        color: AppColors.textSecondary,
                      ),
                      if (!compact) const SizedBox(width: AppSpace.xs),
                      if (!compact)
                        Text(
                          label,
                          style: const TextStyle(
                            color: AppColors.textSecondary,
                            fontSize: AppType.caption,
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),

          const SizedBox(width: AppSpace.sm),
          const VerticalDivider(
            color: AppColors.border,
            indent: 12,
            endIndent: 12,
            width: 1,
          ),
          const SizedBox(width: AppSpace.sm),

          // Громкость (иконка + слайдер)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                icon: Icon(
                  vm.isMuted
                      ? Icons.volume_off
                      : (currentVol > 50 ? Icons.volume_up : Icons.volume_down),
                  size: 18,
                  color: vm.isMuted ? AppColors.red : AppColors.textSecondary,
                ),
                tooltip: L10n.t.muteTooltip,
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
                onPressed: () => vm.toggleMute(),
              ),
              SizedBox(
                width: 75,
                child: SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    trackHeight: 3,
                    thumbShape: const RoundSliderThumbShape(
                      enabledThumbRadius: 6,
                    ),
                    overlayShape: const RoundSliderOverlayShape(
                      overlayRadius: 10,
                    ),
                    activeTrackColor: AppColors.accentLight,
                    inactiveTrackColor: AppColors.surfaceLight,
                    thumbColor: Colors.white,
                  ),
                  child: Slider(
                    value: currentVol.clamp(0.0, 100.0),
                    min: 0,
                    max: 100,
                    onChanged: vm.setVolume,
                  ),
                ),
              ),
              if (!compact)
                Text(
                  '${currentVol.round()}%',
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: AppType.caption,
                  ),
                ),
            ],
          ),

          const SizedBox(width: AppSpace.sm),

          // Кнопка: [Настройки (S)]
          IconButton(
            icon: const Icon(
              Icons.settings,
              size: 18,
              color: AppColors.textSecondary,
            ),
            tooltip: L10n.t.settingsTooltip,
            onPressed: () => vm.toggleOverlay(OverlayType.settings),
          ),

          // Кнопка: [Во весь экран (F)]
          IconButton(
            constraints: toolbarButtonSize,
            style: toolbarButtonStyle,
            tooltip: L10n.t.lastChannelTooltip,
            icon: const Icon(Icons.history, size: 18),
            onPressed: vm.previousChannel == null
                ? null
                : vm.playPreviousChannel,
          ),
          IconButton(
            constraints: toolbarButtonSize,
            style: toolbarButtonStyle,
            tooltip: L10n.t.compactTooltip,
            icon: const Icon(Icons.picture_in_picture_alt_outlined, size: 18),
            onPressed: vm.toggleCompact,
          ),
          IconButton(
            icon: Icon(
              vm.isFullscreen ? Icons.fullscreen_exit : Icons.fullscreen,
              size: 20,
              color: AppColors.textSecondary,
            ),
            tooltip: L10n.t.fullscreenTooltip,
            onPressed: () => vm.toggleFullscreen(),
          ),
        ],
      ),
    );
  }
}
