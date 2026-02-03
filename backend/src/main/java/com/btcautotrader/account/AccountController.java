package com.btcautotrader.account;

import com.btcautotrader.upbit.UpbitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UpbitService upbitService;

    public AccountController(UpbitService upbitService) {
        this.upbitService = upbitService;
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance() {
        List<Map<String, Object>> accounts = upbitService.fetchAccounts();

        Map<String, Object> response = new HashMap<>();
        response.put("queriedAt", OffsetDateTime.now().toString());
        response.put("accounts", accounts);

        return ResponseEntity.ok(response);
    }
}
