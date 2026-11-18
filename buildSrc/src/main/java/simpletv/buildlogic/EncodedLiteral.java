package simpletv.buildlogic;

import java.util.Base64;
import java.util.HexFormat;

final class EncodedLiteral {
    private EncodedLiteral() {}

    static String text(String text, String context, byte[] signer) {
        byte[] bytes = new byte[text.length() * 2];
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            bytes[i * 2] = (byte) (ch >>> 8);
            bytes[i * 2 + 1] = (byte) ch;
        }
        return encode(bytes, 31 * context.hashCode() + text.hashCode(), signer);
    }

    static byte[] signerBytes(String fingerprint) {
        return HexFormat.of().parseHex(fingerprint);
    }

    static String encode(byte[] bytes, int seed, byte[] signer) {
        int state = seed != 0 ? seed : 0x6D2B79F5;
        // Format: signer flag, seed, then an encoded four-byte marker and payload.
        byte[] output = new byte[bytes.length + 9];
        output[0] = (byte) (signer.length == 0 ? 0 : 1);
        for (int i = 0; i < 4; i++) output[i + 1] = (byte) (state >>> (24 - i * 8));
        for (int i = 0; i < bytes.length + 4; i++) {
            if (signer.length != 0) state ^= (signer[i & 31] & 255) << ((i & 3) * 8);
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            int value = i < 4 ? 0x53545632 >>> (24 - i * 8) : bytes[i - 4];
            output[i + 5] = (byte) (value ^ state);
        }
        return Base64.getEncoder().encodeToString(output);
    }
}
