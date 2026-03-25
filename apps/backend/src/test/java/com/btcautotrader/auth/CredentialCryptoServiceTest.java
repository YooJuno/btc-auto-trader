package com.btcautotrader.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCryptoServiceTest {
    @Test
    void encryptAndDecrypt_roundTrip() {
        CredentialCryptoService service = new CredentialCryptoService("test-encryption-key");

        String encrypted = service.encrypt("sample-secret-value");
        String decrypted = service.decrypt(encrypted);

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo("sample-secret-value");
        assertThat(decrypted).isEqualTo("sample-secret-value");
    }

    @Test
    void missingEncryptionKey_throwsImmediately() {
        assertThatThrownBy(() -> new CredentialCryptoService("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_EXCHANGE_KEY_ENCRYPTION_KEY");
    }
}
