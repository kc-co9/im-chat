package com.co.kc.imchat.management.iam.sdk.session.crypto;

import com.co.kc.imchat.common.utils.AssertUtils;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用每次随机 Nonce 的 AES-256-GCM 保护应用 Session 凭据。 */
public class AesGcmIamSessionCipher implements IamSessionCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    public AesGcmIamSessionCipher(byte[] keyBytes) {
        this(keyBytes, new SecureRandom());
    }

    AesGcmIamSessionCipher(byte[] keyBytes, SecureRandom secureRandom) {
        AssertUtils.argNotNull("IAM Session encryption key must not be null", keyBytes);
        AssertUtils.argTrue("IAM Session encryption key must contain 32 bytes",
                keyBytes.length == 32);
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
        this.secureRandom = secureRandom;
    }

    @Override
    public String encrypt(String value) {
        AssertUtils.argNotBlank("IAM Session plaintext must not be blank", value);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("IAM Session encryption failed", exception);
        }
    }

    @Override
    public String decrypt(String encryptedValue) {
        AssertUtils.argNotBlank("Encrypted IAM Session value must not be blank", encryptedValue);
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(encryptedValue);
            if (encoded.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Encrypted IAM Session value is malformed");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[encoded.length - NONCE_BYTES];
            ByteBuffer.wrap(encoded).get(nonce).get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw new IllegalArgumentException("Encrypted IAM Session value was tampered", exception);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Encrypted IAM Session value is invalid", exception);
        }
    }
}
