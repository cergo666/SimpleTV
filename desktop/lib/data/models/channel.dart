enum StreamKind {
  generic('generic'),
  wink('wink'),
  beeline('beeline');

  const StreamKind(this.storageId);

  // Persisted identifiers must survive renaming in an obfuscated build.
  final String storageId;

  static StreamKind fromStorageId(Object? value) => values.firstWhere(
    (kind) => kind.storageId == value,
    orElse: () => generic,
  );
}

class StreamVariant {
  final String url;
  final String sourceName;
  final StreamKind kind;
  final String? userAgent;
  final String? referer;
  final String? clearKey;
  final String? catchup;
  final num? catchupDays;
  final bool isOtt;

  const StreamVariant({
    required this.url,
    required this.sourceName,
    this.kind = StreamKind.generic,
    this.userAgent,
    this.referer,
    this.clearKey,
    this.catchup,
    this.catchupDays,
    this.isOtt = false,
  });

  Map<String, dynamic> toJson() => {
    'url': url,
    'sourceName': sourceName,
    'kind': kind.storageId,
    'userAgent': userAgent,
    'referer': referer,
    'clearKey': clearKey,
    'catchup': catchup,
    'catchupDays': catchupDays,
    'isOtt': isOtt,
  };

  factory StreamVariant.fromJson(Map<String, dynamic> json) => StreamVariant(
    url: json['url'] as String? ?? '',
    sourceName: json['sourceName'] as String? ?? '',
    kind: StreamKind.fromStorageId(json['kind']),
    userAgent: json['userAgent'] as String?,
    referer: json['referer'] as String?,
    clearKey: json['clearKey'] as String?,
    catchup: json['catchup'] as String?,
    catchupDays: json['catchupDays'] as num?,
    isOtt: json['isOtt'] as bool? ?? false,
  );
}

class Channel {
  final String key;
  final String name;
  final String group;
  final String? logoUrl;
  final String? epgId;
  final int number;
  final List<StreamVariant> variants;

  const Channel({
    required this.key,
    required this.name,
    required this.group,
    this.logoUrl,
    this.epgId,
    this.number = 0,
    this.variants = const [],
  });

  Channel copyWith({
    String? key,
    String? name,
    String? group,
    String? logoUrl,
    String? epgId,
    int? number,
    List<StreamVariant>? variants,
  }) => Channel(
    key: key ?? this.key,
    name: name ?? this.name,
    group: group ?? this.group,
    logoUrl: logoUrl ?? this.logoUrl,
    epgId: epgId ?? this.epgId,
    number: number ?? this.number,
    variants: variants ?? this.variants,
  );

  Map<String, dynamic> toJson() => {
    'key': key,
    'name': name,
    'group': group,
    'logoUrl': logoUrl,
    'epgId': epgId,
    'number': number,
    'variants': variants.map((v) => v.toJson()).toList(),
  };

  factory Channel.fromJson(Map<String, dynamic> json) => Channel(
    key: json['key'] as String? ?? '',
    name: json['name'] as String? ?? '',
    group: json['group'] as String? ?? 'Общие',
    logoUrl: json['logoUrl'] as String?,
    epgId: json['epgId'] as String?,
    number: json['number'] as int? ?? 0,
    variants:
        (json['variants'] as List<dynamic>?)
            ?.map((v) => StreamVariant.fromJson(v as Map<String, dynamic>))
            .toList() ??
        [],
  );
}

class Programme {
  final String channelId;
  final String title;
  final String? description;
  final DateTime start;
  final DateTime stop;
  final String? category;

  const Programme({
    required this.channelId,
    required this.title,
    this.description,
    required this.start,
    required this.stop,
    this.category,
  });

  double get progress {
    final now = DateTime.now();
    if (now.isBefore(start)) return 0.0;
    if (now.isAfter(stop)) return 1.0;
    final total = stop.difference(start).inMilliseconds;
    if (total <= 0) return 0.0;
    final cur = now.difference(start).inMilliseconds;
    return (cur / total).clamp(0.0, 1.0);
  }

  Map<String, dynamic> toJson() => {
    'channelId': channelId,
    'title': title,
    'description': description,
    'start': start.millisecondsSinceEpoch,
    'stop': stop.millisecondsSinceEpoch,
    'category': category,
  };

  factory Programme.fromJson(Map<String, dynamic> json) => Programme(
    channelId: json['channelId'] as String? ?? '',
    title: json['title'] as String? ?? '',
    description: json['description'] as String?,
    start: DateTime.fromMillisecondsSinceEpoch(json['start'] as int? ?? 0),
    stop: DateTime.fromMillisecondsSinceEpoch(json['stop'] as int? ?? 0),
    category: json['category'] as String?,
  );
}
