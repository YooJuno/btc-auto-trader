package com.btcautotrader.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserApprovalOnboardingMigrationService {
    private static final Logger log = LoggerFactory.getLogger(UserApprovalOnboardingMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    public UserApprovalOnboardingMigrationService(JdbcTemplate jdbcTemplate) {
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
            jdbcTemplate.execute("""
                    create table if not exists user_onboarding_state (
                      user_id bigint primary key references app_users(id) on delete cascade,
                      profile_completed boolean not null default false,
                      credentials_completed boolean not null default false,
                      strategy_completed boolean not null default false,
                      completed_at timestamptz,
                      updated_at timestamptz not null default now()
                    )
                    """);
        } catch (RuntimeException ex) {
            log.warn("User approval/onboarding migration skipped: {}", ex.getMessage());
        }
    }
}
