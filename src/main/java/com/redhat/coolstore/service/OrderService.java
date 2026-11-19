package com.redhat.coolstore.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.jboss.logging.Logger;

import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.AuditLogger;
import com.redhat.coolstore.model.Order;

/**
 * Order persistence service. Uses audit-logging-library 2.0.0:
 * {@code AuditEvent} record + {@code StreamableAuditLogger#logEventAsync} (TCP).
 */
@ApplicationScoped
public class OrderService {

  private static final Logger LOG = Logger.getLogger(OrderService.class);

  @Inject
  private EntityManager em;

  @Inject
  private AuditLogger auditLogger;

  public void save(Order order) {
    em.persist(order);
    auditEvent(order, AuditResult.SUCCESS, "Order persisted");
  }

  public List<Order> getOrders() {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Order> criteria = cb.createQuery(Order.class);
    Root<Order> member = criteria.from(Order.class);
    criteria.select(member);
    return em.createQuery(criteria).getResultList();
  }

  public Order getOrderById(long id) {
    return em.find(Order.class, id);
  }

  /**
   * Non-blocking async audit emission via the AuditLogger (StreamableAuditLogger over TCP).
   * The AuditEvent is instantiated directly as a Java record (no Builder).
   */
  private void auditEvent(Order order, AuditResult result, String message) {
    try {
      AuditEvent event = new AuditEvent(
          Instant.now(),          // timestamp
          "ORDER_CREATED",        // event_type
          null,                   // user_id
          null,                   // session_id
          "coolstore",            // application
          "OrderService",         // component
          "SAVE",                 // action
          "order",                // resource
          result,                 // result
          message + ": orderId=" + order.getOrderId(), // message
          Map.of(),               // details
          null,                   // correlation_id
          null,                   // source_ip
          null                    // user_agent
      );
      auditLogger.logEventAsync(event);
    } catch (RuntimeException e) {
      LOG.error("Failed to emit audit event", e);
    }
  }
}
