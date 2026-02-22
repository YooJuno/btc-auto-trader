package com.btcautotrader.tenant;

import com.btcautotrader.auth.UserEntity;
import com.btcautotrader.auth.UserRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TenantDatabaseProvisioningService {
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
        if (existingTenant != null) {
            return user;
        }

        String resolvedTenant;
        if (isOwner(user)) {
            resolvedTenant = tenantDataSourceProvider.getSystemDatabaseName();
        } else {
            if (user.getId() == null) {
                throw new IllegalStateException("user id is required to provision tenant database");
            }
            resolvedTenant = "btc_user_" + user.getId();
            createDatabaseIfNeeded(resolvedTenant);
            initializeDatabaseIfNeeded(resolvedTenant);
        }

        user.setTenantDatabase(resolvedTenant);
        return userRepository.save(user);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bindOwnerTenantOnStartup() {
        if (ownerEmail.isBlank()) {
            return;
        }
        userRepository.findFirstByEmailIgnoreCase(ownerEmail).ifPresent(this::ensureTenant);
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

    private boolean isOwner(UserEntity user) {
        String email = user.getEmail();
        if (email == null || email.isBlank() || ownerEmail.isBlank()) {
            return false;
        }
        return ownerEmail.equals(email.trim().toLowerCase(Locale.ROOT));
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
