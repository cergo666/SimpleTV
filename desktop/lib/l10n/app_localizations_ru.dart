// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get browseHome => 'Главная';

  @override
  String get browseHomeStartup => 'Открывать главную при запуске';

  @override
  String get browseContinue => 'Продолжить просмотр';

  @override
  String get browseNow => 'Сейчас';

  @override
  String get all => 'Все';

  @override
  String get favourites => 'Избранное';

  @override
  String get recent => 'Недавние';

  @override
  String get original => 'Исходный';

  @override
  String get auto => 'Авто';

  @override
  String get yes => 'Да';

  @override
  String get no => 'Нет';

  @override
  String get cancel => 'Отмена';

  @override
  String get add => 'Добавить';

  @override
  String get closeEsc => 'Закрыть (Esc)';

  @override
  String get now => 'Сейчас';

  @override
  String get fromStart => 'С начала';

  @override
  String get onAir => 'В эфире';

  @override
  String get backToLive => 'В эфир';

  @override
  String get retry => 'Повторить';

  @override
  String get nextSource => 'Следующий источник';

  @override
  String get sourcesLabel => 'Источники: ';

  @override
  String get unknownSource => 'Неизвестный источник';

  @override
  String get audio => 'Аудио';

  @override
  String get archive => 'Архив';

  @override
  String get onAirNow => 'Сейчас в эфире';

  @override
  String get onAirNowUpper => 'СЕЙЧАС В ЭФИРЕ';

  @override
  String get guideTitle => 'Телегид SimpleTV';

  @override
  String channelsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count канала',
      many: '$count каналов',
      few: '$count канала',
      one: '$count канал',
    );
    return '$_temp0';
  }

  @override
  String sourcesShort(Object count) {
    return '$count ист.';
  }

  @override
  String sourcesAvailable(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count источника вещания доступно',
      many: '$count источников вещания доступно',
      few: '$count источника вещания доступно',
      one: '$count источник вещания доступен',
    );
    return '$_temp0';
  }

  @override
  String get epgIptvxLite => 'iptvx.one lite (лёгкая)';

  @override
  String get epgPwRu => 'epg.pw RU (3 дня)';

  @override
  String get selectChannelForGuide =>
      'Выберите канал для просмотра программы передач';

  @override
  String get searchHint => 'Поиск канала или передачи...';

  @override
  String get allSources => 'Все источники';

  @override
  String get noChannels => 'Каналы не найдены';

  @override
  String get liveBadge => 'ЭФИР';

  @override
  String get noProgrammeNow => 'Нет программы на это время';

  @override
  String get guideNotLoaded => 'Телепрограмма не загружена для данного канала';

  @override
  String get noProgrammeInfo =>
      'Информация о передачах отсутствует для этого канала';

  @override
  String get noDescription => 'Описание передачи отсутствует в телепрограмме.';

  @override
  String get toGuideGrid => 'К сетке эфира';

  @override
  String sourcesTitle(Object name) {
    return 'Источники: $name';
  }

  @override
  String get videoQuality => 'Качество видео';

  @override
  String get videoQualityTooltip => 'Выбор качества видео';

  @override
  String get autoArrow => 'Авто ▾';

  @override
  String mbps(Object value) {
    return '$value Мбит/с';
  }

  @override
  String kbps(Object value) {
    return '$value кбит/с';
  }

  @override
  String get mono => 'моно';

  @override
  String get stereo => 'стерео';

  @override
  String channelsShort(Object count) {
    return '$count кан.';
  }

  @override
  String khz(Object value) {
    return '$value кГц';
  }

  @override
  String nextAt(Object time) {
    return 'Далее ($time): ';
  }

  @override
  String get channelsGuide => 'Каналы и программа';

  @override
  String get channelsGuideTooltip => 'Каналы и программа (L)';

  @override
  String get prevChannelTooltip => 'Предыдущий канал (PageDown / ↓)';

  @override
  String get nextChannelTooltip => 'Следующий канал (PageUp / ↑)';

  @override
  String get channelInfoTooltip => 'Информация о канале (I)';

  @override
  String get channelNotSelected => 'Канал не выбран';

  @override
  String sourceLabel(Object label) {
    return 'Источник: $label';
  }

  @override
  String get aspectTooltip => 'Формат экрана';

  @override
  String get aspectOriginal => 'Формат: Исходный';

  @override
  String get aspect169 => 'Формат: 16:9 (Широкий)';

  @override
  String get aspect43 => 'Формат: 4:3 (Стандарт)';

  @override
  String get aspect219 => 'Формат: 21:9 (Кинематограф)';

  @override
  String get muteTooltip => 'Вкл/выкл звук (M)';

  @override
  String get settingsTooltip => 'Настройки (S)';

  @override
  String get lastChannelTooltip => 'Последний канал (R)';

  @override
  String get compactTooltip => 'Компактное окно поверх остальных';

  @override
  String get fullscreenTooltip => 'Во весь экран (F / F11)';

  @override
  String get menuGuide => 'Телегид и каналы (L)';

  @override
  String get menuNextSource => 'Следующий источник';

  @override
  String get menuNextChannel => 'Следующий канал (PageUp / ↑)';

  @override
  String get menuPrevChannel => 'Предыдущий канал (PageDown / ↓)';

  @override
  String get menuStats => 'Техническая статистика (Tab)';

  @override
  String get menuSettings => 'Настройки (S)';

  @override
  String get menuFullscreen => 'Во весь экран (F / Enter)';

  @override
  String get tooltipLastChannel => 'Последний канал';

  @override
  String get tooltipSound => 'Звук';

  @override
  String get tooltipNormalWindow => 'Обычное окно';

  @override
  String get importFailed => 'Не удалось импортировать плейлист';

  @override
  String get dropHint => 'Перетащите файл M3U или M3U8 размером до 16 МБ';

  @override
  String connecting(Object index, Object source, Object total) {
    return 'Подключение · $source $index/$total';
  }

  @override
  String get playbackError => 'Ошибка воспроизведения потока';

  @override
  String get dropToAdd => 'Отпустите файл M3U, чтобы добавить плейлист';

  @override
  String get addPlaylistTitle => 'Добавить плейлист M3U';

  @override
  String get playlistName => 'Название плейлиста';

  @override
  String get playlistNameHint => 'Например: Мой IPTV';

  @override
  String get playlistUrl => 'URL ссылка или путь к .m3u файлу';

  @override
  String get uaOptional => 'User-Agent (необязательно)';

  @override
  String get epgSourceTitle => 'Источник телепрограммы';

  @override
  String get name => 'Название';

  @override
  String get xmltvUrl => 'Ссылка на XMLTV';

  @override
  String get desktopSettingsTitle => 'Настройки SimpleTV';

  @override
  String get sourcesSection => 'Источники плейлистов (TVSources)';

  @override
  String get sourcesSubtitle =>
      'Каналы из разных источников объединяются по названию';

  @override
  String get appearanceSection => 'Оформление';

  @override
  String get appearanceSubtitle => 'Акцентный цвет кнопок, фокуса и выделения';

  @override
  String get accentViolet => 'Индиго';

  @override
  String get accentOrange => 'Оранжевый';

  @override
  String get accentBlue => 'Синий';

  @override
  String get accentTeal => 'Бирюзовый';

  @override
  String get accentPink => 'Розовый';

  @override
  String get accentRed => 'Красный';

  @override
  String get builtIn => 'Встроенный';

  @override
  String get updateError => 'Ошибка обновления';

  @override
  String lastLoad(Object date) {
    return 'Последняя загрузка: $date';
  }

  @override
  String get noLastLoad => 'Нет даты успешной загрузки';

  @override
  String get controlsSection => 'Управление и навигация';

  @override
  String get reverseZapTitle =>
      'Обратное переключение стрелками (Вверх = След., Вниз = Пред.)';

  @override
  String get reverseZapSub =>
      'Логичный порядок переключения для пульта и клавиатуры';

  @override
  String get filtersSection => 'Фильтры и объединение каналов';

  @override
  String get hideAdult => 'Скрывать каналы для взрослых (18+)';

  @override
  String get hideAdultSub => 'Исключать эротические и ночные каналы из списка';

  @override
  String get preferMax => 'Принудительно включать максимальное качество';

  @override
  String get preferMaxSub =>
      'Автоматически выбирать 4K / UHD и 1080p поток при наличии';

  @override
  String get hideSd => 'Скрывать SD-дубли';

  @override
  String get hideSdSub => 'Оставлять только HD/UHD версии при наличии';

  @override
  String get hideTz => 'Скрывать другие часовые пояса';

  @override
  String get hideTzSub =>
      'Исключать дубли с пометками (+2), (+4), (+7), Орбита';

  @override
  String get mergeOtt => 'Объединять OTT каналы';

  @override
  String get mergeOttSub =>
      'Добавлять OTT версии как резервные источники к основным';

  @override
  String get lcn => 'Федеральный порядок каналов (LCN)';

  @override
  String get lcnSub => 'Первые 20 обязательных кнопок цифрового ТВ России';

  @override
  String get aspectSection => 'Соотношение сторон';

  @override
  String get aspect1610 => '16:10';

  @override
  String get buffer => 'Буфер потока';

  @override
  String get bufferSub => 'Применяется при следующем открытии потока';

  @override
  String secondsShort(Object count) {
    return '$count с';
  }

  @override
  String get epgSection => 'Телепрограмма';

  @override
  String get xmltvSource => 'Источник XMLTV';

  @override
  String get deleteEpgTooltip => 'Удалить источник телепрограммы';

  @override
  String get autoEpg => 'Автообновление телепрограммы';

  @override
  String everyHours(Object count) {
    return 'Каждые $count ч';
  }

  @override
  String get applyPlaylists => 'Применить и обновить плейлисты';

  @override
  String get refreshEpg => 'Обновить телегид (EPG)';

  @override
  String get statsTitle => 'Техническая статистика';

  @override
  String get statChannel => 'Канал';

  @override
  String get statSource => 'Источник';

  @override
  String get videoFormat => 'Формат видео';

  @override
  String get videoCodec => 'Видеокодек';

  @override
  String get audioCodec => 'Аудиокодек';

  @override
  String get videoOutput => 'Вывод видео';

  @override
  String get decoding => 'Декодирование';

  @override
  String get fpsLabel => 'Кадры/с';

  @override
  String get statVideoBitrate => 'Битрейт видео';

  @override
  String get statAudioBitrate => 'Битрейт аудио';

  @override
  String get droppedFrames => 'Потеря кадров';

  @override
  String get statBuffer => 'Буфер';

  @override
  String get buffering => 'Буферизация';

  @override
  String get statPlaying => 'Воспроизведение';

  @override
  String get stateActive => 'Активно';

  @override
  String get statePaused => 'Пауза';

  @override
  String get drmLabel => 'DRM ClearKey';

  @override
  String get engine => 'Движок';

  @override
  String get customSource => 'Пользовательский';

  @override
  String noStreams(Object name) {
    return '$name: нет доступных потоков';
  }

  @override
  String get archiveUnavailable =>
      'Архив недоступен для выбранной передачи или источника';

  @override
  String get playbackTimeout => 'Таймаут воспроизведения';

  @override
  String variantFailed(Object channel, Object failed, Object next) {
    return '$channel: $failed недоступен, пробую $next...';
  }

  @override
  String allSourcesDown(Object channel, Object count) {
    return '$channel: все источники недоступны ($count)';
  }

  @override
  String sourceSelected(Object name) {
    return 'Выбран источник: $name';
  }

  @override
  String get noOtherArchive => 'У этой передачи нет другого источника архива';

  @override
  String favouriteAdded(Object name) {
    return 'Добавлено в избранное: $name';
  }

  @override
  String favouriteRemoved(Object name) {
    return 'Удалено из избранного: $name';
  }

  @override
  String get soundOn => 'Звук включен';

  @override
  String get soundOff => 'Звук выключен';

  @override
  String get playlistUrlHint => 'https://example.com/playlist.m3u';

  @override
  String get invalidUrl => 'Введите корректный адрес http(s)';

  @override
  String get groupGeneric => 'Общие';

  @override
  String aspectFormat(Object ratio) {
    return 'Формат экрана: $ratio';
  }

  @override
  String get archiveUnavailableSources =>
      'Архив недоступен у источников канала';

  @override
  String get streamPlayingConfirmed => 'Воспроизведение подтверждено';

  @override
  String get streamUnavailable => 'Недоступен';

  @override
  String get streamAvailable => 'Сеть доступна';

  @override
  String get streamNotChecked => 'Не проверен';

  @override
  String loadingPlaylist(Object name) {
    return 'Загрузка: $name...';
  }

  @override
  String get cacheSaveFailed => 'Не удалось сохранить кэш плейлистов';

  @override
  String loadingGuide(Object name) {
    return 'Загрузка телегида: $name...';
  }

  @override
  String get guideUpdated => 'Телегид обновлён';

  @override
  String get guideUpdateFailed => 'Не удалось обновить телегид';

  @override
  String get openStreamError => 'Ошибка открытия потока';
}
