package com.btcautotrader.tenant;

public final class TenantContext {
    private static final ThreadLocal<String> TENANT_DATABASE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantDatabase(String database) {
        TENANT_DATABASE.set(database);
    }

    public static String getTenantDatabase() {
        return TENANT_DATABASE.get();
    }

    public static void clear() {
        TENANT_DATABASE.remove();
    }
}
