# SimpleTV

Кроссплатформенный IPTV-плеер для Android, Android TV, Windows и Linux. Android-клиент написан на Kotlin, Jetpack Compose и Media3, десктопный — на Flutter и `media_kit`/mpv.

Пакет Android: `io.mediacast.simpletv`. Готовые сборки — в [Releases](https://github.com/cergo666/SimpleTV/releases).

## Возможности

- **Источники.** Встроенные Beeline, Wink, Cinerama, Sputnik24 и «Витрина ТВ» дополняются онлайн-каталогами. Последняя успешная загрузка доступна из кэша. «Смотрим» можно включить в настройках. Поддерживаются свои M3U по ссылке или из файла и отдельный User-Agent для плейлиста и потока.
- **Failover.** Одинаковые каналы из разных источников объединяются; при ошибке плеер переходит к следующему. Источник можно выбрать вручную и запомнить.
- **Телегид.** XMLTV с кэшем и несколькими источниками, «Сейчас в эфире», поиск по передачам, архив и возврат к прямой трансляции.
- **Android TV и телефон.** Пульт, D-pad, широкий баннер, «картинка в картинке» на совместимых телефонах.
- **Desktop.** Клавиатура и мышь, полноэкранный режим, аппаратное декодирование `auto-safe` с откатом на software.
- **Оформление.** Шесть акцентных тем (индиго по умолчанию), русский и английский языки, единая иконка на всех платформах.
- **Надёжность.** TTL проверки потоков, атомарное сохранение кэшей, сериализация обновлений EPG и плейлистов, восстановление после зависаний.

Поддерживаемые платформы:

| Платформа | Требования |
|---|---|
| Android / Android TV | Android 6.0+ (API 23) |
| Windows | Windows 10/11 x64 |
| Linux | x86_64, glibc-совместимый дистрибутив; эталон — Ubuntu 24.04 |

Если заданы TLS-пины (`simpletvTlsPins` или `SIMPLETV_TLS_PINS`), `minSdk` автоматически поднимается до 24.

## Установка

- **Android.** Скачайте APK из Releases и разрешите установку из неизвестного источника. Обновления ставятся поверх предыдущей версии.
- **Windows.** `SimpleTV-*-Setup.exe` устанавливает приложение, `SimpleTV-*-Portable.exe` запускается без установки и готовит файлы в `%LOCALAPPDATA%\SimpleTV\portable`.
- **Linux.** `SimpleTV-*-x86_64.AppImage` — `chmod +x` и запуск; альтернатива — `SimpleTV-*-linux-x64.tar.gz`. mpv, FFmpeg и libplacebo уже входят в пакет.

## Управление

Настольная версия:

| Клавиши | Действие |
|---|---|
| `↑` / `↓`, `Page Up` / `Page Down` | Предыдущий / следующий канал |
| `Enter`, `Space` | Список каналов / пауза |
| `0`–`9` | Номер канала |
| `F`, `F11`, двойной клик | Полный экран |
| `+` / `-`, колесо мыши | Громкость |
| `M` | Без звука |
| `P` / `L` | Список каналов / телегид |
| `G` | «Сейчас в эфире» |
| `R` | Последний канал |
| `V` / `Q` | Источник / соотношение сторон |
| `S` | Настройки |
| `Tab` / `I` | Статистика / информация о передаче |
| `Esc` | Закрыть оверлей или выйти из полного экрана |

На Android TV: `▲`/`▼` — каналы, `OK`/`◀` — список, `▶` — телегид, цифры — номер, `Меню` — поток и качество. На телефоне касание открывает панель, свайп вверх/вниз переключает каналы.

## Сборка

Нужны JDK 21, Android SDK с NDK `28.2.13676358` и CMake `3.22.1`, Flutter `3.47.9`. Для Windows-сборки — Visual Studio с C++ toolchain, для Linux — Clang, CMake, Ninja, GTK 3 и `libmpv`.

Встроенные плейлисты и адаптеры источников хранятся в `private/catalog.enc`, зашифрованном AES-256-GCM. Ключ доступен владельцу проекта и настроен в GitHub Actions через секрет `SIMPLETV_CATALOG_KEY`. Для локальной сборки положите ключ в `~/.simpletv-private/catalog.key` либо задайте одноимённую переменную окружения, затем выполните команды из корня проекта.

```bash
python -m pip install cryptography==50.0.2
python tools/private_catalog.py restore
```

Расшифрованные файлы исключены из Git. После изменения приватных данных выполните `python tools/private_catalog.py seal`, затем `python tools/private_catalog.py check` и закоммитьте обновлённый `private/catalog.enc`. Полная сборка приложения требует ключа каталога. Он хранится отдельно от ключа подписи Android.

Android:

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Release подписывается ключом из `keystore/release.properties` или переменных `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Локальная сборка без ключа использует debug-подпись; CI для публикации требует релизный ключ.

Desktop:

```bash
run-desktop.bat                 # Windows
bash run-desktop.sh             # Linux
cd desktop && flutter run -d windows
```

Пакеты:

```powershell
.\packaging\windows\build-windows-packages.ps1 -Version "1.0.0"
```

```bash
bash packaging/linux/build-linux-packages.sh 1.0.0
```

GitHub Actions собирает релиз по тегу `v*` или вручную с версией в формате `1.5.0`. Без версии создаётся dev-сборка в артефактах. Linux-медиастек кэшируется между запусками.

## Тесты

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug ktlintCheck
```

```bash
cd desktop
flutter analyze
flutter test --exclude-tags native-playback
```

Проверка реального декодирования mpv — `flutter test --tags native-playback`; на Windows нужен `libmpv-2.dll` из release-сборки или `SIMPLETV_TEST_LIBMPV`. Подробнее — в [CONTRIBUTING.md](CONTRIBUTING.md).

## Структура

- `core/` — модели, M3U, EPG, сопоставление каналов и проверка потоков.
- `app/` — Android и Android TV на Compose и Media3.
- `desktop/` — Windows и Linux на Flutter и mpv.
- `packaging/` — сборка установщиков, AppImage и архивов.
- `.opencode/skills/simpletv-ui/` — правила UI, токены и чек-лист ревью.

## Документация

- [CHANGELOG.md](CHANGELOG.md) — история изменений.
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — описание релизов.
- [CONTRIBUTING.md](CONTRIBUTING.md) — требования и проверки.
- [SECURITY.md](SECURITY.md) — сообщения об уязвимостях.
- [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) — сторонние компоненты и лицензии.
- [desktop/README.md](desktop/README.md) — особенности десктопного клиента.

## Отличия от SimpleTV

- `#EXTVLCOPT` не вырезается, а учитывается — так запускается больше каналов с нестандартным User-Agent.
- Ссылки на веб-страницы и картинки по умолчанию пропускаются; поведение настраивается.
- Нет фильмов, YouTube, AceStream и медиасервера: приложение только для ТВ.

## Лицензия

MIT — см. [LICENSE](LICENSE). Сторонние компоненты (mpv, FFmpeg, libplacebo, Media3, Flutter и другие) описаны в [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md); их лицензии поставляются вместе с пакетами.
