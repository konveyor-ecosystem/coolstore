package com.redhat.coolstore.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;

import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.redhat.coolstore.model.Order;

@ApplicationScoped
public class OrderService {

    @Inject
    EntityManager em;

    @Inject
    AuditLoggerProducer audit;

    @Transactional
    public void save(Order order) {
        try {
            em.persist(order);
            audit(order, AuditResult.SUCCESS, "Order persisted");
        } catch (RuntimeException e) {
            audit(order, AuditResult.FAILURE, "Order persistence failed: " + e.getMessage());
            throw e;
        }
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

    private void audit(Order order, AuditResult result, String message) {
        AuditEvent event = new AuditEvent(Instant.now(), "ORDER_CREATED", order.getCustomerEmail(), null,
                "coolstore", "OrderService", "CREATE_ORDER", "order/" + order.getOrderId(), result, message,
                Map.of("orderValue", order.getOrderValue(), "items", order.getItemList().size()),
                UUID.randomUUID().toString(), null, null);
        audit.publish(event);
    }
}
