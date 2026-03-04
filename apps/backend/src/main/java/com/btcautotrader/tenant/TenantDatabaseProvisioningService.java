package com.btcautotrader.tenant;

import com.btcautotrader.auth.UserEntity;
import com.btcautotrader.auth.UserRepository;
import com.btcautotrader.auth.TradingApprovalStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TenantDatabaseProvisioningService {
    private static final Logger log = LoggerFactory.getLogger(TenantDatabaseProvisioningService.class);
    private static final long ENGINE_STATE_ID = 1L;

    private final UserRepository userRepository;
    private final TenantDataSourceProvider tenantDataSourceProvider;
    private final Resource tenantSchemaScript = new ClassPathResource("db/tenant-schema.sql");
    private final String ownerEmail;

    public TenantDatabaseProvisioningService(
            UserRepository userRepository,
            TenantDataSourceProvider tenantDataSourceProvider,
            @Value("${app.multi-tenant.owner-email:juno980220@gmail.com}") String ownerEmail
    ) {
        this.userRepository = userRepository;
        this.tenantDataSourceProvider = tenantDataSourceProvider;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserEntity ensureTenant(UserEntity user) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }

        String existingTenant = trimToNull(user.getTenantDatabase());
        String systemTenant = tenantDataSourceProvider.getSystemDatabaseName();
        boolean owner = isOwner(user);
        TradingApprovalStatus approvalStatus = TradingApprovalStatus.from(user.getTradingApprovalStatus());
        boolean approved = approvalStatus == TradingApprovalStatus.APPROVED;
        String resolvedTenant = existingTenant;
        String dedicatedTenant = null;

        if (owner) {
            resolvedTenant = systemTenant;
        } else {
            // 승인 전 사용자(PENDING/SUSPENDED)는 시스템 DB를 사용하고,
            // 승인 시점에만 전용 DB를 할당/생성한다.
            if (approved) {
                if (user.getId() == null) {
                    throw new IllegalStateException("user id is required to provision tenant database");
                }
                dedicatedTenant = "btc_user_" + user.getId();
                if (existingTenant == null || systemTenant.equals(existingTenant)) {
                    resolvedTenant = dedicatedTenant;
                }
            } else {
                resolvedTenant = systemTenant;
            }
        }

        boolean shouldProvisionDedicatedTenant = dedicatedTenant != null && dedicatedTenant.equals(resolvedTenant);
        if ((existingTenant == null && resolvedTenant == null)
                || (existingTenant != null && existingTenant.equals(resolvedTenant))) {
            if (shouldProvisionDedicatedTenant) {
                boolean provisioned = provisionDedicatedTenantBestEffort(user, dedicatedTenant);
                if (!provisioned && !systemTenant.equals(resolvedTenant)) {
                    user.setTenantDatabase(systemTenant);
                    return userRepository.save(user);
                }
            }
            return user;
        }

        if (!owner && existingTenant != null && systemTenant.equals(existingTenant)) {
            log.warn(
                    "Rebinding non-owner user {} ({}) from system tenant {} to {}",
                    user.getId(),
                    user.getEmail(),
                    existingTenant,
                    resolvedTenant
            );
        } else if (owner && existingTenant != null && !systemTenant.equals(existingTenant)) {
            log.warn(
                    "Rebinding owner user {} ({}) from tenant {} to system tenant {}",
                    user.getId(),
                    user.getEmail(),
                    existingTenant,
                    systemTenant
            );
        }

        user.setTenantDatabase(resolvedTenant);
        UserEntity saved = userRepository.save(user);
        if (shouldProvisionDedicatedTenant) {
            boolean provisioned = provisionDedicatedTenantBestEffort(saved, dedicatedTenant);
            if (!provisioned && !systemTenant.equals(resolvedTenant)) {
                saved.setTenantDatabase(systemTenant);
                saved = userRepository.save(saved);
            }
        }
        return saved;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void normalizeTenantBindingsOnStartup() {
        for (UserEntity user : userRepository.findAll()) {
            try {
                ensureTenant(user);
            } catch (RuntimeException ex) {
                log.warn(
                        "Tenant binding normalization skipped for user {} ({}): {}",
                        user.getId(),
                        user.getEmail(),
                        ex.getMessage()
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public List<String> listKnownTenantDatabases() {
        Set<String> databases = new LinkedHashSet<>();
        databases.add(tenantDataSourceProvider.getSystemDatabaseName());

        List<String> assigned = TenantContext.callWithTenantDatabase(null, () -> userRepository.findAll()
                .stream()
                .map(UserEntity::getTenantDatabase)
                .map(TenantDatabaseProvisioningService::trimToNull)
                .filter(name -> name != null)
                .toList());
        if (assigned != null && !assigned.isEmpty()) {
            databases.addAll(assigned);
        }
        return List.copyOf(databases);
    }

    @Transactional(readOnly = true)
    public boolean isSystemTenantDatabase(String tenantDatabase) {
        String resolved = trimToNull(tenantDatabase);
        if (resolved == null) {
            return true;
        }
        return tenantDataSourceProvider.getSystemDatabaseName().equals(resolved);
    }

    public boolean dropDedicatedTenantDatabase(String tenantDatabase) {
        String resolved = trimToNull(tenantDatabase);
        if (resolved == null || isSystemTenantDatabase(resolved)) {
            return false;
        }
        if (!resolved.startsWith("btc_user_")) {
            throw new IllegalArgumentException("refusing to drop non-dedicated tenant database: " + resolved);
        }

        tenantDataSourceProvider.closeTenantDataSource(resolved);
        String adminUrl = tenantDataSourceProvider.buildAdminJdbcUrl();

        try (Connection connection = java.sql.DriverManager.getConnection(
                adminUrl,
                tenantDataSourceProvider.getUsername(),
                tenantDataSourceProvider.getPassword()
        )) {
            if (!databaseExists(connection, resolved)) {
                return false;
            }

            terminateActiveConnections(connection, resolved);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS \"" + resolved + "\"");
            }
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to drop tenant database: " + resolved, ex);
        }
    }

    private boolean isOwner(UserEntity user) {
        String email = user.getEmail();
        if (email == null || email.isBlank() || ownerEmail.isBlank()) {
            return false;
        }
        return ownerEmail.equals(email.trim().toLowerCase(Locale.ROOT));
    }

    private boolean provisionDedicatedTenantBestEffort(UserEntity user, String tenantDatabase) {
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return false;
        }
        try {
            createDatabaseIfNeeded(tenantDatabase);
            initializeDatabaseIfNeeded(tenantDatabase);
            seedEngineStateIfNeeded(tenantDatabase);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "Tenant database provisioning skipped for user {} ({}) tenant {}: {}",
                    user == null ? null : user.getId(),
                    user == null ? null : user.getEmail(),
                    tenantDatabase,
                    ex.getMessage()
            );
            return false;
        }
    }

    private void createDatabaseIfNeeded(String databaseName) {
        String adminUrl = tenantDataSourceProvider.buildAdminJdbcUrl();
        try (Connection connection = java.sql.DriverManager.getConnection(
                adminUrl,
                tenantDataSourceProvider.getUsername(),
                tenantDataSourceProvider.getPassword()
        )) {
            if (databaseExists(connection, databaseName)) {
                return;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE \"" + databaseName + "\"");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to create tenant database: " + databaseName, ex);
        }
    }

    private static void terminateActiveConnections(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select pg_terminate_backend(pid) from pg_stat_activity where datname = ? and pid <> pg_backend_pid()"
        )) {
            statement.setString(1, databaseName);
            statement.execute();
        }
    }

    private static boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from pg_database where datname = ?"
        )) {
            statement.setString(1, databaseName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void initializeDatabaseIfNeeded(String databaseName) {
        String jdbcUrl = tenantDataSourceProvider.buildJdbcUrl(databaseName);
        try (Connection connection = java.sql.DriverManager.getConnection(
                jdbcUrl,
                tenantDataSourceProvider.getUsername(),
                tenantDataSourceProvider.getPassword()
        )) {
            if (tableExists(connection, "orders")) {
                return;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to inspect tenant database schema: " + databaseName, ex);
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(tenantDataSourceProvider.getUsername());
        dataSource.setPassword(tenantDataSourceProvider.getPassword());

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(true);
        populator.addScript(tenantSchemaScript);
        populator.execute(dataSource);
    }

    @Transactional(readOnly = true)
    public OffsetDateTime resolveTenantProvisionedAt(String tenantDatabase) {
        String resolved = trimToNull(tenantDatabase);
        if (resolved == null) {
            return null;
        }
        try {
            String jdbcUrl = tenantDataSourceProvider.buildJdbcUrl(resolved);
            try (Connection connection = java.sql.DriverManager.getConnection(
                    jdbcUrl,
                    tenantDataSourceProvider.getUsername(),
                    tenantDataSourceProvider.getPassword()
            );
                 PreparedStatement statement = connection.prepareStatement(
                         "select updated_at from engine_state where id = ?"
                 )) {
                statement.setLong(1, ENGINE_STATE_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return null;
                    }
                    return resultSet.getObject(1, OffsetDateTime.class);
                }
            }
        } catch (SQLException | IllegalArgumentException ex) {
            log.warn("Failed to resolve tenant provisioning timestamp for {}: {}", resolved, ex.getMessage());
            return null;
        }
    }

    private void seedEngineStateIfNeeded(String databaseName) {
        String jdbcUrl = tenantDataSourceProvider.buildJdbcUrl(databaseName);
        try (Connection connection = java.sql.DriverManager.getConnection(
                jdbcUrl,
                tenantDataSourceProvider.getUsername(),
                tenantDataSourceProvider.getPassword()
        );
             PreparedStatement statement = connection.prepareStatement(
                     "insert into engine_state (id, running, updated_at) values (?, ?, now()) on conflict (id) do nothing"
             )) {
            statement.setLong(1, ENGINE_STATE_ID);
            statement.setBoolean(2, false);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to seed engine_state for tenant database: " + databaseName, ex);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = current_schema() and table_name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
