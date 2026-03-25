package com.btcautotrader.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAccessService adminAccessService;
    private final AdminUserService adminUserService;

    public AdminController(
            AdminAccessService adminAccessService,
            AdminUserService adminUserService
    ) {
        this.adminAccessService = adminAccessService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserItemResponse>> listUsers(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "status", required = false) String status
    ) {
        adminAccessService.requireOwner(authentication);
        return ResponseEntity.ok(adminUserService.listUsers(query, status));
    }

    @GetMapping("/users/page")
    public ResponseEntity<AdminUserPageResponse> listUsersPage(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        adminAccessService.requireOwner(authentication);
        return ResponseEntity.ok(adminUserService.listUsersPage(query, status, page, size));
    }

    @PatchMapping("/users/{userId}/approval")
    public ResponseEntity<?> updateApproval(
            Authentication authentication,
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) AdminApprovalUpdateRequest request
    ) {
        adminAccessService.requireOwner(authentication);
        try {
            return ResponseEntity.ok(adminUserService.updateApproval(userId, request));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            Authentication authentication,
            @PathVariable("userId") Long userId
    ) {
        adminAccessService.requireOwner(authentication);
        try {
            return ResponseEntity.ok(adminUserService.deleteUser(userId));
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
