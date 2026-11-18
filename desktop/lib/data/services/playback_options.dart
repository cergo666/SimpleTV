/// Опции libavformat/mpv, без которых живые HLS и DASH на десктопе не стартуют.
class PlaybackOptions {
  /// Тот же набор, что media_kit ставит при создании плеера, плюс httpproxy.
  /// Вложенные плейлисты и сегменты ходят по http/https/tls; без whitelist
  /// FFmpeg отвечает «Protocol not on whitelist» и канал не открывается.
  static const protocols =
      'udp,rtp,tcp,tls,data,file,http,https,crypto,httpproxy';

  /// Эквивалент `--http-reconnect` из vlcrc рабочего simpleTV.
  static const streamOptions =
      'reconnect=1,reconnect_streamed=1,reconnect_on_network_error=1,'
      'reconnect_delay_max=5,timeout=20000000';

  static String lavfOptions(String? clearKey) {
    final base =
        'seg_max_retry=5,strict=experimental,allowed_extensions=ALL,'
        'protocol_whitelist=[$protocols]';
    final key = clearKey?.trim() ?? '';
    if (key.isEmpty) return base;
    // decryption_key — старые сборки FFmpeg, cenc_decryption_key — DASH CENC в FFmpeg 6+.
    return '$base,decryption_key=$key,cenc_decryption_key=$key';
  }

  /// Подсказка демуксера по адресу. Пустая строка — автоопределение.
  static String lavfFormat(String url) {
    final clean = url.split(r'$OPT').first.split('|').first;
    final path = Uri.tryParse(clean)?.path.toLowerCase() ?? clean.toLowerCase();
    if (path.endsWith('.m3u8')) return 'hls';
    if (path.endsWith('.mpd')) return 'dash';
    if (path.endsWith('.ts')) return 'mpegts';
    return '';
  }
}
