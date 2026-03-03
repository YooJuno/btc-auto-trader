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
                alter table if exists strategy_market_overrides
                add column if not exists trade_paused boolean
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
                alter table if exists strategy_config
                add column if not exists signal_model varchar(10)
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                add column if not exists entry_score_threshold double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                add column if not exists exit_score_threshold double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                add column if not exists risk_per_trade_pct double precision
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                add column if not exists time_stop_candles integer
                """);
        jdbcTemplate.execute("""
                update strategy_config
                set signal_model = coalesce(nullif(signal_model, ''), 'V1')
                """);
        jdbcTemplate.execute("""
                update strategy_config
                set entry_score_threshold = coalesce(entry_score_threshold, 65)
                """);
        jdbcTemplate.execute("""
                update strategy_config
                set exit_score_threshold = coalesce(exit_score_threshold, 60)
                """);
        jdbcTemplate.execute("""
                update strategy_config
                set risk_per_trade_pct = coalesce(risk_per_trade_pct, 0.7)
                """);
        jdbcTemplate.execute("""
                update strategy_config
                set time_stop_candles = coalesce(time_stop_candles, 180)
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                alter column signal_model set default 'V1'
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                alter column entry_score_threshold set default 65
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                alter column exit_score_threshold set default 60
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                alter column risk_per_trade_pct set default 0.7
                """);
        jdbcTemplate.execute("""
                alter table if exists strategy_config
                alter column time_stop_candles set default 180
                """);
    }
}
