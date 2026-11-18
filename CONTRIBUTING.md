# Участие в разработке SimpleTV

## Структура

- `core/` — общая Kotlin/JVM-логика: модели, M3U, EPG, сопоставление каналов, проверка потоков.
- `app/` — Android и Android TV (Jetpack Compose, Media3).
- `desktop/` — Windows и Linux (Flutter, `media_kit`/mpv).
- `buildSrc/` — Gradle-логика подписи и защиты релизных строк.
- `packaging/` — скрипты упаковки Windows/Linux.

## Требования

- JDK 21, Android SDK с NDK `28.2.13676358` и CMake `3.22.1`.
- Flutter 3.47.9 для `desktop/`.
- Для Windows-сборки — Visual Studio с C++ toolchain, для Linux — Clang, CMake, Ninja, GTK 3, libmpv.

## Проверки перед коммитом

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug ktlintCheck
```

```bash
cd desktop
flutter pub get
dart format --output=none --set-exit-if-changed lib test tool
flutter analyze
flutter test --exclude-tags native-playback
```

Тесты с реальным mpv запускаются отдельно и требуют bundled libmpv:

```bash
flutter test --tags native-playback
```

## Правила

- Не увеличивайте `minSdk` без необходимости: Android 6 (API 23) официально поддерживается.
- Пользовательские строки Android складывайте в ресурсы, а не в код.
- Новые сетевые запросы должны иметь таймауты и не выполняться в главном потоке.
- Не коммитьте keystore, `local.properties`, кэши и дампы падений.
- Один коммит — одна логическая правка с осмысленным сообщением.
