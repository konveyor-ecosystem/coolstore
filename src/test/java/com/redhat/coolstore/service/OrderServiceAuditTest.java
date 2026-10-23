package com.redhat.coolstore.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Order;

/** Audit logging must fail gracefully when no TCP collector is listening (no live server needed). */
class OrderServiceAuditTest {
    @Test
    void auditWithoutCollectorDoesNotThrow() {
        OrderService svc = new OrderService();
        svc.streamHost = "127.0.0.1";
        svc.streamPort = 1; // nothing listens here
        svc.streamProtocol = "TCP";
        assertDoesNotThrow(svc::init);
        Order o = new Order();
        o.setCustomerName("test");
        assertDoesNotThrow(() -> svc.audit(o));
        assertDoesNotThrow(svc::cleanup);
    }

    @Test
    void auditWithoutLoggerIsNoop() {
        assertDoesNotThrow(() -> new OrderService().audit(new Order()));
    }
}
