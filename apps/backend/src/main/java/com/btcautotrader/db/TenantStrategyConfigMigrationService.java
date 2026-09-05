package com.btcautotrader.db;

import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TenantStrategyConfigMigrationService {
    private static final Logger log = LoggerFactory.getLogger(TenantStrategyConfigMigrationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    private final boolean enabled;

    public TenantStrategyConfigMigrationService(
            JdbcTemplate jdbcTemplate,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService,
            @Value("${db.migration.tenant-strategy-config.enabled:true}") boolean enabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        if (!enabled) {
            return;
        }

        List<String> tenantDatabases = tenantDatabaseProvisioningService.listKnownTenantDatabases();
        for (String tenantDatabase : tenantDatabases) {
            try {
                TenantContext.runWithTenantDatabase(tenantDatabase, this::migrateCurrentTenantSchema);
            } catch (RuntimeException ex) {
                log.warn("Tenant strategy_config migration skipped for {}: {}", tenantDatabase, ex.getMessage());
            }
        }
    }

    private void migrateCurrentTenantSchema() {
        jdbcTemplate.execute("""
                create table if not exists strategy_market_overrides (
                    market varchar(20) primary key,
                    max_order_krw double precision,
                    profile varchar(20),
                    trade_paused boolean default false,
                    take_profit_pct double precision,
                    stop_loss_pct double precision,
                    trailing_stop_pct double precision,
                    partial_take_profit_pct double precision,
                    stop_exit_pct double precision,
                    trend_exit_pct double precision,
                    momentum_exit_pct double precision,
                    updated_at timestamptz not null default now()
                )
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists trade_paused boolean
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists take_profit_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists stop_loss_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists trailing_stop_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists partial_take_profit_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists stop_exit_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists trend_exit_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists momentum_exit_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                add column if not exists risk_per_trade_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                add column if not exists signal_model varchar(40)
                """);
        jdbcTemplate.execute("""
                create table if not exists paper_accounts (
                    currency varchar(20) primary key,
                    balance numeric(38, 18) not null default 0,
                    locked numeric(38, 18) not null default 0,
                    avg_buy_price numeric(38, 18) not null default 0,
                    updated_at timestamptz not null default now()
                )
                """);
        jdbcTemplate.execute("""
                update strategy_market_overrides
                set trade_paused = coalesce(trade_paused, false)
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_market_overrides
                alter column trade_paused set default false
                """);
        jdbcTemplate.execute("""
                update strategy_config
                set risk_per_trade_pct = coalesce(risk_per_trade_pct, 0.7)
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                alter column risk_per_trade_pct set default 0.7
                """);
    }
}
