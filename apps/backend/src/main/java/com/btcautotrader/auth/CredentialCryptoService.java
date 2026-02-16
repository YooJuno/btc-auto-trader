package com.btcautotrader.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CredentialCryptoService {
    private static final Logger log = LoggerFactory.getLogger(CredentialCryptoService.class);
    private static final String FALLBACK_KEY = "btc-auto-trader-default-credential-key";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCryptoService(
            @Value("${app.exchange-key-encryption-key:${APP_EXCHANGE_KEY_ENCRYPTION_KEY:}}") String keyMaterial
    ) {
        String material = keyMaterial == null ? "" : keyMaterial.trim();
        if (material.isEmpty()) {
            material = FALLBACK_KEY;
            log.warn("APP_EXCHANGE_KEY_ENCRYPTION_KEY is not set. Using fallback key for credential encryption.");
        }
        this.secretKeySpec = new SecretKeySpec(sha256(material), "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText is required");
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to encrypt credential", ex);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new IllegalArgumentException("cipherText is required");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("invalid credential payload");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to decrypt credential", ex);
        }
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize encryption key", ex);
        }
    }
}
