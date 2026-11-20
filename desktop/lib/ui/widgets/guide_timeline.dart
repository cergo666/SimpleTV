import 'package:flutter/material.dart';

import '../../data/models/channel.dart';
import '../../data/services/stream_resolver.dart';
import '../../l10n/l10n.dart';
import '../theme/theme.dart';
import '../view_models/player_view_model.dart';
import 'channel_logo.dart';

class GuideTimeline extends StatefulWidget {
  const GuideTimeline({
    super.key,
    required this.vm,
    required this.channels,
    required this.onProgramme,
  });

  final PlayerViewModel vm;
  final List<Channel> channels;
  final void Function(Channel, Programme) onProgramme;

  @override
  State<GuideTimeline> createState() => _GuideTimelineState();
}

class _GuideTimelineState extends State<GuideTimeline> {
  static const _duration = Duration(hours: 2);
  static const _logoWidth = 70.0;

  DateTime? _anchor;
  late final ScrollController _scroll;

  @override
  void initState() {
    super.initState();
    final cursor = widget.vm.guideCursor;
    final index = widget.channels.indexWhere(
      (channel) => channel.key == cursor?.$1,
    );
    _anchor = cursor?.$2;
    _scroll = ScrollController(
      initialScrollOffset: index < 0 ? 0 : index * 55.0,
    );
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final vm = widget.vm;
    final channels = widget.channels;
    if (channels.isEmpty) {
      return Center(child: Text(L10n.t.noProgrammeNow));
    }
    final now = DateTime.now();
    final anchor = _anchor ?? now;
    final start = DateTime(
      anchor.year,
      anchor.month,
      anchor.day,
      anchor.hour,
      anchor.minute ~/ 30 * 30,
    );
    final cursor = vm.guideCursor;
    final selectedChannel =
        channels.where((channel) => channel.key == cursor?.$1).firstOrNull ??
        channels
            .where((channel) => channel.key == vm.playbackInfo.channel?.key)
            .firstOrNull ??
        channels.first;
    final selected =
        vm.epgService
            .getSchedule(selectedChannel)
            .where((programme) => programme.start == cursor?.$2)
            .firstOrNull ??
        vm.epgService.getCurrentProgramme(selectedChannel);
    final archiveAvailable =
        selected != null &&
        selectedChannel.variants.any(
          (variant) =>
              StreamResolver.catchupUrl(
                variant,
                selected.start,
                selected.stop,
              ) !=
              null,
        );
    void select(Channel channel, Programme programme) =>
        setState(() => vm.guideCursor = (channel.key, programme.start));
    final end = start.add(_duration);
    double fraction(DateTime time) =>
        (time.difference(start).inMilliseconds / _duration.inMilliseconds)
            .clamp(0.0, 1.0);
    String timeLabel(DateTime time) =>
        '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';

    return Padding(
      padding: const EdgeInsets.all(AppSpace.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (selected != null) ...[
            Text(
              selected.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: AppType.title,
                fontWeight: FontWeight.bold,
              ),
            ),
            Text(
              '${timeLabel(selected.start)}–${timeLabel(selected.stop)} · ${selectedChannel.name}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            if (selected.description?.isNotEmpty ?? false)
              Text(
                selected.description!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: AppColors.textSecondary),
              ),
          ],
          Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              IconButton(
                onPressed: () => setState(
                  () => _anchor = start.subtract(const Duration(minutes: 30)),
                ),
                tooltip: timeLabel(start.subtract(const Duration(minutes: 30))),
                icon: const Icon(Icons.chevron_left),
              ),
              TextButton(
                onPressed: () {
                  setState(() {
                    _anchor = null;
                    vm.guideCursor = null;
                  });
                  if (_scroll.hasClients) _scroll.jumpTo(0);
                },
                child: Text(L10n.t.browseNow),
              ),
              IconButton(
                onPressed: () => setState(
                  () => _anchor = start.add(const Duration(minutes: 30)),
                ),
                tooltip: timeLabel(start.add(const Duration(minutes: 30))),
                icon: const Icon(Icons.chevron_right),
              ),
              if (archiveAvailable)
                TextButton.icon(
                  onPressed: () {
                    vm.play(selectedChannel, archive: selected);
                    vm.closeOverlay();
                  },
                  icon: const Icon(Icons.replay),
                  label: Text(L10n.t.fromStart),
                ),
              if (selected != null)
                TextButton(
                  onPressed: () =>
                      widget.onProgramme(selectedChannel, selected),
                  child: Text(L10n.t.channelsGuide),
                ),
            ],
          ),
          const SizedBox(height: AppSpace.md),
          Padding(
            padding: const EdgeInsets.only(left: _logoWidth),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                for (var minutes = 0; minutes <= 120; minutes += 30)
                  Text(
                    timeLabel(start.add(Duration(minutes: minutes))),
                    style: const TextStyle(
                      fontSize: AppType.caption,
                      color: AppColors.textSecondary,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: AppSpace.sm),
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              itemCount: channels.length,
              itemExtent: 55,
              itemBuilder: (context, i) {
                final channel = channels[i];
                final programmes = vm.epgService
                    .getSchedule(channel)
                    .where(
                      (p) => p.start.isBefore(end) && p.stop.isAfter(start),
                    );
                return Row(
                  children: [
                    SizedBox(
                      width: _logoWidth,
                      child: Tooltip(
                        message: channel.name,
                        child: Center(
                          child: ChannelLogo(channel, width: 48, height: 30),
                        ),
                      ),
                    ),
                    Expanded(
                      child: LayoutBuilder(
                        builder: (context, constraints) {
                          final width = constraints.maxWidth;
                          return Stack(
                            fit: StackFit.expand,
                            children: [
                              for (final p in programmes) ...[
                                Positioned(
                                  left: width * fraction(p.start),
                                  width:
                                      width *
                                      (fraction(p.stop) - fraction(p.start)),
                                  top: 4,
                                  bottom: 4,
                                  child: Builder(
                                    builder: (context) {
                                      final isNow =
                                          !now.isBefore(p.start) &&
                                          now.isBefore(p.stop);
                                      return Tooltip(
                                        message:
                                            '${timeLabel(p.start)}–${timeLabel(p.stop)} · ${p.title}',
                                        child: Material(
                                          color: isNow
                                              ? AppColors.accent.withValues(
                                                  alpha: 0.28,
                                                )
                                              : AppColors.surfaceLight,
                                          shape: RoundedRectangleBorder(
                                            borderRadius: BorderRadius.circular(
                                              AppRadius.control,
                                            ),
                                            side: isNow
                                                ? BorderSide(
                                                    color: AppColors.accent,
                                                    width: 1.5,
                                                  )
                                                : BorderSide.none,
                                          ),
                                          clipBehavior: Clip.antiAlias,
                                          child: InkWell(
                                            onTap: () => select(channel, p),
                                            onDoubleTap: () =>
                                                widget.onProgramme(channel, p),
                                            onFocusChange: (focused) {
                                              if (focused) select(channel, p);
                                            },
                                            autofocus:
                                                channel.key == cursor?.$1 &&
                                                p.start == cursor?.$2,
                                            child: Padding(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    horizontal: AppSpace.sm,
                                                  ),
                                              child: Align(
                                                alignment: Alignment.centerLeft,
                                                child: Text(
                                                  p.title,
                                                  maxLines: 2,
                                                  overflow:
                                                      TextOverflow.ellipsis,
                                                  style: const TextStyle(
                                                    fontSize: AppType.caption,
                                                    color: AppColors.text,
                                                  ),
                                                ),
                                              ),
                                            ),
                                          ),
                                        ),
                                      );
                                    },
                                  ),
                                ),
                              ],
                              if (!now.isBefore(start) && now.isBefore(end))
                                Positioned(
                                  left: width * fraction(now),
                                  top: 0,
                                  bottom: 0,
                                  child: const IgnorePointer(
                                    child: ColoredBox(
                                      color: AppColors.green,
                                      child: SizedBox(width: AppSpace.xs),
                                    ),
                                  ),
                                ),
                            ],
                          );
                        },
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
