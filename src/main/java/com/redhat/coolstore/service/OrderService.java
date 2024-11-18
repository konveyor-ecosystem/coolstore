
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import java.util.List;
import jakarta.ejb.Stateless;
import javax.inject.Inject;
import jakarta.persistence.EntityManager; // Updated import statement to jakarta.persistence
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@Stateless
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
