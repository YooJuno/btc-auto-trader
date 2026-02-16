package com.btcautotrader.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class TenantDataSourceProvider {
    private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("^[a-zA-Z0-9_\\-]{1,63}$");

    private final JdbcUrlParts jdbcUrlParts;
    private final String username;
    private final String password;
    private final HikariDataSource systemDataSource;
    private final Map<String, HikariDataSource> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceProvider(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password
    ) {
        this.jdbcUrlParts = JdbcUrlParts.parse(jdbcUrl);
        this.username = username;
        this.password = password;
        this.systemDataSource = createDataSource(jdbcUrlParts.database(), buildJdbcUrl(jdbcUrlParts.database()), 10);
    }

    public DataSource getCurrentDataSource() {
        String tenantDatabase = TenantContext.getTenantDatabase();
        if (tenantDatabase == null || tenantDatabase.isBlank() || tenantDatabase.equals(jdbcUrlParts.database())) {
            return systemDataSource;
        }
        return tenantDataSources.computeIfAbsent(tenantDatabase, this::createTenantDataSource);
    }

    public DataSource getSystemDataSource() {
        return systemDataSource;
    }

    public String getSystemDatabaseName() {
        return jdbcUrlParts.database();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String buildJdbcUrl(String databaseName) {
        validateDatabaseName(databaseName);
        return jdbcUrlParts.prefix() + databaseName + jdbcUrlParts.query();
    }

    public String buildAdminJdbcUrl() {
        return buildJdbcUrl("postgres");
    }

    private HikariDataSource createTenantDataSource(String databaseName) {
        validateDatabaseName(databaseName);
        return createDataSource(databaseName, buildJdbcUrl(databaseName), 5);
    }

    private HikariDataSource createDataSource(String databaseName, String jdbcUrl, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("tenant-" + databaseName);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    private static void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank() || !SAFE_DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Unsafe database name: " + databaseName);
        }
    }

    @PreDestroy
    void close() {
        tenantDataSources.values().forEach(HikariDataSource::close);
        systemDataSource.close();
    }

    private record JdbcUrlParts(String prefix, String database, String query) {
        private static JdbcUrlParts parse(String jdbcUrl) {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("spring.datasource.url is required");
            }

            String prefix = "jdbc:postgresql://";
            if (!jdbcUrl.startsWith(prefix)) {
                throw new IllegalArgumentException("Only PostgreSQL JDBC URL is supported: " + jdbcUrl);
            }

            String hostAndPath = jdbcUrl.substring(prefix.length());
            int firstSlash = hostAndPath.indexOf('/');
            if (firstSlash < 0) {
                throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL: " + jdbcUrl);
            }

            String hostPort = hostAndPath.substring(0, firstSlash);
            String pathAndQuery = hostAndPath.substring(firstSlash + 1);
            int queryIndex = pathAndQuery.indexOf('?');

            String database = queryIndex >= 0
                    ? pathAndQuery.substring(0, queryIndex)
                    : pathAndQuery;
            if (database.isBlank()) {
                throw new IllegalArgumentException("Database name is missing in JDBC URL: " + jdbcUrl);
            }

            String query = queryIndex >= 0 ? pathAndQuery.substring(queryIndex) : "";
            return new JdbcUrlParts(prefix + hostPort + "/", database, query);
        }
    }
}
