package com.nyberg.directory.messaging;

import org.springframework.context.ApplicationEvent;

public class TenantCreatedApplicationEvent extends ApplicationEvent {

    private final TenantLifecycleEvent payload;

    public TenantCreatedApplicationEvent(Object source, TenantLifecycleEvent payload) {
        super(source);
        this.payload = payload;
    }

    public TenantLifecycleEvent getPayload() {
        return payload;
    }
}
