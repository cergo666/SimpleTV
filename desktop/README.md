# SimpleTV Desktop

Версия для Windows и Linux на Flutter и `media_kit`/mpv. Команды сборки и тестирования описаны в [README проекта](../README.md#сборка).

Интерфейс локализован: строки лежат в `lib/l10n/app_ru.arb` и `lib/l10n/app_en.arb`, файлы `app_localizations*.dart` генерируются командой `flutter gen-l10n`. Язык выбирается по локали системы (русский или английский, иначе английский).

Автообновление проверяет GitHub Releases и сверяет загрузки с `SHA256SUMS`. Windows использует `Setup.exe` (или `Portable.exe` для портативной сборки), Linux — AppImage. Проверка также доступна вручную в настройках.
