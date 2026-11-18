import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

import '../../l10n/l10n.dart';
import '../models/channel.dart';
import '../models/settings_model.dart';
import 'mpv_diagnostics.dart';
import 'playback_options.dart';
import 'stream_resolver.dart';
import 'public_tv.dart';

typedef StreamFormat = ({
  String? videoCodec,
  String? audioCodec,
  double? fps,
  double? videoBitrate,
  double? audioBitrate,
  String? renderer,
  String? decoder,
  int? droppedFrames,
  double? bufferSeconds,
  int? audioChannels,
  int? sampleRate,
});

class PlaybackInfo {
  final int attemptId;
  final Channel? channel;
  final int variantIndex;
  final bool isPlaying;
  final bool isBuffering;
  final Duration position;
  final String? error;
  final Programme? archive;
  final int? width;
  final int? height;
  final StreamFormat? format;

  const PlaybackInfo({
    this.attemptId = 0,
    this.channel,
    this.variantIndex = 0,
    this.isPlaying = false,
    this.isBuffering = false,
    this.position = Duration.zero,
    this.error,
    this.archive,
    this.width,
    this.height,
    this.format,
  });

  PlaybackInfo copyWith({
    int? attemptId,
    Channel? channel,
    int? variantIndex,
    bool? isPlaying,
    bool? isBuffering,
    Duration? position,
    String? error,
    Programme? archive,
    int? width,
    int? height,
    StreamFormat? format,
  }) => PlaybackInfo(
    attemptId: attemptId ?? this.attemptId,
    channel: channel ?? this.channel,
    variantIndex: variantIndex ?? this.variantIndex,
    isPlaying: isPlaying ?? this.isPlaying,
    isBuffering: isBuffering ?? this.isBuffering,
    position: position ?? this.position,
    error: error ?? this.error,
    archive: archive ?? this.archive,
    width: width ?? this.width,
    height: height ?? this.height,
    format: format ?? this.format,
  );
}

class PlayerService {
  late final Player player;
  late final VideoController controller;
  bool _isInitialized = false;
  bool get isInitialized => _isInitialized;
  int cacheSeconds = 20;

  PlaybackInfo _info = const PlaybackInfo();
  final _stateController = StreamController<PlaybackInfo>.broadcast();

  Stream<PlaybackInfo> get stateStream => _stateController.stream;

  final List<StreamSubscription> _subscriptions = [];
  bool _forcedFormat = false;
  bool _acceptPlayerEvents = false;
  int _requestedAttempt = 0;
  Future<void> _pendingOpen = Future.value();
  Timer? _formatTimer;
  bool _samplingFormat = false;
  Future<void>? _shutdown;
  final _diagnosticsEnabled =
      (Platform.isLinux || Platform.isWindows) &&
      Platform.environment['SIMPLETV_MPV_DIAGNOSTICS'] == '1';
  final _reportedMpvMessages = <String>{};
  bool _reportedProgress = false;
  String? _renderer;

  void _diagnostic(String message) {
    if (_diagnosticsEnabled) {
      stderr.writeln('[SimpleTV/mpv #${_info.attemptId}] $message');
    }
  }

  void reportAttemptFailure(int attemptId, {required bool timedOut}) {
    if (!_diagnosticsEnabled) return;
    stderr.writeln(
      '[SimpleTV/mpv #$attemptId] '
      '${timedOut ? 'playback progress timed out' : 'open failed'}; switching source if available',
    );
  }

  void _update(PlaybackInfo Function(PlaybackInfo) update) {
    if (!_acceptPlayerEvents || _stateController.isClosed) return;
    _info = update(_info);
    if (!_stateController.isClosed) _stateController.add(_info);
  }

  void initialize() {
    if (_isInitialized) return;
    player = Player(
      configuration: PlayerConfiguration(
        bufferSize: 32 * 1024 * 1024, // 32MB буфер
        logLevel: _diagnosticsEnabled ? MPVLogLevel.v : MPVLogLevel.error,
      ),
    );

    controller = VideoController(
      player,
      configuration: const VideoControllerConfiguration(
        // Вывод кадра в текстуру — через GPU. Аппаратный декодер включаем в
        // безопасном режиме: auto-safe сам откатывается на software, если
        // конкретный поток или кодек не поддерживается драйвером.
        enableHardwareAcceleration: true,
        hwdec: 'auto-safe',
      ),
    );
    if (Platform.isLinux) unawaited(_readRenderer());

    _subscriptions.addAll([
      player.stream.playing.listen(
        (playing) => _update((info) => info.copyWith(isPlaying: playing)),
      ),
      player.stream.buffering.listen(
        (buffering) => _update((info) => info.copyWith(isBuffering: buffering)),
      ),
      player.stream.position.listen(
        (pos) => _update((info) => info.copyWith(position: pos)),
      ),
      player.stream.width.listen(
        (width) => _update((info) => info.copyWith(width: width)),
      ),
      player.stream.height.listen(
        (height) => _update((info) => info.copyWith(height: height)),
      ),
    ]);
    if (_diagnosticsEnabled) {
      _subscriptions.addAll([
        player.stream.log.listen((log) {
          if (!_acceptPlayerEvents) return;
          final diagnosis = classifyMpvMessage(log.text);
          if (diagnosis == null &&
              !['error', 'fatal', 'warn'].contains(log.level)) {
            return;
          }
          final component =
              RegExp(r'^[a-zA-Z0-9_-]{1,40}$').hasMatch(log.prefix)
              ? log.prefix
              : 'mpv';
          final message =
              '$component: ${diagnosis ?? 'unclassified backend warning/error'}';
          if (_reportedMpvMessages.add(message)) _diagnostic(message);
        }),
        player.stream.position.listen((position) {
          if (_acceptPlayerEvents &&
              !_reportedProgress &&
              position > Duration.zero) {
            _reportedProgress = true;
            _diagnostic('playback position advanced');
          }
        }),
      ]);
    }
    // mpv log errors have no media identity and can arrive after a zap.
    // Failover uses open errors and the watchdog for the active attempt.

    _isInitialized = true;
  }

  Future<void> _readRenderer() async {
    try {
      await controller.platform.future;
      _renderer = await const MethodChannel('simpletv/linux')
          .invokeMethod<String>('videoRenderer');
      if (_renderer != null) _diagnostic('Linux video renderer: $_renderer');
    } catch (_) {
      // Older runners can still play without reporting the texture backend.
    }
  }

  Future<void> play(
    Channel channel, {
    required int attemptId,
    int variantIndex = 0,
    Programme? archive,
    String? defaultUserAgent,
  }) {
    if (_shutdown != null) return _shutdown!;
    _requestedAttempt = attemptId;
    _acceptPlayerEvents = false;
    _formatTimer?.cancel();
    return _pendingOpen = _pendingOpen.then((_) async {
      if (attemptId != _requestedAttempt || _stateController.isClosed) return;
      _info = PlaybackInfo(
        attemptId: attemptId,
        channel: channel,
        variantIndex: variantIndex,
        archive: archive,
        isBuffering: true,
      );
      _reportedMpvMessages.clear();
      _reportedProgress = false;
      _stateController.add(_info);
      try {
        await player.stop();
        if (attemptId != _requestedAttempt) return;
        await _open(
          channel,
          attemptId: attemptId,
          variantIndex: variantIndex,
          archive: archive,
          defaultUserAgent: defaultUserAgent,
        );
      } catch (error) {
        if (attemptId != _requestedAttempt || _stateController.isClosed) return;
        _diagnostic('open raised ${error.runtimeType}');
        _acceptPlayerEvents = false;
        _info = _info.copyWith(
          error: L10n.t.openStreamError,
          isPlaying: false,
          isBuffering: false,
        );
        _stateController.add(_info);
      }
    });
  }

  Future<void> _open(
    Channel channel, {
    required int attemptId,
    required int variantIndex,
    Programme? archive,
    String? defaultUserAgent,
  }) async {
    final variant = channel.variants[variantIndex];

    final resolved = StreamResolver.resolve(variant.url, variant.kind);
    var playUrl = resolved.url;

    if (archive != null) {
      final catchup = StreamResolver.catchupUrl(
        variant,
        archive.start,
        archive.stop,
      );
      if (catchup == null) throw StateError('Archive unavailable');
      playUrl = catchup;
    }
    if (PublicTv.needsResolution(playUrl)) {
      playUrl = await PublicTv.resolve(playUrl);
      if (attemptId != _requestedAttempt) return;
    }

    final effectiveKind = resolved.kind != StreamKind.generic
        ? resolved.kind
        : variant.kind;
    final effectiveUa =
        (variant.userAgent != null && variant.userAgent!.isNotEmpty)
        ? variant.userAgent!
        : (effectiveKind == StreamKind.wink
              ? UserAgents.winkTizen
              : (effectiveKind == StreamKind.beeline
                    ? UserAgents.beelineManifest
                    : (defaultUserAgent ?? UserAgents.defaultGlobal)));

    final headers = <String, String>{
      'User-Agent': effectiveUa,
      if (variant.referer != null) 'Referer': variant.referer!,
    };

    final clearKey = resolved.clearKey ?? variant.clearKey;
    final format = PlaybackOptions.lavfFormat(playUrl);
    _diagnostic(
      'source=${effectiveKind.name} format=${format.isEmpty ? 'auto' : format} '
      'scheme=${playUrl.startsWith('https://')
          ? 'https'
          : playUrl.startsWith('http://')
          ? 'http'
          : 'other'} '
      'CENC key supplied=${clearKey?.isNotEmpty == true}',
    );
    if (Platform.isLinux &&
        effectiveKind == StreamKind.beeline &&
        format == 'dash') {
      final manifest = Uri.tryParse(playUrl);
      if (manifest != null &&
          (manifest.scheme == 'http' || manifest.scheme == 'https')) {
        final target = await StreamResolver.followManifestRedirects(
          manifest,
          headers,
        );
        if (attemptId != _requestedAttempt) return;
        if (target != manifest) {
          _diagnostic('Beeline DASH manifest redirected; opening CDN URL');
          playUrl = target.toString();
        } else {
          _diagnostic('Beeline DASH manifest: using original URL');
        }
      }
    }
    await _applyStreamProperties(
      userAgent: effectiveUa,
      referer: variant.referer,
      clearKey: clearKey,
      forceDash: clearKey?.isNotEmpty == true && format == 'dash',
    );

    if (attemptId != _requestedAttempt) return;
    _acceptPlayerEvents = true;
    await player.open(Media(playUrl, httpHeaders: headers), play: true);
    if (attemptId == _requestedAttempt) {
      _diagnostic('loadfile command returned');
    }
    final native = player.platform;
    if (attemptId == _requestedAttempt && native is NativePlayer) {
      unawaited(_sampleFormat(native, attemptId));
      _formatTimer = Timer.periodic(const Duration(seconds: 2), (_) {
        unawaited(_sampleFormat(native, attemptId));
      });
    }
  }

  Future<void> _sampleFormat(NativePlayer native, int attemptId) async {
    if (!_acceptPlayerEvents ||
        attemptId != _requestedAttempt ||
        _samplingFormat) {
      return;
    }
    _samplingFormat = true;
    try {
      // Плеер может быть закрыт прямо во время опроса свойств — это не ошибка воспроизведения
      final values = await Future.wait([
        native.getProperty(
          'current-tracks/video/codec',
          waitForInitialization: false,
        ),
        native.getProperty(
          'current-tracks/audio/codec',
          waitForInitialization: false,
        ),
        native.getProperty('container-fps', waitForInitialization: false),
        native.getProperty('video-bitrate', waitForInitialization: false),
        native.getProperty('audio-bitrate', waitForInitialization: false),
        native.getProperty('hwdec-current', waitForInitialization: false),
        native.getProperty('frame-drop-count', waitForInitialization: false),
        native.getProperty(
          'demuxer-cache-duration',
          waitForInitialization: false,
        ),
      ]).catchError((_) => List<String>.filled(8, ''));
      if (!_acceptPlayerEvents || attemptId != _requestedAttempt) return;
      final audio = player.state.audioParams;
      final StreamFormat format = (
        videoCodec: values[0].isEmpty ? null : values[0],
        audioCodec: values[1].isEmpty ? null : values[1],
        fps: _positiveNumber(values[2]),
        videoBitrate: _positiveNumber(values[3]),
        audioBitrate: _positiveNumber(values[4]),
        renderer: _renderer,
        decoder: values[0].isEmpty
            ? null
            : values[5].isEmpty || values[5] == 'no'
            ? 'Software'
            : values[5],
        droppedFrames: int.tryParse(values[6]),
        bufferSeconds: _positiveNumber(values[7]),
        audioChannels: audio.channelCount,
        sampleRate: audio.sampleRate,
      );
      if (format != _info.format) {
        _diagnostic(
          'tracks: video=${_safeCodec(format.videoCodec)} audio=${_safeCodec(format.audioCodec)} '
          'decoder=${format.decoder ?? '-'} '
          'size=${_info.width ?? 0}x${_info.height ?? 0}',
        );
        _update((info) => info.copyWith(format: format));
      }
    } finally {
      _samplingFormat = false;
    }
  }

  static String _safeCodec(String? codec) =>
      codec != null && RegExp(r'^[a-zA-Z0-9_-]{1,24}$').hasMatch(codec)
      ? codec
      : '-';

  static double? _positiveNumber(String value) {
    final parsed = double.tryParse(value);
    return parsed != null && parsed.isFinite && parsed > 0 ? parsed : null;
  }

  Future<void> _applyStreamProperties({
    required String userAgent,
    required String? referer,
    required String? clearKey,
    required bool forceDash,
  }) async {
    final platform = player.platform as dynamic;
    if (platform == null) return;

    Future<void> set(String key, String value) async {
      try {
        await platform.setProperty(key, value);
      } catch (_) {
        _diagnostic('could not set mpv property $key');
      }
    }

    // media_kit при старте сам пишет demuxer-lavf-o с protocol_whitelist.
    // Пустая строка или один decryption_key затирают этот список, и FFmpeg
    // отказывается открывать сегменты HLS/DASH — канал «не грузится».
    await set('ytdl', 'no');
    await set('cache-on-disk', 'no');
    // Как --http-reconnect и --adaptive-logic=highest в vlcrc прародителя.
    await set('cache-pause-initial', 'no');
    await set('cache-secs', '$cacheSeconds');
    await set('network-timeout', '20');
    await set('demuxer-readahead-secs', '$cacheSeconds');
    await set('hls-bitrate', 'max');
    await set('user-agent', userAgent);
    await set('referrer', referer ?? '');
    await set('stream-lavf-o', PlaybackOptions.streamOptions);
    await set('demuxer-lavf-o', PlaybackOptions.lavfOptions(clearKey));

    // Формат не навязываем обычным HLS: у VLC свой adaptive-демуксер,
    // у mpv свой. lavf dash нужен только чтобы cenc_decryption_key
    // попал в манифест ClearKey, как $OPT:adaptive-use-avdemux.
    if (forceDash) {
      await set('demuxer-lavf-format', 'dash');
      _forcedFormat = true;
    } else if (_forcedFormat) {
      await set('demuxer-lavf-format', '');
      _forcedFormat = false;
    }
  }

  Future<void> togglePlayPause() async => await player.playOrPause();

  List<VideoTrack> get videoQualities => player.state.tracks.video
      .where((track) => track.h != null && track.h! > 0 && track.image != true)
      .toList();

  VideoTrack get videoTrack => player.state.track.video;

  Future<void> setVideoQuality(VideoTrack track) => player.setVideoTrack(track);

  Future<void> setVolume(double vol) async {
    await player.setVolume(vol.clamp(0.0, 100.0));
  }

  double get volume => player.state.volume;

  Future<void> setAspectRatio(String? ratio) async {
    try {
      await (player.platform as dynamic)?.setProperty(
        'video-aspect-override',
        ratio ?? '-1',
      );
    } catch (_) {
      _diagnostic('could not set aspect ratio $ratio');
    }
  }

  void dispose() => unawaited(shutdown());

  Future<void> shutdown() => _shutdown ??= _shutdownPlayer();

  Future<void> _shutdownPlayer() async {
    _requestedAttempt++;
    _acceptPlayerEvents = false;
    _formatTimer?.cancel();
    await Future.wait(
      _subscriptions.map((subscription) => subscription.cancel()),
    );
    _subscriptions.clear();
    try {
      await _pendingOpen;
    } finally {
      try {
        if (_isInitialized) await player.dispose();
        _diagnostic('player disposed before closing the window');
      } finally {
        await _stateController.close();
      }
    }
  }
}
