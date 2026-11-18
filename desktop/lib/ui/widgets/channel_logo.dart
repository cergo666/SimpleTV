import 'package:flutter/material.dart';

import '../../data/models/channel.dart';
import '../../data/services/logo_service.dart';

class ChannelLogo extends StatelessWidget {
  const ChannelLogo(
    this.channel, {
    super.key,
    required this.width,
    required this.height,
    this.placeholder = const SizedBox.shrink(),
  });

  final Channel channel;
  final double width;
  final double height;
  final Widget placeholder;

  @override
  Widget build(BuildContext context) {
    final fallback = LogoService.findCatalogLogo(channel.name);
    final primary = channel.logoUrl;
    final urls = {
      if (primary != null && primary.isNotEmpty) primary,
      ?fallback,
    }.toList();

    Widget load(int index) {
      if (index == urls.length) return placeholder;
      return Image.network(
        urls[index],
        width: width,
        height: height,
        fit: BoxFit.contain,
        errorBuilder: (context, error, stackTrace) => load(index + 1),
      );
    }

    return load(0);
  }
}
