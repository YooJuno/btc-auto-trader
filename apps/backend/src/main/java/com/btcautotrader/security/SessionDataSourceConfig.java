package com.btcautotrader.security;

import com.btcautotrader.tenant.TenantDataSourceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;

import javax.sql.DataSource;

@Configuration
public class SessionDataSourceConfig {
    @Bean
    @SpringSessionDataSource
    public DataSource springSessionDataSource(TenantDataSourceProvider tenantDataSourceProvider) {
        return tenantDataSourceProvider.getSystemDataSource();
    }
}
