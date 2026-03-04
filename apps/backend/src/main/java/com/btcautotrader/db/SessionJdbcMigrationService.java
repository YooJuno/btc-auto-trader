package com.btcautotrader.db;

import com.btcautotrader.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SessionJdbcMigrationService {
    private static final Logger log = LoggerFactory.getLogger(SessionJdbcMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    public SessionJdbcMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        try {
            TenantContext.runWithTenantDatabase(null, this::createSpringSessionSchemaIfMissing);
        } catch (RuntimeException ex) {
            log.warn("Spring Session JDBC schema migration skipped: {}", ex.getMessage());
        }
    }

    private void createSpringSessionSchemaIfMissing() {
        jdbcTemplate.execute("""
                create table if not exists spring_session (
                  primary_id char(36) not null,
                  session_id char(36) not null,
                  creation_time bigint not null,
                  last_access_time bigint not null,
                  max_inactive_interval integer not null,
                  expiry_time bigint not null,
                  principal_name varchar(100),
                  constraint spring_session_pk primary key (primary_id)
                )
                """);
        jdbcTemplate.execute("""
                create unique index if not exists spring_session_ix1
                on spring_session (session_id)
                """);
        jdbcTemplate.execute("""
                create index if not exists spring_session_ix2
                on spring_session (expiry_time)
                """);
        jdbcTemplate.execute("""
                create index if not exists spring_session_ix3
                on spring_session (principal_name)
                """);
        jdbcTemplate.execute("""
                create table if not exists spring_session_attributes (
                  session_primary_id char(36) not null,
                  attribute_name varchar(200) not null,
                  attribute_bytes bytea not null,
                  constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
                  constraint spring_session_attributes_fk
                    foreign key (session_primary_id)
                    references spring_session(primary_id)
                    on delete cascade
                )
                """);
    }
}
