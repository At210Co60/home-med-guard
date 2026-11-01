package com.homemedguard.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 健康数据字段级加密：AES-256-GCM，12 字节随机 IV 前置存进密文，密文带 enc:v1: 前缀。
 * 没带前缀的值原样返回，这样历史明文列不会在解密时炸掉。
 */
public final class AesGcmCodec {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static volatile SecretKey key;

    private AesGcmCodec() {
    }

    public static void use(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalStateException("app.crypto.data-key 必须是 base64 编码的 32 字节密钥");
        }
        key = new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isBlank() || plain.startsWith(PREFIX)) {
            return plain;
        }
        SecretKey current = requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception ex) {
            throw new IllegalStateException("健康数据加密失败", ex);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        SecretKey current = requireKey();
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, current,
                    new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("健康数据解密失败：密钥是否换过或数据被改写", ex);
        }
    }

    private static SecretKey requireKey() {
        SecretKey current = key;
        if (current == null) {
            throw new IllegalStateException("加密密钥未初始化（检查 app.crypto.data-key）");
        }
        return current;
    }
}
