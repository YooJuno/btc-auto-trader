package com.btcautotrader;

import com.btcautotrader.upbit.UpbitCredentialMissingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UpbitCredentialMissingException.class)
    public ResponseEntity<Map<String, Object>> handleMissingCredential(UpbitCredentialMissingException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage() == null ? "거래소 API 키가 필요합니다." : ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getReason() == null ? "요청 처리 실패" : ex.getReason());
        error.put("status", status.value());
        error.put("path", request == null ? null : request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled API exception on {}", request == null ? "-" : request.getRequestURI(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("details", ex.getMessage() == null ? "unexpected error" : ex.getMessage());
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("path", request == null ? null : request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
