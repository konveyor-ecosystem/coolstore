package com.redhat.coolstore.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.StreamableAuditLogger;
import com.redhat.coolstore.model.Order;

@ApplicationScoped
public class OrderService {

  private static final Logger LOG = Logger.getLogger(OrderService.class.getName());

  @Inject
  EntityManager em;

  @ConfigProperty(name = "audit.stream.host", defaultValue = "localhost")
  String streamHost;
  @ConfigProperty(name = "audit.stream.port", defaultValue = "5170")
  int streamPort;
  @ConfigProperty(name = "audit.stream.protocol", defaultValue = "TCP")
  String streamProtocol;

  private StreamableAuditLogger auditLogger;

  @Transactional
  public void save(Order order) {
    em.persist(order);
    audit(order);
  }

  public List<Order> getOrders() {
    return em.createQuery("select o from Order o", Order.class).getResultList();
  }

  public Order getOrderById(long id) {
    return em.find(Order.class, id);
  }

  /** Rules audit-logging-0002..0005: direct AuditEvent record + logEventAsync; failures never break save. */
  void audit(Order order) {
    if (auditLogger == null) {
      return;
    }
    try {
      AuditEvent event = new AuditEvent(Instant.now(), UUID.randomUUID().toString(), "ORDER_PLACED",
          "coolstore", "order-service", String.valueOf(order.getCustomerName()), "SAVE_ORDER",
          "order/" + order.getOrderId(), AuditResult.SUCCESS, "Order persisted",
          Map.of("orderValue", order.getOrderValue()), null, null, null);
      auditLogger.logEventAsync(event).exceptionally(t -> {
        LOG.warning("Audit event not delivered: " + t.getMessage());
        return null;
      });
    } catch (Exception e) {
      LOG.warning("Audit logging failed: " + e.getMessage());
    }
  }

  @PostConstruct
  void init() {
    try {
      AuditConfiguration config = new AuditConfiguration();
      config.setStreamHost(streamHost);
      config.setStreamPort(streamPort);
      config.setStreamProtocol(streamProtocol);
      auditLogger = new StreamableAuditLogger(config);
    } catch (Exception e) {
      LOG.warning("Audit logger unavailable: " + e.getMessage());
    }
  }

  @PreDestroy
  void cleanup() {
    try {
      if (auditLogger != null) {
        auditLogger.close();
      }
    } catch (Exception e) {
      LOG.fine(e.getMessage());
    }
  }
}
