package com.redhat.coolstore.service;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.StreamableAuditLogger;
import com.redhat.coolstore.model.Order;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.quarkus.runtime.StartupEvent;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OrderService {

  @Inject
  private EntityManager em;

  private StreamableAuditLogger auditLogger;

  @ConfigProperty(name = "audit.logger.tcp.host", defaultValue = "localhost")
  String auditHost;

  @ConfigProperty(name = "audit.logger.tcp.port", defaultValue = "1514")
  int auditPort;

  void onStart(@Observes StartupEvent ev) {
    // Initialize audit logger
    AuditConfiguration config = new AuditConfiguration();
    config.setStreamHost(auditHost);
    config.setStreamPort(auditPort);
    config.setStreamProtocol("tcp");
    auditLogger = new StreamableAuditLogger(config);
  }

  @PreDestroy
  void onDestroy() {
    if (auditLogger != null) {
      auditLogger.close();
    }
  }

  @Transactional
  public void save(Order order) {
    em.persist(order);

    Map<String, Object> details = Map.of(
        "orderValue", (Object) Double.valueOf(order.getOrderValue()),
        "itemCount", (Object) Integer.valueOf(order.getItemList().size()));

    AuditEvent event = new AuditEvent(
        Instant.now(),
        "ORDER_CREATED",
        null,
        null,
        "coolstore",
        "OrderService",
        "PERSIST_ORDER",
        String.valueOf(order.getOrderId()),
        AuditResult.SUCCESS,
        "Order persisted",
        details,
        null,
        null,
        null);
    auditLogger.logEventAsync(event);
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

}
