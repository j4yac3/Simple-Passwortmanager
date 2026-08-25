package org.example;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpUtil {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    private TotpUtil() {}

    public static String normalize(String secret) {
        if (secret == null) return "";
        String trimmed = secret.trim();
        if (trimmed.toLowerCase().startsWith("otpauth://")) {
            int idx = trimmed.toLowerCase().indexOf("secret=");
            if (idx < 0) return "";
            String sub = trimmed.substring(idx + 7);
            int amp = sub.indexOf('&');
            if (amp >= 0) sub = sub.substring(0, amp);
            return sub.replaceAll("[\\s\\-]", "").toUpperCase();
        }
        return trimmed.replaceAll("[\\s\\-]", "").toUpperCase();
    }

    public static boolean isPlausible(String secret) {
        byte[] key = base32Decode(normalize(secret));
        return key != null && key.length >= 10;
    }

    public static String currentCode(String secret) {
        return generate(secret, System.currentTimeMillis() / 1000L);
    }

    public static String generate(String secret, long unixSeconds) {
        byte[] key = base32Decode(normalize(secret));
        if (key == null || key.length == 0) return "";

        long counter = unixSeconds / STEP_SECONDS;
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] base32Decode(String input) {
        if (input == null || input.isEmpty()) return null;
        StringBuilder bits = new StringBuilder();
        for (char c : input.toCharArray()) {
            int idx = BASE32_ALPHABET.indexOf(c);
            if (idx < 0) return null;
            String bin = Integer.toBinaryString(idx);
            bits.append("00000", 0, 5 - bin.length()).append(bin);
        }
        int usableBits = bits.length() / 8 * 8;
        byte[] out = new byte[usableBits / 8];
        for (int i = 0; i < usableBits; i += 8) {
            out[i / 8] = (byte) Integer.parseInt(bits.substring(i, i + 8), 2);
        }
        return out;
    }
}
