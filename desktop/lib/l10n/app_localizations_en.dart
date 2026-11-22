// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get browseHome => 'Home';

  @override
  String get browseHomeStartup => 'Open Home on startup';

  @override
  String get browseContinue => 'Continue watching';

  @override
  String get browseNow => 'Now';

  @override
  String get all => 'All';

  @override
  String get favourites => 'Favourites';

  @override
  String get recent => 'Recent';

  @override
  String get original => 'Original';

  @override
  String get auto => 'Auto';

  @override
  String get yes => 'Yes';

  @override
  String get no => 'No';

  @override
  String get cancel => 'Cancel';

  @override
  String get add => 'Add';

  @override
  String get closeEsc => 'Close (Esc)';

  @override
  String get now => 'Now';

  @override
  String get fromStart => 'From start';

  @override
  String get onAir => 'On air';

  @override
  String get backToLive => 'Back to live';

  @override
  String get retry => 'Retry';

  @override
  String get nextSource => 'Next source';

  @override
  String get sourcesLabel => 'Sources: ';

  @override
  String get unknownSource => 'Unknown source';

  @override
  String get audio => 'Audio';

  @override
  String get archive => 'Archive';

  @override
  String get onAirNow => 'On air now';

  @override
  String get onAirNowUpper => 'ON AIR NOW';

  @override
  String get guideTitle => 'SimpleTV guide';

  @override
  String channelsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count channels',
      one: '$count channel',
    );
    return '$_temp0';
  }

  @override
  String sourcesShort(Object count) {
    return '$count src.';
  }

  @override
  String sourcesAvailable(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count broadcast sources available',
      one: '$count broadcast source available',
    );
    return '$_temp0';
  }

  @override
  String get epgIptvxLite => 'iptvx.one lite (light)';

  @override
  String get epgPwRu => 'epg.pw RU (3 days)';

  @override
  String get selectChannelForGuide =>
      'Select a channel to see its programme guide';

  @override
  String get searchHint => 'Search channels or programmes...';

  @override
  String get allSources => 'All sources';

  @override
  String get noChannels => 'No channels found';

  @override
  String get liveBadge => 'LIVE';

  @override
  String get noProgrammeNow => 'No programme at this time';

  @override
  String get guideNotLoaded => 'Programme guide is not loaded for this channel';

  @override
  String get noProgrammeInfo => 'No programme information for this channel';

  @override
  String get noDescription => 'No description in the programme guide.';

  @override
  String get toGuideGrid => 'To the guide grid';

  @override
  String sourcesTitle(Object name) {
    return 'Sources: $name';
  }

  @override
  String get videoQuality => 'Video quality';

  @override
  String get videoQualityTooltip => 'Choose video quality';

  @override
  String get autoArrow => 'Auto ▾';

  @override
  String mbps(Object value) {
    return '$value Mbit/s';
  }

  @override
  String kbps(Object value) {
    return '$value kbit/s';
  }

  @override
  String get mono => 'mono';

  @override
  String get stereo => 'stereo';

  @override
  String channelsShort(Object count) {
    return '$count ch.';
  }

  @override
  String khz(Object value) {
    return '$value kHz';
  }

  @override
  String nextAt(Object time) {
    return 'Next ($time): ';
  }

  @override
  String get channelsGuide => 'Channels and guide';

  @override
  String get channelsGuideTooltip => 'Channels and guide (L)';

  @override
  String get prevChannelTooltip => 'Previous channel (PageDown / ↓)';

  @override
  String get nextChannelTooltip => 'Next channel (PageUp / ↑)';

  @override
  String get channelInfoTooltip => 'Channel information (I)';

  @override
  String get channelNotSelected => 'No channel selected';

  @override
  String sourceLabel(Object label) {
    return 'Source: $label';
  }

  @override
  String get aspectTooltip => 'Aspect ratio';

  @override
  String get aspectOriginal => 'Ratio: Original';

  @override
  String get aspect169 => 'Ratio: 16:9 (Wide)';

  @override
  String get aspect43 => 'Ratio: 4:3 (Standard)';

  @override
  String get aspect219 => 'Ratio: 21:9 (Cinema)';

  @override
  String get muteTooltip => 'Mute/unmute (M)';

  @override
  String get settingsTooltip => 'Settings (S)';

  @override
  String get lastChannelTooltip => 'Last channel (R)';

  @override
  String get compactTooltip => 'Compact always-on-top window';

  @override
  String get fullscreenTooltip => 'Fullscreen (F / F11)';

  @override
  String get menuGuide => 'Guide and channels (L)';

  @override
  String get menuNextSource => 'Next source';

  @override
  String get menuNextChannel => 'Next channel (PageUp / ↑)';

  @override
  String get menuPrevChannel => 'Previous channel (PageDown / ↓)';

  @override
  String get menuStats => 'Technical statistics (Tab)';

  @override
  String get menuSettings => 'Settings (S)';

  @override
  String get menuFullscreen => 'Fullscreen (F / Enter)';

  @override
  String get tooltipLastChannel => 'Last channel';

  @override
  String get tooltipSound => 'Sound';

  @override
  String get tooltipNormalWindow => 'Normal window';

  @override
  String get importFailed => 'Could not import the playlist';

  @override
  String get dropHint => 'Drop an M3U or M3U8 file up to 16 MB';

  @override
  String connecting(Object index, Object source, Object total) {
    return 'Connecting · $source $index/$total';
  }

  @override
  String get playbackError => 'Stream playback error';

  @override
  String get dropToAdd => 'Release the M3U file to add the playlist';

  @override
  String get addPlaylistTitle => 'Add M3U playlist';

  @override
  String get playlistName => 'Playlist name';

  @override
  String get playlistNameHint => 'For example: My IPTV';

  @override
  String get playlistUrl => 'URL or path to an .m3u file';

  @override
  String get uaOptional => 'User-Agent (optional)';

  @override
  String get epgSourceTitle => 'Programme guide source';

  @override
  String get name => 'Name';

  @override
  String get xmltvUrl => 'XMLTV URL';

  @override
  String get desktopSettingsTitle => 'SimpleTV settings';

  @override
  String get sourcesSection => 'Playlist sources (TVSources)';

  @override
  String get sourcesSubtitle =>
      'Channels from different sources are merged by name';

  @override
  String get appearanceSection => 'Appearance';

  @override
  String get appearanceSubtitle =>
      'Accent colour for buttons, focus and selection';

  @override
  String get accentViolet => 'Indigo';

  @override
  String get accentOrange => 'Orange';

  @override
  String get accentBlue => 'Blue';

  @override
  String get accentTeal => 'Teal';

  @override
  String get accentPink => 'Pink';

  @override
  String get accentRed => 'Red';

  @override
  String get builtIn => 'Built-in';

  @override
  String get updateError => 'Update error';

  @override
  String lastLoad(Object date) {
    return 'Last update: $date';
  }

  @override
  String get noLastLoad => 'No successful update yet';

  @override
  String get controlsSection => 'Controls and navigation';

  @override
  String get reverseZapTitle =>
      'Reverse arrow switching (Up = Next, Down = Prev.)';

  @override
  String get reverseZapSub => 'Natural switching order for remote and keyboard';

  @override
  String get filtersSection => 'Filters and channel merging';

  @override
  String get hideAdult => 'Hide adult channels (18+)';

  @override
  String get hideAdultSub => 'Exclude erotic and late-night channels';

  @override
  String get preferMax => 'Force maximum quality';

  @override
  String get preferMaxSub =>
      'Automatically choose 4K / UHD and 1080p when available';

  @override
  String get hideSd => 'Hide SD duplicates';

  @override
  String get hideSdSub => 'Keep only HD/UHD versions when available';

  @override
  String get hideTz => 'Hide other time zones';

  @override
  String get hideTzSub => 'Exclude duplicates marked (+2), (+4), (+7), Orbita';

  @override
  String get mergeOtt => 'Merge OTT channels';

  @override
  String get mergeOttSub => 'Add OTT versions as backup sources';

  @override
  String get lcn => 'Federal channel order (LCN)';

  @override
  String get lcnSub => 'First 20 mandatory digital TV channels in Russia';

  @override
  String get aspectSection => 'Aspect ratio';

  @override
  String get aspect1610 => '16:10';

  @override
  String get buffer => 'Stream buffer';

  @override
  String get bufferSub => 'Applied the next time a stream is opened';

  @override
  String secondsShort(Object count) {
    return '$count s';
  }

  @override
  String get epgSection => 'Programme guide';

  @override
  String get xmltvSource => 'XMLTV source';

  @override
  String get deleteEpgTooltip => 'Delete programme guide source';

  @override
  String get autoEpg => 'Automatic guide updates';

  @override
  String everyHours(Object count) {
    return 'Every $count h';
  }

  @override
  String get applyPlaylists => 'Apply and refresh playlists';

  @override
  String get refreshEpg => 'Refresh guide (EPG)';

  @override
  String get statsTitle => 'Technical statistics';

  @override
  String get statChannel => 'Channel';

  @override
  String get statSource => 'Source';

  @override
  String get videoFormat => 'Video format';

  @override
  String get videoCodec => 'Video codec';

  @override
  String get audioCodec => 'Audio codec';

  @override
  String get videoOutput => 'Video output';

  @override
  String get decoding => 'Decoding';

  @override
  String get fpsLabel => 'Frames/s';

  @override
  String get statVideoBitrate => 'Video bitrate';

  @override
  String get statAudioBitrate => 'Audio bitrate';

  @override
  String get droppedFrames => 'Dropped frames';

  @override
  String get statBuffer => 'Buffer';

  @override
  String get buffering => 'Buffering';

  @override
  String get statPlaying => 'Playback';

  @override
  String get stateActive => 'Active';

  @override
  String get statePaused => 'Paused';

  @override
  String get drmLabel => 'DRM ClearKey';

  @override
  String get drmShort => 'DRM';

  @override
  String get engine => 'Engine';

  @override
  String get engineValue => 'libmpv / media_kit';

  @override
  String get customSource => 'Custom';

  @override
  String noStreams(Object name) {
    return '$name: no available streams';
  }

  @override
  String get archiveUnavailable =>
      'Archive is not available for the selected programme or source';

  @override
  String get playbackTimeout => 'Playback timeout';

  @override
  String variantFailed(Object channel, Object failed, Object next) {
    return '$channel: $failed is unavailable, trying $next...';
  }

  @override
  String allSourcesDown(Object channel, Object count) {
    return '$channel: all sources are unavailable ($count)';
  }

  @override
  String sourceSelected(Object name) {
    return 'Source selected: $name';
  }

  @override
  String get noOtherArchive => 'This programme has no other archive source';

  @override
  String favouriteAdded(Object name) {
    return 'Added to favourites: $name';
  }

  @override
  String favouriteRemoved(Object name) {
    return 'Removed from favourites: $name';
  }

  @override
  String get soundOn => 'Sound on';

  @override
  String get soundOff => 'Sound off';

  @override
  String get playlistUrlHint => 'https://example.com/playlist.m3u';

  @override
  String get invalidUrl => 'Enter a valid http(s) address';

  @override
  String get groupGeneric => 'General';

  @override
  String aspectFormat(Object ratio) {
    return 'Aspect ratio: $ratio';
  }

  @override
  String get archiveUnavailableSources =>
      'Archive is unavailable in the channel sources';

  @override
  String get streamPlayingConfirmed => 'Playback confirmed';

  @override
  String get streamUnavailable => 'Unavailable';

  @override
  String get streamAvailable => 'Network reachable';

  @override
  String get streamNotChecked => 'Not checked';

  @override
  String loadingPlaylist(Object name) {
    return 'Loading: $name...';
  }

  @override
  String get cacheSaveFailed => 'Could not save the playlist cache';

  @override
  String loadingGuide(Object name) {
    return 'Loading guide: $name...';
  }

  @override
  String get guideUpdated => 'Guide updated';

  @override
  String get guideUpdateFailed => 'Could not update the guide';

  @override
  String get openStreamError => 'Stream open error';
}
