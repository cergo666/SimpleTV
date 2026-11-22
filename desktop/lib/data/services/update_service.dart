import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

class AppUpdate {
  final String version;
  final Map<String, String> assets;
  final String? checksumsUrl;
  final String releaseUrl;

  const AppUpdate({
    required this.version,
    required this.assets,
    required this.checksumsUrl,
    required this.releaseUrl,
  });
}

/// Проверка и установка обновлений с GitHub Releases.
class UpdateService {
  static const _api =
      'https://api.github.com/repos/cergo666/SimpleTV/releases/latest';
  final http.Client _client = http.Client();

  Future<AppUpdate?> check() async {
    final current = (await PackageInfo.fromPlatform()).version;
    final response = await _client
        .get(
          Uri.parse(_api),
          headers: {
            'Accept': 'application/vnd.github+json',
            'User-Agent': 'SimpleTV/$current',
          },
        )
        .timeout(const Duration(seconds: 12));
    if (response.statusCode != 200) return null;
    final release = jsonDecode(response.body) as Map<String, dynamic>;
    final version = (release['tag_name'] as String? ?? '').replaceFirst(
      RegExp(r'^v'),
      '',
    );
    if (version.isEmpty || !isNewer(version, current)) return null;

    final assets = <String, String>{};
    String? checksumsUrl;
    for (final raw in (release['assets'] as List<dynamic>? ?? const [])) {
      final asset = raw as Map<String, dynamic>;
      final name = asset['name'] as String? ?? '';
      final url = asset['browser_download_url'] as String? ?? '';
      if (name.isEmpty || url.isEmpty) continue;
      if (name == 'SHA256SUMS') {
        checksumsUrl = url;
      } else {
        assets[name] = url;
      }
    }
    return AppUpdate(
      version: version,
      assets: assets,
      checksumsUrl: checksumsUrl,
      releaseUrl: release['html_url'] as String? ?? '',
    );
  }

  Future<File> download(
    AppUpdate update,
    String assetName,
    void Function(double progress) onProgress,
  ) async {
    final url = update.assets[assetName];
    if (url == null) throw StateError('Release asset not found: $assetName');
    final directory = await getTemporaryDirectory();
    final target = File('${directory.path}${Platform.pathSeparator}$assetName');
    final expected = await _checksum(update, assetName);
    if (expected == null) {
      throw const FormatException('Missing SHA256SUMS');
    }
    if (await target.exists() && await _sha256(target) == expected) {
      onProgress(1);
      return target;
    }

    final temporary = File('${target.path}.part');
    final request = http.Request('GET', Uri.parse(url));
    request.headers['User-Agent'] = 'SimpleTV/${update.version}';
    final response = await _client.send(request);
    if (response.statusCode != 200) {
      throw HttpException('HTTP ${response.statusCode}', uri: Uri.parse(url));
    }
    final total = response.contentLength ?? -1;
    var written = 0;
    final sink = temporary.openWrite();
    try {
      await for (final chunk in response.stream) {
        sink.add(chunk);
        written += chunk.length;
        if (total > 0) {
          onProgress((written / total).clamp(0, 1).toDouble());
        }
      }
    } finally {
      await sink.close();
    }
    if (await _sha256(temporary) != expected) {
      await temporary.delete();
      throw const FormatException('Update checksum mismatch');
    }
    if (await target.exists()) await target.delete();
    await temporary.rename(target.path);
    onProgress(1);
    return target;
  }

  /// Запускает установщик и завершает текущий процесс.
  Future<bool> install(
    AppUpdate update, {
    void Function(double progress)? onProgress,
  }) async {
    final progress = onProgress ?? (_) {};
    if (Platform.isWindows) {
      final portable = Platform.resolvedExecutable.toLowerCase().contains(
        '${Platform.pathSeparator}portable${Platform.pathSeparator}',
      );
      final assetName =
          'SimpleTV-${update.version}-${portable ? 'Portable.exe' : 'Setup.exe'}';
      final file = await download(update, assetName, progress);
      if (portable) {
        await Process.start(
          file.path,
          const [],
          mode: ProcessStartMode.detached,
        );
      } else {
        await Process.start(file.path, const [
          '/SILENT',
          '/SUPPRESSMSGBOXES',
          '/NORESTART',
          '/CLOSEAPPLICATIONS',
        ], mode: ProcessStartMode.detached);
      }
      exit(0);
    }

    if (Platform.isLinux) {
      final current = Platform.environment['APPIMAGE'];
      if (current == null || current.isEmpty) return false;
      final assetName = 'SimpleTV-${update.version}-x86_64.AppImage';
      final file = await download(update, assetName, progress);
      await Process.run('chmod', ['+x', file.path]);
      final staged = '$current.new';
      await file.copy(staged);
      await Process.start(
        'sh',
        [
          '-c',
          'while kill -0 "\$PID" 2>/dev/null; do sleep 0.2; done; '
              'mv -f "\$NEW" "\$CURRENT"; chmod +x "\$CURRENT"; '
              'nohup "\$CURRENT" >/dev/null 2>&1 &',
        ],
        environment: {'PID': '$pid', 'NEW': staged, 'CURRENT': current},
        mode: ProcessStartMode.detached,
      );
      exit(0);
    }
    return false;
  }

  Future<String?> _checksum(AppUpdate update, String assetName) async {
    final url = update.checksumsUrl;
    if (url == null) return null;
    final response = await _client
        .get(
          Uri.parse(url),
          headers: {'User-Agent': 'SimpleTV/${update.version}'},
        )
        .timeout(const Duration(seconds: 12));
    if (response.statusCode != 200) return null;
    for (final line in const LineSplitter().convert(response.body)) {
      final parts = line.trim().split(RegExp(r'\s+'));
      if (parts.length >= 2 && parts.last == assetName) {
        return parts.first.toLowerCase();
      }
    }
    return null;
  }

  Future<String> _sha256(File file) async {
    final digest = await sha256.bind(file.openRead()).first;
    return digest.toString();
  }

  static bool isNewer(String remote, String current) {
    List<int> parts(String value) => value
        .replaceFirst(RegExp(r'^v'), '')
        .split('-')
        .first
        .split('.')
        .map((part) => int.tryParse(part) ?? 0)
        .toList();
    final remoteParts = parts(remote);
    final currentParts = parts(current);
    for (var i = 0; i < remoteParts.length || i < currentParts.length; i++) {
      final r = i < remoteParts.length ? remoteParts[i] : 0;
      final c = i < currentParts.length ? currentParts[i] : 0;
      if (r != c) return r > c;
    }
    return false;
  }

  void dispose() => _client.close();
}
