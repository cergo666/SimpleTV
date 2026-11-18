import 'package:flutter/material.dart';

import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';

class StatsOverlay extends StatelessWidget {
  final PlayerViewModel vm;

  const StatsOverlay({super.key, required this.vm});

  @override
  Widget build(BuildContext context) {
    final info = vm.playbackInfo;
    final ch = info.channel;
    final format = info.format;
    final variant = (ch != null && ch.variants.isNotEmpty)
        ? ch.variants[info.variantIndex]
        : null;

    return Align(
      alignment: Alignment.topRight,
      child: Container(
        margin: const EdgeInsets.all(AppSpace.xl),
        padding: const EdgeInsets.all(AppSpace.lg),
        decoration: BoxDecoration(
          color: AppColors.overlayBackground,
          borderRadius: BorderRadius.circular(AppRadius.panel),
          border: Border.all(color: AppColors.border),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              L10n.t.statsTitle,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: AppType.body,
                color: AppColors.accentLight,
              ),
            ),
            const SizedBox(height: AppSpace.sm),
            _statRow(L10n.t.statChannel, ch?.name ?? '—'),
            _statRow(L10n.t.statSource, variant?.sourceName ?? '—'),
            _statRow(
              L10n.t.videoFormat,
              info.width != null ? '${info.width}x${info.height}' : L10n.t.auto,
            ),
            _statRow(
              L10n.t.videoCodec,
              format?.videoCodec?.toUpperCase() ?? '—',
            ),
            _statRow(
              L10n.t.audioCodec,
              format?.audioCodec?.toUpperCase() ?? '—',
            ),
            if (format?.renderer case final renderer?)
              _statRow(L10n.t.videoOutput, renderer),
            _statRow(L10n.t.decoding, format?.decoder ?? '—'),
            if (format?.fps case final fps?)
              _statRow(L10n.t.fpsLabel, fps.toStringAsFixed(2)),
            if (format?.videoBitrate case final rate?)
              _statRow(
                L10n.t.statVideoBitrate,
                L10n.t.kbps((rate / 1000).round().toString()),
              ),
            if (format?.audioBitrate case final rate?)
              _statRow(
                L10n.t.statAudioBitrate,
                L10n.t.kbps((rate / 1000).round().toString()),
              ),
            if (format?.droppedFrames case final dropped?)
              _statRow(L10n.t.droppedFrames, '$dropped'),
            if (format?.bufferSeconds case final buffer?)
              _statRow(
                L10n.t.statBuffer,
                L10n.t.secondsShort(buffer.toStringAsFixed(1)),
              ),
            _statRow(
              L10n.t.buffering,
              info.isBuffering ? L10n.t.yes : L10n.t.no,
            ),
            _statRow(
              L10n.t.statPlaying,
              info.isPlaying ? L10n.t.stateActive : L10n.t.statePaused,
            ),
            _statRow(
              L10n.t.drmLabel,
              variant?.clearKey != null ? L10n.t.yes : L10n.t.no,
            ),
            _statRow(L10n.t.engine, 'libmpv / media_kit'),
          ],
        ),
      ),
    );
  }

  Widget _statRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpace.xs),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '$label: ',
            style: const TextStyle(
              fontSize: AppType.caption,
              color: AppColors.textSecondary,
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: AppType.caption,
              fontWeight: FontWeight.bold,
              color: AppColors.text,
            ),
          ),
        ],
      ),
    );
  }
}
