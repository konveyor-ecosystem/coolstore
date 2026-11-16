package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Replaces the original @MessageDriven OrderServiceMDB.
 * Consumes order messages from the AMQP 'orders' channel (backed by AMQ Artemis topic/orders)
 * and persists the order and updates inventory.
 */
@ApplicationScoped
public class OrderServiceConsumer {

    private static final Logger LOG = Logger.getLogger(OrderServiceConsumer.class);

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    public void onMessage(String orderStr) {
        LOG.infof("Received order message: %s", orderStr);
        try {
            Order order = Transformers.jsonToOrder(orderStr);
            LOG.infof("Order object: %s", order);
            orderService.save(order);
            order.getItemList().forEach(orderItem ->
                catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity())
            );
            LOG.infof("Order processed successfully: orderId=%d", order.getOrderId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process order message: %s", orderStr);
            throw new RuntimeException("Order processing failed", e);
        }
    }
}
