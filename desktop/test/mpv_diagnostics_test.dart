import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/services/mpv_diagnostics.dart';

void main() {
  test('mpv diagnostics emit an HTTP status without the request URL', () {
    expect(
      classifyMpvMessage(
        'Failed to open https://example.invalid/manifest.mpd?token=secret: '
        'HTTP error 403 Forbidden',
      ),
      'HTTP 403',
    );
  });

  test('mpv diagnostics keep key material and unrecognized details out', () {
    expect(
      classifyMpvMessage('Option cenc_decryption_key=secret'),
      'CENC/decryption message',
    );
    expect(classifyMpvMessage('Authorization: Bearer secret'), isNull);
  });

  test('mpv diagnostics distinguish network options from errors', () {
    expect(classifyMpvMessage('Setting option network-timeout=20'), isNull);
    expect(classifyMpvMessage('Setting protocol whitelist=http,tls'), isNull);
    expect(classifyMpvMessage('TLS handshake failed'), 'TLS error');
  });
}
