package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.AuditLogger;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.service.OrderService;
import jakarta.persistence.EntityManager;

/** audit-logging-library 2.0.0: AuditEvent record + non-blocking logEventAsync on save. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock EntityManager em;
    @Mock AuditLogger auditLogger;
    @InjectMocks OrderService service;

    @Test
    void savePersistsAndEmitsAuditEventAsync() throws Exception {
        when(auditLogger.logEventAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        Order order = new Order();
        order.setOrderId(42L);

        service.save(order);

        verify(em).persist(order);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).logEventAsync(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditResult.SUCCESS, event.result());
        assertEquals("coolstore", event.application());
        assertEquals("OrderService", event.component());
        assertTrue(event.message().contains("42"));
        verify(auditLogger, never()).logEvent(any()); // sync logEvent must NOT be used
    }
}
