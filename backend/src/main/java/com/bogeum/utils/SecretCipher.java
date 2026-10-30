package com.bogeum.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.bogeum.config.OpenBankingProperties;
import com.bogeum.domain.openbanking.OpenBankingException;

import lombok.RequiredArgsConstructor;

/** 계좌번호와 OAuth 토큰을 매번 다른 IV의 AES-256-GCM으로 암호화한다. */
@Component
@RequiredArgsConstructor
public class SecretCipher {
	private static final SecureRandom RANDOM = new SecureRandom();
	private final OpenBankingProperties properties;

	public String encrypt(String value) {
		try {
			byte[] iv = new byte[12];
			RANDOM.nextBytes(iv);
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
			return Base64.getEncoder().encodeToString(iv) + ":"
				+ Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw OpenBankingException.unavailable();
		}
	}

	public String decrypt(String value) {
		try {
			String[] parts = value.split(":", 2);
			if (parts.length != 2) {
				throw OpenBankingException.unavailable();
			}
			byte[] iv = Base64.getDecoder().decode(parts[0]);
			if (iv.length != 12) {
				throw OpenBankingException.unavailable();
			}
			return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(
				Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw OpenBankingException.unavailable();
		}
	}

	private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
		if (properties.encryptionKey() == null || properties.encryptionKey().isBlank()) {
			throw OpenBankingException.unavailable();
		}
		byte[] key = Base64.getDecoder().decode(properties.encryptionKey());
		if (key.length != 32) {
			throw OpenBankingException.unavailable();
		}
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
		return cipher;
	}
}
