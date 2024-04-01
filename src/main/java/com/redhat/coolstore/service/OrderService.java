package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.CriteriaBuilder;
import jakarta.persistence.CriteriaQuery;
import jakarta.persistence.Root;

@ApplicationScoped
public class OrderService {

  @Inject
  private EntityManager em;

  public void save(Order order) {
    em.persist(order);
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
