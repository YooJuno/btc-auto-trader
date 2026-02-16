package com.btcautotrader.tenant;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class TenantAwareDataSource extends AbstractDataSource {
    private final TenantDataSourceProvider tenantDataSourceProvider;

    public TenantAwareDataSource(TenantDataSourceProvider tenantDataSourceProvider) {
        this.tenantDataSourceProvider = tenantDataSourceProvider;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return currentDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return currentDataSource().getConnection(username, password);
    }

    private DataSource currentDataSource() {
        return tenantDataSourceProvider.getCurrentDataSource();
    }
}
