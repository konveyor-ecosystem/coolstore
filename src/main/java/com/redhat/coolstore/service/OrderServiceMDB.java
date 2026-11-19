package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/**
 * Replaces the JMS {@code @MessageDriven} OrderServiceMDB (topic/orders, auto-acknowledge).
 * Consumes the "orders" AMQP channel, persists the order and decrements inventory per item.
 * Exceptions propagate so the message is not acknowledged (nacked) and is redelivered,
 * preserving the original MDB semantics.
 */
@ApplicationScoped
public class OrderServiceMDB {

    private static final Logger LOG = Logger.getLogger(OrderServiceMDB.class);

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    public void onOrder(String orderStr) {
        LOG.info("Received order message");
        LOG.debug("Received order: " + orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        orderService.save(order);
        order.getItemList().forEach(orderItem ->
            catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity()));
        LOG.info("Order persisted and inventory updated");
    }
}
