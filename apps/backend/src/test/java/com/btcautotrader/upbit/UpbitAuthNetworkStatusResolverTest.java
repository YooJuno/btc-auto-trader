package com.btcautotrader.upbit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpbitAuthNetworkStatusResolverTest {
    @Test
    void fromError_returnsIpNotWhitelistedForNoAuthorizationIpCode() {
        UpbitApiException exception = new UpbitApiException(
                401,
                "{\"error\":{\"name\":\"no_authorization_ip\",\"message\":\"ip not allowed\"}}"
        );

        String status = UpbitAuthNetworkStatusResolver.fromError(exception);

        assertThat(status).isEqualTo(UpbitAuthNetworkStatusResolver.IP_NOT_WHITELISTED);
    }

    @Test
    void fromError_returnsUnknownForNonIpAuthorizationErrors() {
        UpbitApiException exception = new UpbitApiException(
                401,
                "{\"error\":{\"name\":\"invalid_query_payload\"}}"
        );

        String status = UpbitAuthNetworkStatusResolver.fromError(exception);

        assertThat(status).isEqualTo(UpbitAuthNetworkStatusResolver.UNKNOWN);
    }
}
