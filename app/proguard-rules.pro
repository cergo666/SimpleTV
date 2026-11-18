# Media3 загружает расширения-декодеры по имени класса через рефлексию —
# эти классы нельзя переименовывать/удалять, иначе ffmpeg-аудио не подключится.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
# JNI_OnLoad registers this method by class name and descriptor.
-keepclasseswithmembernames class io.mediacast.simpletv.protection.StringVault {
    private static native byte[] decode(byte[], byte[]);
}
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
