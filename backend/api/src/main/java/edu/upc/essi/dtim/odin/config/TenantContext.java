package edu.upc.essi.dtim.odin.config;

public class TenantContext {

    public static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000000";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static String getCurrentTenant() {
        String t = CURRENT.get();
        return t != null ? t : DEFAULT_TENANT;
    }

    public static void setCurrentTenant(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
