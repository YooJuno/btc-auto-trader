package com.btcautotrader.tenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class TenantDataSourceConfig {
    @Bean
    @Primary
    public DataSource dataSource(TenantDataSourceProvider tenantDataSourceProvider) {
        return new TenantAwareDataSource(tenantDataSourceProvider);
    }
}
