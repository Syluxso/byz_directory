package com.nyberg.directory.messaging;

import org.springframework.context.ApplicationEvent;

public class MembershipLifecycleApplicationEvent extends ApplicationEvent {

    private final MembershipLifecycleEvent payload;

    public MembershipLifecycleApplicationEvent(Object source, MembershipLifecycleEvent payload) {
        super(source);
        this.payload = payload;
    }

    public MembershipLifecycleEvent getPayload() {
        return payload;
    }
}
