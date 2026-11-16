package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @Inject
    EntityManager em;

    @Inject
    AuditService auditService;

    @Transactional
    public void save(Order order) {
        em.persist(order);
        auditService.logOrderEvent(
            String.valueOf(order.getOrderId()),
            "SAVED",
            "Order persisted: customer=" + order.getCustomerName() + " value=" + order.getOrderValue()
        );
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
