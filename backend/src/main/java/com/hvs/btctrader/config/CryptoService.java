package com.hvs.btctrader.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

@Service
public class CryptoService {
	private static final String AES = "AES";
	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH = 16;
	private static final int IV_LENGTH = 12;

	private final AppProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();

	public CryptoService(AppProperties properties) {
		this.properties = properties;
	}

	public String encrypt(String plainText) {
		if (plainText == null || plainText.isBlank()) {
			return plainText;
		}
		SecretKey key = loadKey();
		byte[] iv = new byte[IV_LENGTH];
		secureRandom.nextBytes(iv);
		byte[] cipherText = doCipher(Cipher.ENCRYPT_MODE, key, iv, plainText.getBytes(StandardCharsets.UTF_8));
		ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
		buffer.put(iv);
		buffer.put(cipherText);
		return Base64.getEncoder().encodeToString(buffer.array());
	}

	public String decrypt(String encrypted) {
		if (encrypted == null || encrypted.isBlank()) {
			return encrypted;
		}
		SecretKey key = loadKey();
		byte[] payload = Base64.getDecoder().decode(encrypted);
		ByteBuffer buffer = ByteBuffer.wrap(payload);
		byte[] iv = new byte[IV_LENGTH];
		buffer.get(iv);
		byte[] cipherText = new byte[buffer.remaining()];
		buffer.get(cipherText);
		byte[] plainBytes = doCipher(Cipher.DECRYPT_MODE, key, iv, cipherText);
		return new String(plainBytes, StandardCharsets.UTF_8);
	}

	private SecretKey loadKey() {
		String keyBase64 = properties.getCrypto().getKey();
		if (keyBase64 == null || keyBase64.isBlank()) {
			throw new IllegalStateException("APP_ENC_KEY is required for encryption.");
		}
		byte[] raw = Base64.getDecoder().decode(keyBase64);
		return new SecretKeySpec(raw, AES);
	}

	private byte[] doCipher(int mode, SecretKey key, byte[] iv, byte[] data) {
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
			cipher.init(mode, key, spec);
			return cipher.doFinal(data);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Crypto operation failed.", ex);
		}
	}
}
