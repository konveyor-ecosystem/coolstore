package com.redhat.coolstore.service;

import com.enterprise.audit.logging.config.AuditConfiguration;
import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.StreamableAuditLogger;
import com.redhat.coolstore.model.Order;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/** [D5] audit-logging 2.0.0: StreamableAuditLogger + AuditEvent record + logEventAsync, fail-open. */
@ApplicationScoped
public class OrderService {
  private static final Logger LOG = Logger.getLogger(OrderService.class);

  @Inject
  EntityManager em;

  @ConfigProperty(name = "coolstore.audit.enabled", defaultValue = "false")
  boolean auditEnabled;
  @ConfigProperty(name = "coolstore.audit.stream-host", defaultValue = "localhost")
  String auditHost;
  @ConfigProperty(name = "coolstore.audit.stream-port", defaultValue = "9000")
  int auditPort;

  private StreamableAuditLogger auditLogger;

  @Transactional
  public void save(Order order) {
    em.persist(order);
    audit(order);
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

  void audit(Order order) {
    if (auditLogger == null) return;
    try {
      // ASSUMPTION: record component order inferred from bytecode (timestamp,eventType,userId,sessionId,
      // application,component,action,resource,result,message,details,correlationId,sourceIp,userAgent)
      AuditEvent event = new AuditEvent(Instant.now(), "ORDER", "anonymous", null, "coolstore",
          "OrderService", "SAVE_ORDER", "order/" + order.getOrderId(), AuditResult.SUCCESS,
          "Order saved", Map.of("total", String.valueOf(order.getOrderValue())),
          UUID.randomUUID().toString(), null, null);
      auditLogger.logEventAsync(event);
    } catch (Exception e) {
      LOG.warn("Audit logging failed (fail-open)", e);
    }
  }

  @PostConstruct
  void init() {
    if (!auditEnabled) return;
    try {
      AuditConfiguration config = new AuditConfiguration();
      config.setStreamHost(auditHost);
      config.setStreamPort(auditPort);
      config.setStreamProtocol("TCP");
      auditLogger = new StreamableAuditLogger(config);
    } catch (Exception e) {
      LOG.warn("Audit logger unavailable, continuing without audit", e);
    }
  }

  @PreDestroy
  void cleanup() {
    try { if (auditLogger != null) auditLogger.close(); } catch (Exception e) { LOG.debug(e); }
  }
}
