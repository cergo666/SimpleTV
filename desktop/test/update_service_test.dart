import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/services/update_service.dart';

void main() {
  test('compares numeric version segments', () {
    expect(UpdateService.isNewer('1.3.0', '1.2.0'), isTrue);
    expect(UpdateService.isNewer('v1.2.1', '1.2.0'), isTrue);
    expect(UpdateService.isNewer('1.2.0', '1.1.9'), isTrue);
    expect(UpdateService.isNewer('1.2.0', '1.2.0'), isFalse);
    expect(UpdateService.isNewer('1.1.9', '1.2.0'), isFalse);
  });

  test('ignores prerelease suffix for comparison', () {
    expect(UpdateService.isNewer('1.3.0-beta', '1.2.0'), isTrue);
    expect(UpdateService.isNewer('1.2.0-beta', '1.2.0'), isFalse);
  });
}
