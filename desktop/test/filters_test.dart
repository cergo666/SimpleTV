import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:simpletv/data/models/channel.dart';
import 'package:simpletv/data/services/player_service.dart';
import 'package:simpletv/ui/view_models/player_view_model.dart';

class _FakePlayerService extends PlayerService {
  final _states = StreamController<PlaybackInfo>.broadcast();

  @override
  bool get isInitialized => true;

  @override
  Stream<PlaybackInfo> get stateStream => _states.stream;

  @override
  Future<void> play(
    Channel channel, {
    required int attemptId,
    int variantIndex = 0,
    Programme? archive,
    String? defaultUserAgent,
  }) async {}

  @override
  void dispose() => _states.close();
}

Channel _channel(String key) => Channel(
  key: key,
  name: 'Channel $key',
  group: key == 'b' ? 'Sport' : 'News',
  variants: [
    StreamVariant(
      url: 'https://test.invalid/$key.m3u8',
      sourceName: key == 'b' ? 'Beeline' : 'Wink',
    ),
  ],
);

void main() {
  test(
    'filteredChannels applies source, group, search and special filters',
    () {
      final vm = PlayerViewModel(playerService: _FakePlayerService());
      addTearDown(vm.dispose);
      final a = _channel('a');
      final b = _channel('b');
      final c = _channel('c');
      vm.debugSetChannels([a, b, c]);

      // Специальные группы присутствуют в списке фильтров
      expect(vm.groups.take(2), [FilterKeys.all, FilterKeys.favourites]);

      // Группа из плейлиста
      vm.selectGroup('News');
      expect(vm.filteredChannels.map((ch) => ch.key), ['a', 'c']);

      // Источник
      vm.selectGroup(FilterKeys.all);
      vm.selectSource('Beeline');
      expect(vm.filteredChannels.map((ch) => ch.key), ['b']);
      vm.selectSource(FilterKeys.allSources);

      // Поиск по названию
      vm.setSearchQuery('channel b');
      expect(vm.filteredChannels.map((ch) => ch.key), ['b']);
      vm.setSearchQuery('');

      // Избранное и недавние
      vm.toggleFavorite(a);
      vm.play(b);
      vm.selectGroup(FilterKeys.favourites);
      expect(vm.filteredChannels.map((ch) => ch.key), ['a']);
      vm.selectGroup(FilterKeys.recent);
      expect(vm.filteredChannels.map((ch) => ch.key), ['b']);
      vm.selectGroup(FilterKeys.all);
      expect(vm.filteredChannels, hasLength(3));
    },
  );
}
