import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';

import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';

class StreamOverlay extends StatelessWidget {
  final PlayerViewModel vm;

  const StreamOverlay({super.key, required this.vm});

  @override
  Widget build(BuildContext context) {
    final info = vm.playbackInfo;
    final ch = info.channel;
    if (ch == null || ch.variants.isEmpty) return const SizedBox.shrink();
    final qualities = vm.playerService.videoQualities;

    return Center(
      child: Container(
        width: 440,
        padding: const EdgeInsets.all(AppSpace.xl),
        decoration: BoxDecoration(
          color: AppColors.overlayBackground,
          borderRadius: BorderRadius.circular(AppRadius.card),
          border: Border.all(color: AppColors.border),
          boxShadow: const [
            BoxShadow(
              color: Colors.black54,
              blurRadius: 24,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  L10n.t.sourcesTitle(ch.name),
                  style: const TextStyle(
                    fontSize: AppType.title,
                    fontWeight: FontWeight.bold,
                    color: AppColors.text,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close, color: AppColors.textSecondary),
                  onPressed: () => vm.closeOverlay(),
                ),
              ],
            ),
            const SizedBox(height: AppSpace.md),
            if (qualities.length > 1)
              Row(
                children: [
                  Expanded(child: Text(L10n.t.videoQuality)),
                  PopupMenuButton<VideoTrack>(
                    tooltip: L10n.t.videoQualityTooltip,
                    initialValue: vm.playerService.videoTrack,
                    onSelected: (track) async {
                      await vm.playerService.setVideoQuality(track);
                      vm.refresh();
                    },
                    itemBuilder: (_) => [
                      PopupMenuItem(
                        value: VideoTrack.auto(),
                        child: Text(L10n.t.auto),
                      ),
                      for (final track in qualities)
                        PopupMenuItem(
                          value: track,
                          child: Text(
                            '${track.h}p${track.bitrate == null ? '' : ' · ${L10n.t.kbps(track.bitrate! ~/ 1000)}'}',
                          ),
                        ),
                    ],
                    child: Text(
                      vm.playerService.videoTrack.h == null
                          ? L10n.t.autoArrow
                          : '${vm.playerService.videoTrack.h}p ▾',
                    ),
                  ),
                ],
              ),
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 320),
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: ch.variants.length,
                separatorBuilder: (context, index) =>
                    const SizedBox(height: AppSpace.sm),
                itemBuilder: (context, i) {
                  final v = ch.variants[i];
                  final isSelected = i == info.variantIndex;

                  return InkWell(
                    onTap: () {
                      vm.selectVariant(i);
                      vm.closeOverlay();
                    },
                    borderRadius: BorderRadius.circular(AppRadius.control),
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpace.lg,
                        vertical: AppSpace.md,
                      ),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? AppColors.accent
                            : AppColors.surface,
                        borderRadius: BorderRadius.circular(AppRadius.control),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            isSelected
                                ? Icons.radio_button_checked
                                : Icons.radio_button_unchecked,
                            color: isSelected
                                ? AppColors.onAccent
                                : AppColors.textSecondary,
                            size: 20,
                          ),
                          const SizedBox(width: AppSpace.md),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  v.sourceName,
                                  style: TextStyle(
                                    fontWeight: isSelected
                                        ? FontWeight.bold
                                        : FontWeight.normal,
                                    color: isSelected
                                        ? AppColors.onAccent
                                        : AppColors.text,
                                    fontSize: AppType.subtitle,
                                  ),
                                ),
                                const SizedBox(height: AppSpace.xs),
                                Text(
                                  v.url,
                                  style: TextStyle(
                                    color: isSelected
                                        ? AppColors.onAccent.withValues(
                                            alpha: 0.8,
                                          )
                                        : AppColors.textMuted,
                                    fontSize: AppType.caption,
                                  ),
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ],
                            ),
                          ),
                          if (v.clearKey != null)
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: AppSpace.sm,
                                vertical: AppSpace.xs,
                              ),
                              decoration: BoxDecoration(
                                color: isSelected
                                    ? AppColors.onAccent.withValues(alpha: 0.15)
                                    : AppColors.surfaceLight,
                                borderRadius: BorderRadius.circular(
                                  AppRadius.chip,
                                ),
                              ),
                              child: Text(
                                L10n.t.drmShort,
                                style: TextStyle(
                                  fontSize: AppType.caption,
                                  fontWeight: FontWeight.bold,
                                  color: isSelected
                                      ? AppColors.onAccent
                                      : AppColors.accentLight,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
