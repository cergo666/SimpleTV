import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';
import 'channel_logo.dart';

class InfoBar extends StatelessWidget {
  final PlayerViewModel vm;

  const InfoBar({super.key, required this.vm});

  String _formatTime(DateTime dt) => DateFormat('HH:mm').format(dt);

  String? _codec(String? value) => switch (value) {
    'h264' => 'H.264',
    'hevc' => 'HEVC',
    _ => value?.toUpperCase(),
  };

  String _number(double value) =>
      value.toStringAsFixed(2).replaceFirst(RegExp(r'\.?0+$'), '');

  String? _bitrate(double? value) => value == null
      ? null
      : value >= 1000000
      ? L10n.t.mbps(_number(value / 1000000))
      : L10n.t.kbps((value / 1000).round().toString());

  @override
  Widget build(BuildContext context) {
    final info = vm.playbackInfo;
    final ch = info.channel;
    if (ch == null) return const SizedBox.shrink();

    final curProg = info.archive ?? vm.currentProgramme;
    final nextProg = info.archive == null ? vm.nextProgramme : null;
    final variant =
        info.variantIndex >= 0 && info.variantIndex < ch.variants.length
        ? ch.variants[info.variantIndex]
        : null;
    final format = info.format;
    final video = [
      if ((info.width ?? 0) > 0 && (info.height ?? 0) > 0)
        '${info.width}×${info.height}',
      _codec(format?.videoCodec),
      if (format?.fps case final fps?) '${_number(fps)} fps',
      _bitrate(format?.videoBitrate),
    ].nonNulls.join(' · ');
    final audio = [
      _codec(format?.audioCodec),
      switch (format?.audioChannels) {
        1 => L10n.t.mono,
        2 => L10n.t.stereo,
        final count? when count > 2 => L10n.t.channelsShort(count),
        _ => null,
      },
      if (format?.sampleRate case final rate? when rate > 0)
        L10n.t.khz(_number(rate / 1000)),
      _bitrate(format?.audioBitrate),
    ].nonNulls.join(' · ');

    return Align(
      alignment: Alignment.bottomCenter,
      child: Container(
        margin: const EdgeInsets.all(AppSpace.xl),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpace.xl,
          vertical: AppSpace.lg,
        ),
        decoration: BoxDecoration(
          color: AppColors.overlayBackground,
          borderRadius: BorderRadius.circular(AppRadius.card),
          border: Border.all(color: AppColors.border),
          boxShadow: const [
            BoxShadow(
              color: Colors.black54,
              blurRadius: 20,
              offset: Offset(0, 8),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                // Номер канала
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpace.md,
                    vertical: AppSpace.xs,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.accent,
                    borderRadius: BorderRadius.circular(AppRadius.control),
                  ),
                  child: Text(
                    '${ch.number}',
                    style: TextStyle(
                      color: AppColors.onAccent,
                      fontWeight: FontWeight.bold,
                      fontSize: AppType.subtitle,
                    ),
                  ),
                ),
                const SizedBox(width: AppSpace.lg),

                Padding(
                  padding: const EdgeInsets.only(right: AppSpace.lg),
                  child: ChannelLogo(ch, width: 44, height: 32),
                ),

                // Название канала и группа
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              ch.name,
                              style: const TextStyle(
                                fontSize: AppType.title,
                                fontWeight: FontWeight.bold,
                                color: AppColors.text,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          if (vm.favorites.contains(ch.key))
                            Padding(
                              padding: const EdgeInsets.only(left: AppSpace.sm),
                              child: Icon(
                                Icons.star,
                                color: AppColors.accentLight,
                                size: 18,
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: AppSpace.xs),
                      Text(
                        '${ch.group} • ${variant?.sourceName ?? L10n.t.unknownSource} (${info.variantIndex + 1}/${ch.variants.length})',
                        style: const TextStyle(
                          fontSize: AppType.caption,
                          color: AppColors.textSecondary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      for (final line in [
                        if (video.isNotEmpty) video,
                        if (audio.isNotEmpty) '${L10n.t.audio} · $audio',
                      ])
                        Padding(
                          padding: const EdgeInsets.only(top: AppSpace.xs),
                          child: Text(
                            line,
                            style: const TextStyle(
                              fontSize: AppType.caption,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),

            if (info.archive != null)
              Row(
                children: [
                  Text(
                    L10n.t.archive,
                    style: TextStyle(color: AppColors.accentLight),
                  ),
                  const Spacer(),
                  TextButton.icon(
                    icon: const Icon(Icons.live_tv, size: 18),
                    label: Text(L10n.t.backToLive),
                    onPressed: () => vm.play(ch),
                  ),
                ],
              ),
            // Телепрограмма EPG
            if (curProg != null) ...[
              const SizedBox(height: AppSpace.md),
              Row(
                children: [
                  Text(
                    '${_formatTime(curProg.start)} - ${_formatTime(curProg.stop)}',
                    style: TextStyle(
                      fontSize: AppType.body,
                      fontWeight: FontWeight.bold,
                      color: AppColors.accentLight,
                    ),
                  ),
                  const SizedBox(width: AppSpace.md),
                  Expanded(
                    child: Text(
                      curProg.title,
                      style: const TextStyle(
                        fontSize: AppType.body,
                        color: AppColors.text,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpace.sm),
              ClipRRect(
                borderRadius: BorderRadius.circular(AppRadius.chip),
                child: LinearProgressIndicator(
                  value: curProg.progress,
                  minHeight: 4,
                  backgroundColor: AppColors.surfaceHover,
                  valueColor: AlwaysStoppedAnimation(AppColors.accent),
                ),
              ),
            ],

            if (nextProg != null) ...[
              const SizedBox(height: AppSpace.sm),
              Row(
                children: [
                  Text(
                    L10n.t.nextAt(_formatTime(nextProg.start)),
                    style: const TextStyle(
                      fontSize: AppType.caption,
                      color: AppColors.textMuted,
                    ),
                  ),
                  Expanded(
                    child: Text(
                      nextProg.title,
                      style: const TextStyle(
                        fontSize: AppType.caption,
                        color: AppColors.textSecondary,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
