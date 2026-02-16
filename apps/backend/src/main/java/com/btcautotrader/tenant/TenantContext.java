package com.btcautotrader.tenant;

import java.util.function.Supplier;

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

    public static void runWithTenantDatabase(String database, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        String previous = TENANT_DATABASE.get();
        setOrClear(database);
        try {
            runnable.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callWithTenantDatabase(String database, Supplier<T> supplier) {
        if (supplier == null) {
            return null;
        }
        String previous = TENANT_DATABASE.get();
        setOrClear(database);
        try {
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    private static void setOrClear(String database) {
        if (database == null || database.isBlank()) {
            clear();
            return;
        }
        setTenantDatabase(database.trim());
    }

    private static void restore(String previous) {
        if (previous == null || previous.isBlank()) {
            clear();
            return;
        }
        setTenantDatabase(previous);
    }
}
