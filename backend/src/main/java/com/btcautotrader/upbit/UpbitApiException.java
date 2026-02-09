package com.btcautotrader.upbit;

public class UpbitApiException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public UpbitApiException(int statusCode, String responseBody) {
        super("Upbit API error (" + statusCode + ")");
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
