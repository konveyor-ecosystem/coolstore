package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/** Replaces OrderServiceMDB (JMS topic/orders) [D2]. */
@ApplicationScoped
public class OrderConsumer {
    private static final Logger LOG = Logger.getLogger(OrderConsumer.class);

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    @Blocking
    @Transactional
    public void onMessage(String orderStr) {
        LOG.debugf("Received order: %s", orderStr);
        Order order = Transformers.jsonToOrder(orderStr);
        orderService.save(order);
        order.getItemList().forEach(i -> catalogService.updateInventoryItems(i.getProductId(), i.getQuantity()));
    }
}
