package com.btcautotrader.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

@Component
public class OrderStatusConstraintMigrationService {
    private static final Logger log = LoggerFactory.getLogger(OrderStatusConstraintMigrationService.class);
    private static final String TARGET_CONSTRAINT = "orders_status_check";
    private static final String EXPECTED_CHECK_SQL = """
            alter table orders
            add constraint orders_status_check
            check (status in ('REQUESTED', 'SUBMITTED', 'PENDING', 'FAILED', 'FILLED', 'CANCELED'))
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public OrderStatusConstraintMigrationService(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            @Value("${db.migration.orders-status-check.enabled:true}") boolean enabled
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateOrdersStatusConstraint() {
        if (!enabled || !isPostgres() || !ordersTableExists()) {
            return;
        }

        String constraintDef = currentConstraintDefinition();
        if (constraintDef != null && supportsFilledAndCanceled(constraintDef)) {
            return;
        }

        if (constraintDef != null) {
            jdbcTemplate.execute("alter table orders drop constraint if exists " + TARGET_CONSTRAINT);
        }
        jdbcTemplate.execute(EXPECTED_CHECK_SQL);
        log.warn("Updated {} to include FILLED/CANCELED statuses", TARGET_CONSTRAINT);
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String product = metadata == null ? null : metadata.getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            log.warn("Failed to detect database product for status-constraint migration: {}", ex.getMessage());
            return false;
        }
    }

    private boolean ordersTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name = 'orders'
                """,
                Integer.class
        );
        return count != null && count > 0;
    }

    private String currentConstraintDefinition() {
        List<String> defs = jdbcTemplate.query(
                """
                select pg_get_constraintdef(c.oid)
                from pg_constraint c
                join pg_class t on c.conrelid = t.oid
                join pg_namespace n on t.relnamespace = n.oid
                where n.nspname = current_schema()
                  and t.relname = 'orders'
                  and c.conname = ?
                  and c.contype = 'c'
                """,
                (rs, rowNum) -> rs.getString(1),
                TARGET_CONSTRAINT
        );
        if (defs.isEmpty()) {
            return null;
        }
        return defs.get(0);
    }

    private static boolean supportsFilledAndCanceled(String definition) {
        if (definition == null) {
            return false;
        }
        String upper = definition.toUpperCase(Locale.ROOT);
        return upper.contains("'FILLED'") && upper.contains("'CANCELED'");
    }
}
