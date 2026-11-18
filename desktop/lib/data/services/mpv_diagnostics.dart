/// Translate libmpv messages into shareable diagnostics. The original message
/// can contain request URLs, headers or keys, so it never goes to the console.
String? classifyMpvMessage(String message) {
  final text = message.toLowerCase();
  final status = RegExp(
    r'\b(?:http(?:/\d(?:\.\d)?| error| status(?: code)?)?|server returned)[: =]+([345]\d\d)\b',
  ).firstMatch(text)?.group(1);
  if (status != null) return 'HTTP $status';
  if (text.contains('failed to resolve') ||
      text.contains('getaddrinfo') ||
      text.contains('name or service not known')) {
    return 'DNS lookup failed';
  }
  if ((text.contains('tls') ||
          text.contains('ssl') ||
          text.contains('certificate')) &&
      (text.contains('error') ||
          text.contains('failed') ||
          text.contains('unable') ||
          text.contains('invalid'))) {
    return 'TLS error';
  }
  if (text.contains('timed out') ||
      text.contains('connection timeout') ||
      text.contains('operation timeout')) {
    return 'network timeout';
  }
  if (text.contains('connection refused') ||
      text.contains('connection reset') ||
      text.contains('network is unreachable')) {
    return 'connection failed';
  }
  if (text.contains('not on whitelist') ||
      text.contains('protocol not found')) {
    return 'protocol rejected';
  }
  if (text.contains('not found') && text.contains('demuxer')) {
    return 'demuxer unavailable';
  }
  if (text.contains('error setting option') ||
      text.contains('option not found')) {
    return 'demuxer option rejected';
  }
  if (text.contains('decryption') ||
      text.contains('encrypted') ||
      text.contains('incorrect key') ||
      text.contains('cenc')) {
    return 'CENC/decryption message';
  }
  if (text.contains('could not find codec') ||
      text.contains('decoder not found')) {
    return 'decoder unavailable';
  }
  if (text.contains('error while decoding') ||
      text.contains('invalid nal') ||
      text.contains('non-existing pps') ||
      text.contains('no frame!')) {
    return 'video bitstream/decoder error';
  }
  if (text.contains('fragment') || text.contains('segment')) {
    return 'segment/fragment message';
  }
  if (text.contains('manifest') || text.contains('mpd')) {
    return 'DASH manifest message';
  }
  if (text.contains('failed to open') || text.contains('error opening')) {
    return 'input open failed';
  }
  if (text.contains('end of file') || text.contains('eof')) {
    return 'end of input';
  }
  return null;
}
