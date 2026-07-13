package com.nyberg.directory.tenant;

import java.util.UUID;

public final class OrganizationContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private OrganizationContext() {}

    public static void set(UUID organizationId) { CURRENT.set(organizationId); }
    public static UUID get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
