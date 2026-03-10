package com.btcautotrader.db;

import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceSeedSnapshotMigrationServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    @Test
    void migrateCurrentTenantSchema_removesDuplicatesThenCreatesUniqueIndex() {
        PerformanceSeedSnapshotMigrationService service = new PerformanceSeedSnapshotMigrationService(
                jdbcTemplate,
                tenantDatabaseProvisioningService,
                true
        );

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 2);

        ReflectionTestUtils.invokeMethod(service, "migrateCurrentTenantSchema");

        verify(jdbcTemplate).execute(contains("create unique index if not exists ux_portfolio_snapshot_performance_seed"));
    }

    @Test
    void migrateCurrentTenantSchema_skipsWhenPortfolioSnapshotTableIsMissing() {
        PerformanceSeedSnapshotMigrationService service = new PerformanceSeedSnapshotMigrationService(
                jdbcTemplate,
                tenantDatabaseProvisioningService,
                true
        );

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        ReflectionTestUtils.invokeMethod(service, "migrateCurrentTenantSchema");

        verify(jdbcTemplate, never()).execute(contains("ux_portfolio_snapshot_performance_seed"));
    }
}
