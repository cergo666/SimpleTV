import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/ui/views/main_screen.dart';

void main() {
  testWidgets('typing in a search field leaves player shortcuts inactive', (
    tester,
  ) async {
    final keyboard = FocusNode();
    final search = FocusNode();
    addTearDown(keyboard.dispose);
    addTearDown(search.dispose);
    final observed = <bool>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: KeyboardListener(
            focusNode: keyboard,
            autofocus: true,
            onKeyEvent: (event) {
              if (event is KeyDownEvent) observed.add(isEditingText());
            },
            child: TextField(focusNode: search),
          ),
        ),
      ),
    );
    await tester.tap(find.byType(TextField));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.keyR);
    keyboard.requestFocus();
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.keyR);
    expect(observed, [true, false]);
    await tester.pumpWidget(const SizedBox());
  });
}
