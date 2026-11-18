import 'dart:convert';

import 'package:flutter/services.dart';

import 'text_helper.dart';

class LogoService {
  static final Map<String, String> _topLogos = {
    'первый канал': 'https://iptvx.one/picons/pervy.png',
    'россия 1': 'https://iptvx.one/picons/rossia1.png',
    'матч тв': 'https://iptvx.one/picons/match-tv.png',
    'нтв': 'https://iptvx.one/picons/ntv.png',
    'пятый канал': 'https://iptvx.one/picons/5kanal-ru.png',
    '5 канал': 'https://iptvx.one/picons/5kanal-ru.png',
    'россия к': 'https://iptvx.one/picons/kultura.png',
    'культура': 'https://iptvx.one/picons/kultura.png',
    'россия 24': 'https://iptvx.one/picons/rossia-24.png',
    'карусель': 'https://iptvx.one/picons/karusel.png',
    'отр': 'https://iptvx.one/picons/otr.png',
    'тв центр': 'https://iptvx.one/picons/tvcentr.png',
    'твц': 'https://iptvx.one/picons/tvcentr.png',
    'рен тв': 'https://iptvx.one/picons/rentv.png',
    'спас': 'https://iptvx.one/picons/spas.png',
    'стс': 'https://iptvx.one/picons/sts.png',
    'домашний': 'https://iptvx.one/picons/domashny.png',
    'тв 3': 'https://iptvx.one/picons/tv3-ru.png',
    'тв3': 'https://iptvx.one/picons/tv3-ru.png',
    'пятница': 'https://iptvx.one/picons/piatnica.png',
    'звезда': 'https://iptvx.one/picons/zvezda.png',
    'мир': 'https://iptvx.one/picons/mir.png',
    'мир 24': 'https://iptvx.one/picons/mir24.png',
    'тнт': 'https://iptvx.one/picons/tnt.png',
    'муз тв': 'https://iptvx.one/picons/muztv.png',
    '2x2': 'https://iptvx.one/picons/2na2.png',
    '2х2': 'https://iptvx.one/picons/2na2.png',
    '2 x 2': 'https://iptvx.one/picons/2na2.png',
    '2 х 2': 'https://iptvx.one/picons/2na2.png',
    'че': 'https://iptvx.one/picons/che.png',
    'тнт4': 'https://iptvx.one/picons/tnt4.png',
    'тнт 4': 'https://iptvx.one/picons/tnt4.png',
    'суббота': 'https://iptvx.one/picons/subbota.png',
    'ю': 'https://iptvx.one/picons/yu.png',
    'солнце': 'https://iptvx.one/picons/solnce.png',
    'стс love': 'https://iptvx.one/picons/sts-love.png',
    'тнт music': 'https://iptvx.one/picons/tnt-music.png',
    'матч премьер': 'https://iptvx.one/picons/match-premier.png',
    'матч футбол 1': 'https://iptvx.one/picons/match-futbol1.png',
    'матч футбол 2': 'https://iptvx.one/picons/match-futbol2.png',
    'матч футбол 3': 'https://iptvx.one/picons/match-futbol3.png',
    'матч боец': 'https://iptvx.one/picons/match-boec.png',
    'матч страна': 'https://iptvx.one/picons/match-strana.png',
    'матч арена': 'https://iptvx.one/picons/match-arena.png',
    'матч игра': 'https://iptvx.one/picons/match-igra.png',
    'кхл': 'https://iptvx.one/picons/khl.png',
    'кхл prime': 'https://iptvx.one/picons/khl-prime.png',
    'кинопремьера': 'https://iptvx.one/picons/kinopremiera.png',
    'кинохит': 'https://iptvx.one/picons/kinohit.png',
    'киносемья': 'https://iptvx.one/picons/kinosemya.png',
    'киносвидание': 'https://iptvx.one/picons/kinosvidanie.png',
    'киномикс': 'https://iptvx.one/picons/kinomiks.png',
    'кинокомедия': 'https://iptvx.one/picons/kinokomedia.png',
    'киносерия': 'https://iptvx.one/picons/kinoseria.png',
    'киноужас': 'https://iptvx.one/picons/kinoujas.png',
    'родное кино': 'https://iptvx.one/picons/rodnoe-kino.png',
    'мужское кино': 'https://iptvx.one/picons/mujskoe-kino.png',
    'индийское кино': 'https://iptvx.one/picons/indiyskoe-kino.png',
    'мосфильм': 'https://iptvx.one/picons/mosfilm.png',
    'дом кино': 'https://iptvx.one/picons/dom-kino.png',
    'дом кино премиум': 'https://iptvx.one/picons/dom-kino-premium.png',
    'победа': 'https://iptvx.one/picons/pobeda.png',
    'мульт': 'https://iptvx.one/picons/mult.png',
    'о!': 'https://iptvx.one/picons/o.png',
    'мультимузыка': 'https://iptvx.one/picons/multimuzyka.png',
    'рыжий': 'https://iptvx.one/picons/ryzhiy.png',
    'радость моя': 'https://iptvx.one/picons/radost-moya.png',
    'ani': 'https://iptvx.one/picons/ani.png',
    'мультиландия': 'https://iptvx.one/picons/multilandia.png',
    'моя планета': 'https://iptvx.one/picons/moya-planeta.png',
    'наука': 'https://iptvx.one/picons/nauka.png',
    'живая планета': 'https://iptvx.one/picons/zhivaya-planeta.png',
    'история': 'https://iptvx.one/picons/istoria.png',
    'т24': 'https://iptvx.one/picons/t24.png',
    'доктор': 'https://iptvx.one/picons/doktor.png',
    'discovery': 'https://iptvx.one/picons/discovery.png',
    'animal planet': 'https://iptvx.one/picons/animal-planet.png',
    'national geographic': 'https://iptvx.one/picons/nat-geo.png',
    'nat geo wild': 'https://iptvx.one/picons/nat-geo-wild.png',
    'viju tv1000': 'https://iptvx.one/picons/tv1000.png',
    'viju tv1000 русское': 'https://iptvx.one/picons/tv1000-russian.png',
    'viju tv1000 action': 'https://iptvx.one/picons/tv1000-action.png',
    'рбк': 'https://iptvx.one/picons/rbc.png',
    'rbc': 'https://iptvx.one/picons/rbc.png',
    'москва 24': 'https://iptvx.one/picons/moskva24.png',
    'москва доверие': 'https://iptvx.one/picons/moskva-doverie.png',
    '78': 'https://iptvx.one/picons/78-spb.png',
    'санкт петербург': 'https://iptvx.one/picons/topspb.png',
    'известия': 'https://iptvx.one/picons/izvestia.png',
    'европа плюс тв': 'https://iptvx.one/picons/europa-plus-tv.png',
    'ру тв': 'https://iptvx.one/picons/ru-tv.png',
    'ru tv': 'https://iptvx.one/picons/ru-tv.png',
    'bridge tv': 'https://iptvx.one/picons/bridge-tv.png',
    'bridge tv русский хит': 'https://iptvx.one/picons/bridge-tv-rus.png',
    'bridge tv classic': 'https://iptvx.one/picons/bridge-tv-classic.png',
    'шансон тв': 'https://iptvx.one/picons/shanson-tv.png',
  };

  static Map<String, String> _catalog = const {};

  static Future<void> loadCatalog() async {
    final json = await rootBundle.loadString('assets/catalog/logos.json');
    _catalog = Map<String, String>.from(jsonDecode(json) as Map);
  }

  static String? findCatalogLogo(String name) =>
      _catalog[TextHelper.channelKey(name)] ??
      _catalog[TextHelper.looseKey(name)];

  static String? findLogo(String name) {
    final k = TextHelper.channelKey(name);
    final lk = TextHelper.looseKey(name);
    return _topLogos[lk] ?? _topLogos[k];
  }
}
