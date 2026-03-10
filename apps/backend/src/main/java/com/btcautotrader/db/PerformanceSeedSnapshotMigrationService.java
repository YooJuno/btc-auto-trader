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
public class PerformanceSeedSnapshotMigrationService {
    private static final Logger log = LoggerFactory.getLogger(PerformanceSeedSnapshotMigrationService.class);
    private static final String UNIQUE_INDEX_NAME = "ux_portfolio_snapshot_performance_seed";

    private final JdbcTemplate jdbcTemplate;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    private final boolean enabled;

    public PerformanceSeedSnapshotMigrationService(
            JdbcTemplate jdbcTemplate,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService,
            @Value("${db.migration.performance-seed-unique.enabled:true}") boolean enabled
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
                log.warn("Performance seed snapshot migration skipped for {}: {}", tenantDatabase, ex.getMessage());
            }
        }
    }

    void migrateCurrentTenantSchema() {
        if (!portfolioSnapshotTableExists()) {
            return;
        }

        int removed = removeDuplicatePerformanceSeeds();
        if (removed > 0) {
            log.warn("Removed {} duplicate PERFORMANCE_SEED snapshots before enforcing {}", removed, UNIQUE_INDEX_NAME);
        }

        jdbcTemplate.execute("""
                create unique index if not exists ux_portfolio_snapshot_performance_seed
                on portfolio_snapshot(event_type, source, occurred_at)
                where event_type = 'PERFORMANCE_SEED' and source = 'SYSTEM'
                """);
    }

    private boolean portfolioSnapshotTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name = 'portfolio_snapshot'
                """,
                Integer.class
        );
        return count != null && count > 0;
    }

    private int removeDuplicatePerformanceSeeds() {
        Integer removed = jdbcTemplate.queryForObject(
                """
                with ranked as (
                    select id,
                           row_number() over (
                               partition by event_type, source, occurred_at
                               order by id desc
                           ) as rn
                    from portfolio_snapshot
                    where event_type = 'PERFORMANCE_SEED'
                      and source = 'SYSTEM'
                ),
                deleted as (
                    delete from portfolio_snapshot
                    where id in (
                        select id
                        from ranked
                        where rn > 1
                    )
                    returning id
                )
                select count(*)
                from deleted
                """,
                Integer.class
        );
        return removed == null ? 0 : removed;
    }
}
