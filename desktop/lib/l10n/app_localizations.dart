import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ru.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ru'),
  ];

  /// No description provided for @browseHome.
  ///
  /// In ru, this message translates to:
  /// **'Главная'**
  String get browseHome;

  /// No description provided for @browseHomeStartup.
  ///
  /// In ru, this message translates to:
  /// **'Открывать главную при запуске'**
  String get browseHomeStartup;

  /// No description provided for @browseContinue.
  ///
  /// In ru, this message translates to:
  /// **'Продолжить просмотр'**
  String get browseContinue;

  /// No description provided for @browseNow.
  ///
  /// In ru, this message translates to:
  /// **'Сейчас'**
  String get browseNow;

  /// No description provided for @all.
  ///
  /// In ru, this message translates to:
  /// **'Все'**
  String get all;

  /// No description provided for @favourites.
  ///
  /// In ru, this message translates to:
  /// **'Избранное'**
  String get favourites;

  /// No description provided for @recent.
  ///
  /// In ru, this message translates to:
  /// **'Недавние'**
  String get recent;

  /// No description provided for @original.
  ///
  /// In ru, this message translates to:
  /// **'Исходный'**
  String get original;

  /// No description provided for @auto.
  ///
  /// In ru, this message translates to:
  /// **'Авто'**
  String get auto;

  /// No description provided for @yes.
  ///
  /// In ru, this message translates to:
  /// **'Да'**
  String get yes;

  /// No description provided for @no.
  ///
  /// In ru, this message translates to:
  /// **'Нет'**
  String get no;

  /// No description provided for @cancel.
  ///
  /// In ru, this message translates to:
  /// **'Отмена'**
  String get cancel;

  /// No description provided for @add.
  ///
  /// In ru, this message translates to:
  /// **'Добавить'**
  String get add;

  /// No description provided for @closeEsc.
  ///
  /// In ru, this message translates to:
  /// **'Закрыть (Esc)'**
  String get closeEsc;

  /// No description provided for @now.
  ///
  /// In ru, this message translates to:
  /// **'Сейчас'**
  String get now;

  /// No description provided for @fromStart.
  ///
  /// In ru, this message translates to:
  /// **'С начала'**
  String get fromStart;

  /// No description provided for @onAir.
  ///
  /// In ru, this message translates to:
  /// **'В эфире'**
  String get onAir;

  /// No description provided for @backToLive.
  ///
  /// In ru, this message translates to:
  /// **'В эфир'**
  String get backToLive;

  /// No description provided for @retry.
  ///
  /// In ru, this message translates to:
  /// **'Повторить'**
  String get retry;

  /// No description provided for @nextSource.
  ///
  /// In ru, this message translates to:
  /// **'Следующий источник'**
  String get nextSource;

  /// No description provided for @sourcesLabel.
  ///
  /// In ru, this message translates to:
  /// **'Источники: '**
  String get sourcesLabel;

  /// No description provided for @unknownSource.
  ///
  /// In ru, this message translates to:
  /// **'Неизвестный источник'**
  String get unknownSource;

  /// No description provided for @audio.
  ///
  /// In ru, this message translates to:
  /// **'Аудио'**
  String get audio;

  /// No description provided for @archive.
  ///
  /// In ru, this message translates to:
  /// **'Архив'**
  String get archive;

  /// No description provided for @onAirNow.
  ///
  /// In ru, this message translates to:
  /// **'Сейчас в эфире'**
  String get onAirNow;

  /// No description provided for @onAirNowUpper.
  ///
  /// In ru, this message translates to:
  /// **'СЕЙЧАС В ЭФИРЕ'**
  String get onAirNowUpper;

  /// No description provided for @guideTitle.
  ///
  /// In ru, this message translates to:
  /// **'Телегид SimpleTV'**
  String get guideTitle;

  /// No description provided for @channelsCount.
  ///
  /// In ru, this message translates to:
  /// **'{count, plural, one{{count} канал} few{{count} канала} many{{count} каналов} other{{count} канала}}'**
  String channelsCount(int count);

  /// No description provided for @sourcesShort.
  ///
  /// In ru, this message translates to:
  /// **'{count} ист.'**
  String sourcesShort(Object count);

  /// No description provided for @sourcesAvailable.
  ///
  /// In ru, this message translates to:
  /// **'{count, plural, one{{count} источник вещания доступен} few{{count} источника вещания доступно} many{{count} источников вещания доступно} other{{count} источника вещания доступно}}'**
  String sourcesAvailable(int count);

  /// No description provided for @epgIptvxLite.
  ///
  /// In ru, this message translates to:
  /// **'iptvx.one lite (лёгкая)'**
  String get epgIptvxLite;

  /// No description provided for @epgPwRu.
  ///
  /// In ru, this message translates to:
  /// **'epg.pw RU (3 дня)'**
  String get epgPwRu;

  /// No description provided for @selectChannelForGuide.
  ///
  /// In ru, this message translates to:
  /// **'Выберите канал для просмотра программы передач'**
  String get selectChannelForGuide;

  /// No description provided for @searchHint.
  ///
  /// In ru, this message translates to:
  /// **'Поиск канала или передачи...'**
  String get searchHint;

  /// No description provided for @allSources.
  ///
  /// In ru, this message translates to:
  /// **'Все источники'**
  String get allSources;

  /// No description provided for @noChannels.
  ///
  /// In ru, this message translates to:
  /// **'Каналы не найдены'**
  String get noChannels;

  /// No description provided for @liveBadge.
  ///
  /// In ru, this message translates to:
  /// **'ЭФИР'**
  String get liveBadge;

  /// No description provided for @noProgrammeNow.
  ///
  /// In ru, this message translates to:
  /// **'Нет программы на это время'**
  String get noProgrammeNow;

  /// No description provided for @guideNotLoaded.
  ///
  /// In ru, this message translates to:
  /// **'Телепрограмма не загружена для данного канала'**
  String get guideNotLoaded;

  /// No description provided for @noProgrammeInfo.
  ///
  /// In ru, this message translates to:
  /// **'Информация о передачах отсутствует для этого канала'**
  String get noProgrammeInfo;

  /// No description provided for @noDescription.
  ///
  /// In ru, this message translates to:
  /// **'Описание передачи отсутствует в телепрограмме.'**
  String get noDescription;

  /// No description provided for @toGuideGrid.
  ///
  /// In ru, this message translates to:
  /// **'К сетке эфира'**
  String get toGuideGrid;

  /// No description provided for @sourcesTitle.
  ///
  /// In ru, this message translates to:
  /// **'Источники: {name}'**
  String sourcesTitle(Object name);

  /// No description provided for @videoQuality.
  ///
  /// In ru, this message translates to:
  /// **'Качество видео'**
  String get videoQuality;

  /// No description provided for @videoQualityTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Выбор качества видео'**
  String get videoQualityTooltip;

  /// No description provided for @autoArrow.
  ///
  /// In ru, this message translates to:
  /// **'Авто ▾'**
  String get autoArrow;

  /// No description provided for @mbps.
  ///
  /// In ru, this message translates to:
  /// **'{value} Мбит/с'**
  String mbps(Object value);

  /// No description provided for @kbps.
  ///
  /// In ru, this message translates to:
  /// **'{value} кбит/с'**
  String kbps(Object value);

  /// No description provided for @mono.
  ///
  /// In ru, this message translates to:
  /// **'моно'**
  String get mono;

  /// No description provided for @stereo.
  ///
  /// In ru, this message translates to:
  /// **'стерео'**
  String get stereo;

  /// No description provided for @channelsShort.
  ///
  /// In ru, this message translates to:
  /// **'{count} кан.'**
  String channelsShort(Object count);

  /// No description provided for @khz.
  ///
  /// In ru, this message translates to:
  /// **'{value} кГц'**
  String khz(Object value);

  /// No description provided for @nextAt.
  ///
  /// In ru, this message translates to:
  /// **'Далее ({time}): '**
  String nextAt(Object time);

  /// No description provided for @channelsGuide.
  ///
  /// In ru, this message translates to:
  /// **'Каналы и программа'**
  String get channelsGuide;

  /// No description provided for @channelsGuideTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Каналы и программа (L)'**
  String get channelsGuideTooltip;

  /// No description provided for @prevChannelTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Предыдущий канал (PageDown / ↓)'**
  String get prevChannelTooltip;

  /// No description provided for @nextChannelTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Следующий канал (PageUp / ↑)'**
  String get nextChannelTooltip;

  /// No description provided for @channelInfoTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Информация о канале (I)'**
  String get channelInfoTooltip;

  /// No description provided for @channelNotSelected.
  ///
  /// In ru, this message translates to:
  /// **'Канал не выбран'**
  String get channelNotSelected;

  /// No description provided for @sourceLabel.
  ///
  /// In ru, this message translates to:
  /// **'Источник: {label}'**
  String sourceLabel(Object label);

  /// No description provided for @aspectTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Формат экрана'**
  String get aspectTooltip;

  /// No description provided for @aspectOriginal.
  ///
  /// In ru, this message translates to:
  /// **'Формат: Исходный'**
  String get aspectOriginal;

  /// No description provided for @aspect169.
  ///
  /// In ru, this message translates to:
  /// **'Формат: 16:9 (Широкий)'**
  String get aspect169;

  /// No description provided for @aspect43.
  ///
  /// In ru, this message translates to:
  /// **'Формат: 4:3 (Стандарт)'**
  String get aspect43;

  /// No description provided for @aspect219.
  ///
  /// In ru, this message translates to:
  /// **'Формат: 21:9 (Кинематограф)'**
  String get aspect219;

  /// No description provided for @muteTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Вкл/выкл звук (M)'**
  String get muteTooltip;

  /// No description provided for @settingsTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Настройки (S)'**
  String get settingsTooltip;

  /// No description provided for @lastChannelTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Последний канал (R)'**
  String get lastChannelTooltip;

  /// No description provided for @compactTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Компактное окно поверх остальных'**
  String get compactTooltip;

  /// No description provided for @fullscreenTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Во весь экран (F / F11)'**
  String get fullscreenTooltip;

  /// No description provided for @menuGuide.
  ///
  /// In ru, this message translates to:
  /// **'Телегид и каналы (L)'**
  String get menuGuide;

  /// No description provided for @menuNextSource.
  ///
  /// In ru, this message translates to:
  /// **'Следующий источник'**
  String get menuNextSource;

  /// No description provided for @menuNextChannel.
  ///
  /// In ru, this message translates to:
  /// **'Следующий канал (PageUp / ↑)'**
  String get menuNextChannel;

  /// No description provided for @menuPrevChannel.
  ///
  /// In ru, this message translates to:
  /// **'Предыдущий канал (PageDown / ↓)'**
  String get menuPrevChannel;

  /// No description provided for @menuStats.
  ///
  /// In ru, this message translates to:
  /// **'Техническая статистика (Tab)'**
  String get menuStats;

  /// No description provided for @menuSettings.
  ///
  /// In ru, this message translates to:
  /// **'Настройки (S)'**
  String get menuSettings;

  /// No description provided for @menuFullscreen.
  ///
  /// In ru, this message translates to:
  /// **'Во весь экран (F / Enter)'**
  String get menuFullscreen;

  /// No description provided for @tooltipLastChannel.
  ///
  /// In ru, this message translates to:
  /// **'Последний канал'**
  String get tooltipLastChannel;

  /// No description provided for @tooltipSound.
  ///
  /// In ru, this message translates to:
  /// **'Звук'**
  String get tooltipSound;

  /// No description provided for @tooltipNormalWindow.
  ///
  /// In ru, this message translates to:
  /// **'Обычное окно'**
  String get tooltipNormalWindow;

  /// No description provided for @importFailed.
  ///
  /// In ru, this message translates to:
  /// **'Не удалось импортировать плейлист'**
  String get importFailed;

  /// No description provided for @dropHint.
  ///
  /// In ru, this message translates to:
  /// **'Перетащите файл M3U или M3U8 размером до 16 МБ'**
  String get dropHint;

  /// No description provided for @connecting.
  ///
  /// In ru, this message translates to:
  /// **'Подключение · {source} {index}/{total}'**
  String connecting(Object index, Object source, Object total);

  /// No description provided for @playbackError.
  ///
  /// In ru, this message translates to:
  /// **'Ошибка воспроизведения потока'**
  String get playbackError;

  /// No description provided for @dropToAdd.
  ///
  /// In ru, this message translates to:
  /// **'Отпустите файл M3U, чтобы добавить плейлист'**
  String get dropToAdd;

  /// No description provided for @addPlaylistTitle.
  ///
  /// In ru, this message translates to:
  /// **'Добавить плейлист M3U'**
  String get addPlaylistTitle;

  /// No description provided for @playlistName.
  ///
  /// In ru, this message translates to:
  /// **'Название плейлиста'**
  String get playlistName;

  /// No description provided for @playlistNameHint.
  ///
  /// In ru, this message translates to:
  /// **'Например: Мой IPTV'**
  String get playlistNameHint;

  /// No description provided for @playlistUrl.
  ///
  /// In ru, this message translates to:
  /// **'URL ссылка или путь к .m3u файлу'**
  String get playlistUrl;

  /// No description provided for @uaOptional.
  ///
  /// In ru, this message translates to:
  /// **'User-Agent (необязательно)'**
  String get uaOptional;

  /// No description provided for @epgSourceTitle.
  ///
  /// In ru, this message translates to:
  /// **'Источник телепрограммы'**
  String get epgSourceTitle;

  /// No description provided for @name.
  ///
  /// In ru, this message translates to:
  /// **'Название'**
  String get name;

  /// No description provided for @xmltvUrl.
  ///
  /// In ru, this message translates to:
  /// **'Ссылка на XMLTV'**
  String get xmltvUrl;

  /// No description provided for @desktopSettingsTitle.
  ///
  /// In ru, this message translates to:
  /// **'Настройки SimpleTV'**
  String get desktopSettingsTitle;

  /// No description provided for @sourcesSection.
  ///
  /// In ru, this message translates to:
  /// **'Источники плейлистов (TVSources)'**
  String get sourcesSection;

  /// No description provided for @sourcesSubtitle.
  ///
  /// In ru, this message translates to:
  /// **'Каналы из разных источников объединяются по названию'**
  String get sourcesSubtitle;

  /// No description provided for @appearanceSection.
  ///
  /// In ru, this message translates to:
  /// **'Оформление'**
  String get appearanceSection;

  /// No description provided for @appearanceSubtitle.
  ///
  /// In ru, this message translates to:
  /// **'Акцентный цвет кнопок, фокуса и выделения'**
  String get appearanceSubtitle;

  /// No description provided for @accentViolet.
  ///
  /// In ru, this message translates to:
  /// **'Индиго'**
  String get accentViolet;

  /// No description provided for @accentOrange.
  ///
  /// In ru, this message translates to:
  /// **'Оранжевый'**
  String get accentOrange;

  /// No description provided for @accentBlue.
  ///
  /// In ru, this message translates to:
  /// **'Синий'**
  String get accentBlue;

  /// No description provided for @accentTeal.
  ///
  /// In ru, this message translates to:
  /// **'Бирюзовый'**
  String get accentTeal;

  /// No description provided for @accentPink.
  ///
  /// In ru, this message translates to:
  /// **'Розовый'**
  String get accentPink;

  /// No description provided for @accentRed.
  ///
  /// In ru, this message translates to:
  /// **'Красный'**
  String get accentRed;

  /// No description provided for @builtIn.
  ///
  /// In ru, this message translates to:
  /// **'Встроенный'**
  String get builtIn;

  /// No description provided for @updateError.
  ///
  /// In ru, this message translates to:
  /// **'Ошибка обновления'**
  String get updateError;

  /// No description provided for @lastLoad.
  ///
  /// In ru, this message translates to:
  /// **'Последняя загрузка: {date}'**
  String lastLoad(Object date);

  /// No description provided for @noLastLoad.
  ///
  /// In ru, this message translates to:
  /// **'Нет даты успешной загрузки'**
  String get noLastLoad;

  /// No description provided for @controlsSection.
  ///
  /// In ru, this message translates to:
  /// **'Управление и навигация'**
  String get controlsSection;

  /// No description provided for @reverseZapTitle.
  ///
  /// In ru, this message translates to:
  /// **'Обратное переключение стрелками (Вверх = След., Вниз = Пред.)'**
  String get reverseZapTitle;

  /// No description provided for @reverseZapSub.
  ///
  /// In ru, this message translates to:
  /// **'Логичный порядок переключения для пульта и клавиатуры'**
  String get reverseZapSub;

  /// No description provided for @filtersSection.
  ///
  /// In ru, this message translates to:
  /// **'Фильтры и объединение каналов'**
  String get filtersSection;

  /// No description provided for @hideAdult.
  ///
  /// In ru, this message translates to:
  /// **'Скрывать каналы для взрослых (18+)'**
  String get hideAdult;

  /// No description provided for @hideAdultSub.
  ///
  /// In ru, this message translates to:
  /// **'Исключать эротические и ночные каналы из списка'**
  String get hideAdultSub;

  /// No description provided for @preferMax.
  ///
  /// In ru, this message translates to:
  /// **'Принудительно включать максимальное качество'**
  String get preferMax;

  /// No description provided for @preferMaxSub.
  ///
  /// In ru, this message translates to:
  /// **'Автоматически выбирать 4K / UHD и 1080p поток при наличии'**
  String get preferMaxSub;

  /// No description provided for @hideSd.
  ///
  /// In ru, this message translates to:
  /// **'Скрывать SD-дубли'**
  String get hideSd;

  /// No description provided for @hideSdSub.
  ///
  /// In ru, this message translates to:
  /// **'Оставлять только HD/UHD версии при наличии'**
  String get hideSdSub;

  /// No description provided for @hideTz.
  ///
  /// In ru, this message translates to:
  /// **'Скрывать другие часовые пояса'**
  String get hideTz;

  /// No description provided for @hideTzSub.
  ///
  /// In ru, this message translates to:
  /// **'Исключать дубли с пометками (+2), (+4), (+7), Орбита'**
  String get hideTzSub;

  /// No description provided for @mergeOtt.
  ///
  /// In ru, this message translates to:
  /// **'Объединять OTT каналы'**
  String get mergeOtt;

  /// No description provided for @mergeOttSub.
  ///
  /// In ru, this message translates to:
  /// **'Добавлять OTT версии как резервные источники к основным'**
  String get mergeOttSub;

  /// No description provided for @lcn.
  ///
  /// In ru, this message translates to:
  /// **'Федеральный порядок каналов (LCN)'**
  String get lcn;

  /// No description provided for @lcnSub.
  ///
  /// In ru, this message translates to:
  /// **'Первые 20 обязательных кнопок цифрового ТВ России'**
  String get lcnSub;

  /// No description provided for @aspectSection.
  ///
  /// In ru, this message translates to:
  /// **'Соотношение сторон'**
  String get aspectSection;

  /// No description provided for @aspect1610.
  ///
  /// In ru, this message translates to:
  /// **'16:10'**
  String get aspect1610;

  /// No description provided for @buffer.
  ///
  /// In ru, this message translates to:
  /// **'Буфер потока'**
  String get buffer;

  /// No description provided for @bufferSub.
  ///
  /// In ru, this message translates to:
  /// **'Применяется при следующем открытии потока'**
  String get bufferSub;

  /// No description provided for @secondsShort.
  ///
  /// In ru, this message translates to:
  /// **'{count} с'**
  String secondsShort(Object count);

  /// No description provided for @epgSection.
  ///
  /// In ru, this message translates to:
  /// **'Телепрограмма'**
  String get epgSection;

  /// No description provided for @xmltvSource.
  ///
  /// In ru, this message translates to:
  /// **'Источник XMLTV'**
  String get xmltvSource;

  /// No description provided for @deleteEpgTooltip.
  ///
  /// In ru, this message translates to:
  /// **'Удалить источник телепрограммы'**
  String get deleteEpgTooltip;

  /// No description provided for @autoEpg.
  ///
  /// In ru, this message translates to:
  /// **'Автообновление телепрограммы'**
  String get autoEpg;

  /// No description provided for @everyHours.
  ///
  /// In ru, this message translates to:
  /// **'Каждые {count} ч'**
  String everyHours(Object count);

  /// No description provided for @applyPlaylists.
  ///
  /// In ru, this message translates to:
  /// **'Применить и обновить плейлисты'**
  String get applyPlaylists;

  /// No description provided for @refreshEpg.
  ///
  /// In ru, this message translates to:
  /// **'Обновить телегид (EPG)'**
  String get refreshEpg;

  /// No description provided for @statsTitle.
  ///
  /// In ru, this message translates to:
  /// **'Техническая статистика'**
  String get statsTitle;

  /// No description provided for @statChannel.
  ///
  /// In ru, this message translates to:
  /// **'Канал'**
  String get statChannel;

  /// No description provided for @statSource.
  ///
  /// In ru, this message translates to:
  /// **'Источник'**
  String get statSource;

  /// No description provided for @videoFormat.
  ///
  /// In ru, this message translates to:
  /// **'Формат видео'**
  String get videoFormat;

  /// No description provided for @videoCodec.
  ///
  /// In ru, this message translates to:
  /// **'Видеокодек'**
  String get videoCodec;

  /// No description provided for @audioCodec.
  ///
  /// In ru, this message translates to:
  /// **'Аудиокодек'**
  String get audioCodec;

  /// No description provided for @videoOutput.
  ///
  /// In ru, this message translates to:
  /// **'Вывод видео'**
  String get videoOutput;

  /// No description provided for @decoding.
  ///
  /// In ru, this message translates to:
  /// **'Декодирование'**
  String get decoding;

  /// No description provided for @fpsLabel.
  ///
  /// In ru, this message translates to:
  /// **'Кадры/с'**
  String get fpsLabel;

  /// No description provided for @statVideoBitrate.
  ///
  /// In ru, this message translates to:
  /// **'Битрейт видео'**
  String get statVideoBitrate;

  /// No description provided for @statAudioBitrate.
  ///
  /// In ru, this message translates to:
  /// **'Битрейт аудио'**
  String get statAudioBitrate;

  /// No description provided for @droppedFrames.
  ///
  /// In ru, this message translates to:
  /// **'Потеря кадров'**
  String get droppedFrames;

  /// No description provided for @statBuffer.
  ///
  /// In ru, this message translates to:
  /// **'Буфер'**
  String get statBuffer;

  /// No description provided for @buffering.
  ///
  /// In ru, this message translates to:
  /// **'Буферизация'**
  String get buffering;

  /// No description provided for @statPlaying.
  ///
  /// In ru, this message translates to:
  /// **'Воспроизведение'**
  String get statPlaying;

  /// No description provided for @stateActive.
  ///
  /// In ru, this message translates to:
  /// **'Активно'**
  String get stateActive;

  /// No description provided for @statePaused.
  ///
  /// In ru, this message translates to:
  /// **'Пауза'**
  String get statePaused;

  /// No description provided for @drmLabel.
  ///
  /// In ru, this message translates to:
  /// **'DRM ClearKey'**
  String get drmLabel;

  /// No description provided for @engine.
  ///
  /// In ru, this message translates to:
  /// **'Движок'**
  String get engine;

  /// No description provided for @customSource.
  ///
  /// In ru, this message translates to:
  /// **'Пользовательский'**
  String get customSource;

  /// No description provided for @noStreams.
  ///
  /// In ru, this message translates to:
  /// **'{name}: нет доступных потоков'**
  String noStreams(Object name);

  /// No description provided for @archiveUnavailable.
  ///
  /// In ru, this message translates to:
  /// **'Архив недоступен для выбранной передачи или источника'**
  String get archiveUnavailable;

  /// No description provided for @playbackTimeout.
  ///
  /// In ru, this message translates to:
  /// **'Таймаут воспроизведения'**
  String get playbackTimeout;

  /// No description provided for @variantFailed.
  ///
  /// In ru, this message translates to:
  /// **'{channel}: {failed} недоступен, пробую {next}...'**
  String variantFailed(Object channel, Object failed, Object next);

  /// No description provided for @allSourcesDown.
  ///
  /// In ru, this message translates to:
  /// **'{channel}: все источники недоступны ({count})'**
  String allSourcesDown(Object channel, Object count);

  /// No description provided for @sourceSelected.
  ///
  /// In ru, this message translates to:
  /// **'Выбран источник: {name}'**
  String sourceSelected(Object name);

  /// No description provided for @noOtherArchive.
  ///
  /// In ru, this message translates to:
  /// **'У этой передачи нет другого источника архива'**
  String get noOtherArchive;

  /// No description provided for @favouriteAdded.
  ///
  /// In ru, this message translates to:
  /// **'Добавлено в избранное: {name}'**
  String favouriteAdded(Object name);

  /// No description provided for @favouriteRemoved.
  ///
  /// In ru, this message translates to:
  /// **'Удалено из избранного: {name}'**
  String favouriteRemoved(Object name);

  /// No description provided for @soundOn.
  ///
  /// In ru, this message translates to:
  /// **'Звук включен'**
  String get soundOn;

  /// No description provided for @soundOff.
  ///
  /// In ru, this message translates to:
  /// **'Звук выключен'**
  String get soundOff;

  /// No description provided for @playlistUrlHint.
  ///
  /// In ru, this message translates to:
  /// **'https://example.com/playlist.m3u'**
  String get playlistUrlHint;

  /// No description provided for @invalidUrl.
  ///
  /// In ru, this message translates to:
  /// **'Введите корректный адрес http(s)'**
  String get invalidUrl;

  /// No description provided for @groupGeneric.
  ///
  /// In ru, this message translates to:
  /// **'Общие'**
  String get groupGeneric;

  /// No description provided for @aspectFormat.
  ///
  /// In ru, this message translates to:
  /// **'Формат экрана: {ratio}'**
  String aspectFormat(Object ratio);

  /// No description provided for @archiveUnavailableSources.
  ///
  /// In ru, this message translates to:
  /// **'Архив недоступен у источников канала'**
  String get archiveUnavailableSources;

  /// No description provided for @streamPlayingConfirmed.
  ///
  /// In ru, this message translates to:
  /// **'Воспроизведение подтверждено'**
  String get streamPlayingConfirmed;

  /// No description provided for @streamUnavailable.
  ///
  /// In ru, this message translates to:
  /// **'Недоступен'**
  String get streamUnavailable;

  /// No description provided for @streamAvailable.
  ///
  /// In ru, this message translates to:
  /// **'Сеть доступна'**
  String get streamAvailable;

  /// No description provided for @streamNotChecked.
  ///
  /// In ru, this message translates to:
  /// **'Не проверен'**
  String get streamNotChecked;

  /// No description provided for @loadingPlaylist.
  ///
  /// In ru, this message translates to:
  /// **'Загрузка: {name}...'**
  String loadingPlaylist(Object name);

  /// No description provided for @cacheSaveFailed.
  ///
  /// In ru, this message translates to:
  /// **'Не удалось сохранить кэш плейлистов'**
  String get cacheSaveFailed;

  /// No description provided for @loadingGuide.
  ///
  /// In ru, this message translates to:
  /// **'Загрузка телегида: {name}...'**
  String loadingGuide(Object name);

  /// No description provided for @guideUpdated.
  ///
  /// In ru, this message translates to:
  /// **'Телегид обновлён'**
  String get guideUpdated;

  /// No description provided for @guideUpdateFailed.
  ///
  /// In ru, this message translates to:
  /// **'Не удалось обновить телегид'**
  String get guideUpdateFailed;

  /// No description provided for @openStreamError.
  ///
  /// In ru, this message translates to:
  /// **'Ошибка открытия потока'**
  String get openStreamError;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'ru'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'ru':
      return AppLocalizationsRu();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
