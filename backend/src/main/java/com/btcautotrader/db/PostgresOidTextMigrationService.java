package com.btcautotrader.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PostgresOidTextMigrationService {
    private static final Logger log = LoggerFactory.getLogger(PostgresOidTextMigrationService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z_][a-z0-9_]*$");
    private static final List<TableColumn> TARGET_COLUMNS = List.of(
            new TableColumn("trade_decisions", "details"),
            new TableColumn("orders", "raw_request"),
            new TableColumn("orders", "raw_response"),
            new TableColumn("orders", "error_message")
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public PostgresOidTextMigrationService(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            @Value("${db.migration.oid-to-text.enabled:true}") boolean enabled
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateOidColumnsToText() {
        if (!enabled) {
            return;
        }
        if (!isPostgres()) {
            return;
        }

        List<TableColumn> oidColumns = findOidColumns();
        List<TableColumn> textColumns = findTextColumns();
        if (oidColumns.isEmpty() && textColumns.isEmpty()) {
            return;
        }

        if (!oidColumns.isEmpty()) {
            log.warn("Detected PostgreSQL OID-backed text columns: {}. Migrating to TEXT.", oidColumns);
        }
        createSafeLoTextFunction();
        try {
            for (TableColumn target : oidColumns) {
                migrateColumn(target);
            }
            for (TableColumn target : textColumns) {
                repairNumericOidLiterals(target);
            }
        } finally {
            dropSafeLoTextFunction();
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String product = metadata == null ? null : metadata.getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            log.warn("Failed to detect database product; skipping OID->TEXT migration: {}", ex.getMessage());
            return false;
        }
    }

    private List<TableColumn> findOidColumns() {
        List<TableColumn> result = new ArrayList<>();
        for (TableColumn target : TARGET_COLUMNS) {
            if (isOidColumn(target)) {
                result.add(target);
            }
        }
        return result;
    }

    private List<TableColumn> findTextColumns() {
        List<TableColumn> result = new ArrayList<>();
        for (TableColumn target : TARGET_COLUMNS) {
            if (isTextColumn(target)) {
                result.add(target);
            }
        }
        return result;
    }

    private boolean isOidColumn(TableColumn target) {
        String udt = lookupUdtName(target);
        return udt != null && "oid".equalsIgnoreCase(udt);
    }

    private boolean isTextColumn(TableColumn target) {
        String udt = lookupUdtName(target);
        return udt != null && "text".equalsIgnoreCase(udt);
    }

    private String lookupUdtName(TableColumn target) {
        String sql = """
                select udt_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = ?
                  and column_name = ?
                """;
        List<String> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getString("udt_name"),
                target.table(),
                target.column()
        );
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private void migrateColumn(TableColumn target) {
        String table = quotedIdentifier(target.table());
        String column = quotedIdentifier(target.column());
        String tempColumn = quotedIdentifier(target.column() + "_txt_tmp");

        jdbcTemplate.execute("alter table " + table + " add column if not exists " + tempColumn + " text");
        jdbcTemplate.execute(
                "update " + table
                        + " set " + tempColumn + " = public.__safe_lo_text(" + column + ")"
                        + " where " + tempColumn + " is null and " + column + " is not null"
        );
        jdbcTemplate.execute("alter table " + table + " drop column " + column);
        jdbcTemplate.execute("alter table " + table + " rename column " + tempColumn + " to " + quotedIdentifier(target.column()));

        log.warn("Migrated {}.{} from OID to TEXT", target.table(), target.column());
    }

    private void repairNumericOidLiterals(TableColumn target) {
        String table = quotedIdentifier(target.table());
        String column = quotedIdentifier(target.column());
        int repaired = jdbcTemplate.update(
                "update " + table
                        + " set " + column + " = public.__safe_lo_text((" + column + ")::oid)"
                        + " where " + column + " ~ '^[0-9]{5,10}$'"
                        + " and public.__safe_lo_text((" + column + ")::oid) is not null"
        );
        if (repaired > 0) {
            log.warn("Repaired {} numeric OID literal values in {}.{}", repaired, target.table(), target.column());
        }
    }

    private void createSafeLoTextFunction() {
        jdbcTemplate.execute("""
                create or replace function public.__safe_lo_text(p_oid oid)
                returns text
                language plpgsql
                as $$
                begin
                  if p_oid is null then
                    return null;
                  end if;
                  return convert_from(lo_get(p_oid), 'UTF8');
                exception
                  when others then
                    return null;
                end;
                $$;
                """);
    }

    private void dropSafeLoTextFunction() {
        jdbcTemplate.execute("drop function if exists public.__safe_lo_text(oid)");
    }

    private static String quotedIdentifier(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "\"" + normalized + "\"";
    }

    private record TableColumn(String table, String column) {
    }
}
