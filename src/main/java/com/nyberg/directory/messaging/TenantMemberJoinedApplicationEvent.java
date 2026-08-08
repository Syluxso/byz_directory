package com.nyberg.directory.messaging;

import org.springframework.context.ApplicationEvent;

public class TenantMemberJoinedApplicationEvent extends ApplicationEvent {

    private final TenantLifecycleEvent payload;

    public TenantMemberJoinedApplicationEvent(Object source, TenantLifecycleEvent payload) {
        super(source);
        this.payload = payload;
    }

    public TenantLifecycleEvent getPayload() {
        return payload;
    }
}
