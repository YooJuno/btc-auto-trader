package com.btcautotrader;

import com.btcautotrader.upbit.UpbitCredentialMissingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UpbitCredentialMissingException.class)
    public ResponseEntity<Map<String, Object>> handleMissingCredential(UpbitCredentialMissingException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage() == null ? "거래소 API 키가 필요합니다." : ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
