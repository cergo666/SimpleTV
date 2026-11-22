import 'package:flutter/material.dart';

import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';

class UpdateDialog extends StatelessWidget {
  final PlayerViewModel vm;

  const UpdateDialog({super.key, required this.vm});

  @override
  Widget build(BuildContext context) {
    final update = vm.update;
    if (update == null) return const SizedBox.shrink();
    final busy = vm.updateBusy;
    final progress = vm.updateProgress;

    return Positioned.fill(
      child: ColoredBox(
        color: Colors.black.withValues(alpha: 0.68),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Material(
              color: AppColors.overlayBackground,
              borderRadius: BorderRadius.circular(AppRadius.card),
              child: Padding(
                padding: const EdgeInsets.all(AppSpace.xl),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      L10n.t.updateAvailableTitle,
                      style: const TextStyle(
                        color: AppColors.text,
                        fontSize: AppType.title,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: AppSpace.md),
                    Text(
                      L10n.t.updateAvailableText(update.version),
                      style: const TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: AppType.body,
                      ),
                    ),
                    if (busy != null) ...[
                      const SizedBox(height: AppSpace.md),
                      Text(
                        busy,
                        style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: AppType.body,
                        ),
                      ),
                      if (progress != null) ...[
                        const SizedBox(height: AppSpace.sm),
                        LinearProgressIndicator(
                          value: progress.clamp(0.0, 1.0).toDouble(),
                          color: AppColors.accentLight,
                          backgroundColor: AppColors.border,
                        ),
                      ],
                    ],
                    const SizedBox(height: AppSpace.xl),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        TextButton(
                          onPressed: busy == null ? vm.dismissUpdate : null,
                          child: Text(L10n.t.updateLater),
                        ),
                        const SizedBox(width: AppSpace.sm),
                        FilledButton(
                          autofocus: true,
                          onPressed: busy == null
                              ? vm.downloadAndInstallUpdate
                              : null,
                          child: Text(L10n.t.updateDownload),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
