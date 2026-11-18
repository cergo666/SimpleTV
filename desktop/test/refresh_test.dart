import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:simpletv/data/models/channel.dart';
import 'package:simpletv/data/repositories/settings_repository.dart';
import 'package:simpletv/data/models/settings_model.dart';
import 'package:simpletv/data/repositories/playlist_repository.dart';
import 'package:simpletv/data/services/channel_arranger.dart';

class _RealHttp extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory cacheDirectory;

  setUp(() async {
    cacheDirectory = await Directory.systemTemp.createTemp('simpletv-cache-');
    addTearDown(() => cacheDirectory.delete(recursive: true));
  });

  test(
    'Migration drops retired built-ins and preserves custom playlists',
    () async {
      SharedPreferences.setMockInitialValues({
        'sources': jsonEncode([
          const SourceConfig(
            id: 'wink',
            name: 'Wink TV',
            urls: ['https://old.invalid/wink.m3u'],
            builtIn: true,
          ),
          const SourceConfig(
            id: 'limehd',
            name: 'Lime HD',
            urls: [],
            builtIn: true,
          ),
          const SourceConfig(
            id: 'tricolor',
            name: 'Триколор',
            urls: [],
            builtIn: true,
          ),
          const SourceConfig(
            id: 'custom_saved',
            name: 'Свой плейлист',
            urls: ['https://custom.invalid/list.m3u'],
          ),
        ]),
      });
      final settings = SettingsRepository();
      await settings.init();
      final merged = settings.sources;

      expect(merged.map((source) => source.id), [
        'beeline',
        'wink',
        'smotrim',
        'cinerama',
        'sputnik24',
        'vitrina',
        'custom_saved',
      ]);
      expect(merged.first.urls, Defaults.sources.first.urls);
      expect(merged[1].urls, Defaults.sources[1].urls);
      expect(
        merged.singleWhere((source) => source.id == 'smotrim').enabled,
        isFalse,
      );
      expect(merged.last.urls, ['https://custom.invalid/list.m3u']);
    },
  );

  test('Built-in playlists load offline and slot #3 is Матч ТВ', () async {
    final repo = PlaylistRepository(cacheDirectory: cacheDirectory);
    final sources = Defaults.sources
        .where((source) => source.sort < 3)
        .toList();

    final channels = await repo.loadCached(
      sources,
      const ArrangerOptions(standardOrder: true, preferMaxQuality: true),
    );

    expect(channels, isNotEmpty);
    expect(channels.length, greaterThan(300));

    // First channel should be Первый канал
    expect(channels[0].name, 'Первый канал HD');
    expect(channels[0].group, 'Федеральные');
    final first = channels[0].variants;
    expect(channels[5].name, 'Россия К HD');
    expect(channels[5].key, 'россия к');
    expect(channels[5].variants.single.sourceName, 'Beeline');
    expect(first.take(3).map((variant) => variant.sourceName), [
      'Beeline',
      'Wink TV (OTT)',
      'Wink TV',
    ]);
    expect(first.any((variant) => variant.url.contains('/CH_1TVSD/')), isFalse);
    final withSd = repo.arrange(const ArrangerOptions(hideSdDuplicates: false));
    expect(
      withSd.any(
        (channel) => channel.variants.any(
          (variant) => variant.url.contains('/CH_1TVSD/'),
        ),
      ),
      isTrue,
    );
    // Second channel should be Россия 1
    expect(channels[1].name, contains('Россия 1'));
    // Third channel should be Матч ТВ or Матч! HD, and NEVER Матч Премьер!
    expect(channels[2].name, isNot(contains('Премьер')));
    expect(
      channels[2].name,
      anyOf(contains('Матч ТВ'), contains('Матч!'), contains('Матч HD')),
    );

    // Fourth channel should be НТВ
    expect(channels[3].name, contains('НТВ'));
  });

  test(
    'Smotrim adds a reserve stream without duplicating federal channels',
    () async {
      final repo = PlaylistRepository(cacheDirectory: cacheDirectory);
      final sources = [
        for (final source in Defaults.sources.where(
          (source) => source.sort <= 3,
        ))
          if (source.id == 'smotrim')
            SourceConfig.fromJson({...source.toJson(), 'enabled': true})
          else
            source,
      ];
      final channels = await repo.loadCached(
        sources,
        const ArrangerOptions(standardOrder: true, preferMaxQuality: true),
      );

      expect(channels[5].key, 'россия к');
      for (final name in ['Россия 1', 'Россия 24', 'Россия К']) {
        final matches = channels.where(
          (channel) => channel.name == name || channel.name == '$name HD',
        );
        expect(matches, hasLength(1));
        final variants = matches.single.variants;
        expect(variants.first.sourceName, isNot('Смотрим'));
        expect(variants.last.sourceName, 'Смотрим');
        expect(variants.last.url, startsWith('https://live.smotrim.ru/'));
      }
    },
  );

  test(
    'Online update augments built-ins and survives an unavailable server',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final source = SourceConfig(
        id: 'beeline',
        name: 'Beeline',
        kind: StreamKind.beeline,
        builtIn: true,
        urls: [
          'asset://playlists/beeline.m3u',
          'http://127.0.0.1:${server.port}/beeline.m3u',
        ],
      );
      final repo = PlaylistRepository(cacheDirectory: cacheDirectory);
      const options = ArrangerOptions();
      final offline = await repo.loadCached([source], options);
      final culture = offline.singleWhere(
        (channel) => channel.key == 'россия к',
      );
      var available = true;
      server.listen((request) async {
        if (available) {
          request.response.write('''
#EXTM3U
#EXTINF:-1 catchup-days="2",Россия К
${culture.variants.single.url}
#EXTINF:-1,Fixture Online
https://example.invalid/online.mpd
''');
        } else {
          request.response.statusCode = HttpStatus.serviceUnavailable;
        }
        await request.response.close();
      });

      final merged = await HttpOverrides.runWithHttpOverrides(
        () => repo.refreshAll([source], options),
        _RealHttp(),
      );
      final updated = merged.singleWhere(
        (channel) => channel.key == culture.key,
      );
      expect(merged.length, offline.length + 1);
      expect(updated.name, culture.name);
      expect(updated.logoUrl, culture.logoUrl);
      expect(updated.epgId, culture.epgId);
      expect(updated.variants.single.catchupDays, 2);

      available = false;
      final reopened = PlaylistRepository(cacheDirectory: cacheDirectory);
      expect(
        await reopened.loadCached([source], options),
        hasLength(merged.length),
      );
      expect(
        await HttpOverrides.runWithHttpOverrides(
          () => reopened.refreshAll([source], options),
          _RealHttp(),
        ),
        hasLength(merged.length),
      );
      expect(reopened.sourceStatus(source.id)?.failed, isTrue);
    },
  );

  test(
    'Bundled sources replace old snapshots before the first playback',
    () async {
      final file = File.fromUri(
        cacheDirectory.uri.resolve('playlist_sources.json'),
      );
      await file.writeAsString(
        jsonEncode({
          'beeline': {
            'channels': [
              {
                'name': 'Россия К',
                'key': 'россия к',
                'group': '',
                'variant': {
                  'url': 'https://old.invalid/kultura',
                  'sourceName': 'Beeline',
                },
                'sourceSort': 2,
              },
            ],
          },
        }),
      );
      final repo = PlaylistRepository(cacheDirectory: cacheDirectory);
      final channels = await repo.loadCached(
        Defaults.sources,
        const ArrangerOptions(),
      );
      expect(channels[5].name, 'Россия К HD');
      expect(channels[5].variants.first.url, contains('channel495.isml'));
      expect(channels[5].variants.map((variant) => variant.sourceName), [
        'Beeline',
        'Витрина ТВ',
      ]);
    },
  );

  test(
    'A failed source retains its snapshot when another source refreshes',
    () async {
      final a = File.fromUri(cacheDirectory.uri.resolve('a.m3u'));
      final b = File.fromUri(cacheDirectory.uri.resolve('b.m3u'));
      final sources = [
        SourceConfig(id: 'a', name: 'A', urls: [a.path]),
        SourceConfig(id: 'b', name: 'B', urls: [b.path]),
      ];
      const options = ArrangerOptions();
      final repo = PlaylistRepository(cacheDirectory: cacheDirectory);

      await a.writeAsString(
        '#EXTM3U\n#EXTINF:-1,Alpha\nhttps://test.invalid/alpha.m3u8\n',
      );
      await b.writeAsString(
        '#EXTM3U\n#EXTINF:-1,Beta\nhttps://test.invalid/beta.m3u8\n',
      );
      final first = await repo.refreshAll(sources, options);
      expect(
        first.map((channel) => channel.name),
        unorderedEquals(['Alpha', 'Beta']),
      );
      final lastSuccess = repo.sourceStatus('a')!.lastSuccess;

      await a.writeAsString('<html>Service unavailable</html>');
      await b.writeAsString(
        '#EXTM3U\n#EXTINF:-1,Gamma\nhttps://test.invalid/gamma.m3u8\n',
      );
      final refreshed = await repo.refreshAll(sources, options);
      expect(
        refreshed.map((channel) => channel.name),
        unorderedEquals(['Alpha', 'Gamma']),
      );
      expect(repo.sourceStatus('a'), (lastSuccess: lastSuccess, failed: true));
      expect(repo.sourceStatus('b')!.failed, isFalse);

      final restarted = PlaylistRepository(cacheDirectory: cacheDirectory);
      final cached = await restarted.loadCached(sources, options);
      expect(
        cached.map((channel) => channel.name),
        unorderedEquals(['Alpha', 'Gamma']),
      );
      expect(restarted.sourceStatus('a'), repo.sourceStatus('a'));
      final remaining = await restarted.loadCached([sources.last], options);
      expect(remaining.single.name, 'Gamma');
    },
  );

  test(
    'Legacy cache retains variants from enabled configured sources',
    () async {
      final legacy = File.fromUri(
        cacheDirectory.uri.resolve('channels_cache.json'),
      );
      await legacy.writeAsString(
        jsonEncode([
          const Channel(
            key: 'alpha',
            name: 'Alpha',
            group: '',
            variants: [
              StreamVariant(
                url: 'https://test.invalid/a.m3u8',
                sourceName: 'A',
              ),
              StreamVariant(
                url: 'https://test.invalid/b.m3u8',
                sourceName: 'B',
              ),
            ],
          ),
        ]),
      );
      const sources = [
        SourceConfig(id: 'a', name: 'A', urls: []),
        SourceConfig(id: 'b', name: 'B', urls: [], enabled: false),
      ];
      final repo = PlaylistRepository(cacheDirectory: cacheDirectory);
      final channels = await repo.refreshAll(sources, const ArrangerOptions());
      expect(channels.single.variants.single.sourceName, 'A');
      expect(repo.sourceStatus('a'), (lastSuccess: null, failed: true));
    },
  );
}
