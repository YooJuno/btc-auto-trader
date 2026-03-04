package com.btcautotrader.upbit;

import java.util.Locale;

public final class UpbitAuthNetworkStatusResolver {
    public static final String OK = "OK";
    public static final String IP_NOT_WHITELISTED = "IP_NOT_WHITELISTED";
    public static final String UNKNOWN = "UNKNOWN";

    private static final String UPBIT_IP_BLOCK_CODE = "no_authorization_ip";

    private UpbitAuthNetworkStatusResolver() {
    }

    public static String fromError(Throwable throwable) {
        if (isIpNotWhitelisted(throwable)) {
            return IP_NOT_WHITELISTED;
        }
        return UNKNOWN;
    }

    public static boolean isIpNotWhitelisted(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (containsIpBlockCode(cursor.getMessage())) {
                return true;
            }
            if (cursor instanceof UpbitApiException upbitApiException
                    && containsIpBlockCode(upbitApiException.getResponseBody())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean containsIpBlockCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(UPBIT_IP_BLOCK_CODE);
    }
}
