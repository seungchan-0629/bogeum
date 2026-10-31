package com.bogeum.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bogeum.common.exception.ErrorCode;
import com.bogeum.config.OpenBankingConfig.Properties;
import com.bogeum.domain.openbanking.OpenBankingException;

class SecretCipherTest {

	private final SecretCipher cipher = cipher(new byte[32]);

	@Test
	@DisplayName("계좌번호·한글·인증 토큰을 원문 노출 없이 암호화하고 복원한다")
	void encryptsAndDecryptsUtf8Secrets() {
		String secret = "계좌:1234567890/access-token-비밀";

		String encrypted = cipher.encrypt(secret);

		assertFalse(encrypted.contains(secret));
		assertEquals(secret, cipher.decrypt(encrypted));
	}

	@Test
	@DisplayName("같은 원문도 매번 다른 12바이트 IV와 암호문으로 저장한다")
	void usesFreshIvForEveryEncryption() {
		String first = cipher.encrypt("same-secret");
		String second = cipher.encrypt("same-secret");

		assertNotEquals(first, second);
		assertNotEquals(first.split(":")[0], second.split(":")[0]);
		assertEquals(12, Base64.getDecoder().decode(first.split(":")[0]).length);
		assertEquals("same-secret", cipher.decrypt(first));
		assertEquals("same-secret", cipher.decrypt(second));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	@DisplayName("IV나 암호문이 한 비트라도 바뀌면 인증에 실패하고 원문을 반환하지 않는다")
	void rejectsTamperedCiphertextOrIv(int partIndex) {
		String[] parts = cipher.encrypt("secret-not-to-leak").split(":");
		byte[] tampered = Base64.getDecoder().decode(parts[partIndex]);
		tampered[0] ^= 1;
		parts[partIndex] = Base64.getEncoder().encodeToString(tampered);

		OpenBankingException exception = assertThrows(OpenBankingException.class,
			() -> cipher.decrypt(String.join(":", parts)));

		assertEquals(ErrorCode.OPEN_BANKING_UNAVAILABLE, exception.getErrorCode());
		assertFalse(exception.getMessage().contains("secret-not-to-leak"));
	}

	@Test
	@DisplayName("다른 암호화 키로는 저장된 비밀값을 복호화할 수 없다")
	void rejectsWrongKey() {
		byte[] anotherKey = new byte[32];
		Arrays.fill(anotherKey, (byte) 1);
		var anotherCipher = cipher(anotherKey);
		String encrypted = cipher.encrypt("account-number");

		assertThrows(OpenBankingException.class, () -> anotherCipher.decrypt(encrypted));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "plain-text-account", ":", "invalid!:invalid!", "AA==:AA=="})
	@DisplayName("형식이 잘못되었거나 평문인 저장 값은 복호화하지 않는다")
	void rejectsMalformedStoredValue(String storedValue) {
		assertThrows(OpenBankingException.class, () -> cipher.decrypt(storedValue));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 16, 24, 31, 33})
	@DisplayName("AES-256에 필요한 32바이트가 아닌 키로는 저장하지 않는다")
	void requires256BitKey(int keyLength) {
		var invalidCipher = cipher(new byte[keyLength]);

		assertThrows(OpenBankingException.class, () -> invalidCipher.encrypt("secret"));
	}

	private static SecretCipher cipher(byte[] key) {
		return new SecretCipher(new Properties(false, null, null, null, null, null,
			null, null, Base64.getEncoder().encodeToString(key), null));
	}
}
