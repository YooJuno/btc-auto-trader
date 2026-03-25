package com.btcautotrader.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserApprovalMigrationService {
    private static final Logger log = LoggerFactory.getLogger(UserApprovalMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    public UserApprovalMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            jdbcTemplate.execute("""
                    alter table app_users
                    add column if not exists trading_approval_status varchar(20)
                    """);
            jdbcTemplate.execute("""
                    alter table app_users
                    add column if not exists trading_approval_note varchar(500)
                    """);
            jdbcTemplate.execute("""
                    alter table app_users
                    add column if not exists trading_approval_updated_at timestamptz
                    """);
            jdbcTemplate.execute("""
                    update app_users
                    set trading_approval_status = coalesce(nullif(trading_approval_status, ''), 'PENDING')
                    """);
            jdbcTemplate.execute("""
                    update app_users
                    set trading_approval_updated_at = coalesce(trading_approval_updated_at, now())
                    """);
            jdbcTemplate.execute("""
                    alter table app_users
                    alter column trading_approval_status set default 'PENDING'
                    """);
        } catch (RuntimeException ex) {
            log.warn("User approval migration skipped: {}", ex.getMessage());
        }
    }
}
