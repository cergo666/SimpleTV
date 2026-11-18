package io.mediacast.simpletv.protection;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

public final class StringVault {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile byte[] signer;

    static {
        System.loadLibrary("stvcodec");
    }

    private StringVault() {}

    @SuppressWarnings("deprecation")
    public static void initialize(Context context) {
        PackageManager manager = context.getPackageManager();
        try {
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
            } else {
                signatures = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            }
            if (signatures == null || signatures.length != 1) throw new SecurityException();
            signer = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException error) {
            throw new SecurityException(error);
        }
    }

    public static String reveal(String encoded) {
        String cached = CACHE.get(encoded);
        if (cached != null) return cached;
        byte[] bytes = unpack(encoded);
        char[] chars = new char[bytes.length / 2];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ((bytes[i * 2] & 255) << 8 | (bytes[i * 2 + 1] & 255));
        }
        String decoded = new String(chars).intern();
        String previous = CACHE.putIfAbsent(encoded, decoded);
        return previous == null ? decoded : previous;
    }

    public static byte[] unpack(String encoded) {
        return decode(Base64.decode(encoded, Base64.NO_WRAP), signer);
    }

    private static native byte[] decode(byte[] input, byte[] signerDigest);
}
