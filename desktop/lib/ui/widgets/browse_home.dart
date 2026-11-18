import 'package:flutter/material.dart';

import '../../data/models/channel.dart';
import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';
import 'channel_logo.dart';

class BrowseHome extends StatelessWidget {
  const BrowseHome({super.key, required this.vm});

  final PlayerViewModel vm;

  @override
  Widget build(BuildContext context) {
    final recent = vm.recentChannels.take(8).toList();
    final favourites = vm.channels
        .where((channel) => vm.favorites.contains(channel.key))
        .take(12)
        .toList();
    final live = vm.channels
        .where((channel) => vm.epgService.getCurrentProgramme(channel) != null)
        .take(12)
        .toList();
    return Material(
      color: AppColors.background,
      child: ListView(
        padding: const EdgeInsets.all(AppSpace.xl),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  L10n.t.browseHome,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: AppType.title,
                  ),
                ),
              ),
              TextButton.icon(
                autofocus: true,
                onPressed: () => vm.setOverlay(OverlayType.playlist),
                icon: const Icon(Icons.list),
                label: Text(L10n.t.channelsGuide),
              ),
              IconButton(
                onPressed: vm.closeOverlay,
                icon: const Icon(Icons.close),
                tooltip: L10n.t.closeEsc,
              ),
            ],
          ),
          if (recent.isNotEmpty) _section(L10n.t.browseContinue, recent),
          if (favourites.isNotEmpty) _section(L10n.t.favourites, favourites),
          if (live.isNotEmpty) _section(L10n.t.onAirNow, live),
          if (vm.channels.isEmpty)
            Padding(
              padding: const EdgeInsets.only(top: AppSpace.lg),
              child: TextButton(
                onPressed: () => vm.setOverlay(OverlayType.settings),
                child: Text(L10n.t.noChannels),
              ),
            ),
        ],
      ),
    );
  }

  Widget _section(String title, List<Channel> channels) {
    return Padding(
      padding: const EdgeInsets.only(top: AppSpace.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: AppType.subtitle,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: AppSpace.sm),
          SizedBox(
            height: 128,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: channels.length,
              separatorBuilder: (_, _) => const SizedBox(width: AppSpace.md),
              itemBuilder: (context, index) {
                final channel = channels[index];
                final programme = vm.epgService.getCurrentProgramme(channel);
                return SizedBox(
                  width: 192,
                  child: Material(
                    color: AppColors.surfaceLight,
                    borderRadius: BorderRadius.circular(AppRadius.control),
                    clipBehavior: Clip.antiAlias,
                    child: InkWell(
                      onTap: () {
                        if (vm.playbackInfo.channel?.key != channel.key ||
                            vm.playbackInfo.archive != null) {
                          vm.play(channel);
                        }
                        vm.closeOverlay();
                      },
                      child: Padding(
                        padding: const EdgeInsets.all(AppSpace.md),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            ChannelLogo(channel, width: 68, height: 38),
                            const Spacer(),
                            Text(
                              channel.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            if (programme != null) ...[
                              Text(
                                programme.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: AppColors.textSecondary,
                                ),
                              ),
                              const SizedBox(height: AppSpace.xs),
                              LinearProgressIndicator(
                                value: programme.progress,
                                minHeight: 2,
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
