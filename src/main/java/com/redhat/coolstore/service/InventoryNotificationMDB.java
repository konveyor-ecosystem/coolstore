
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Stream;
import io.smallrye.reactive.messaging.jms.IncomingJmsMessage;
import io.smallrye.reactive.messaging.jms.JmsMessage;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped; // Updated import statement

import java.util.logging.Logger;

@ApplicationScoped
public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;
    private static final Logger LOGGER = Logger.getLogger(InventoryNotificationMDB.class.getName());

    @Inject
    private CatalogService catalogService;

    @Stream("orders")
    @IncomingJmsMessage(topic = "topic/orders")
    public void onMessage(JmsMessage<String> rcvMessage) {
        try {
            LOGGER.info("received message inventory");
            String orderStr = rcvMessage.getPayload();
            Order order = Transformers.jsonToOrder(orderStr);
            order.getItemList().forEach(orderItem -> {
                int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
                int new_quantity = old_quantity - orderItem.getQuantity();
                if (new_quantity < LOW_THRESHOLD) {
                    LOGGER.info("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
                } else {
                    orderItem.setQuantity(new_quantity);
                }
            });
        } catch (Exception e) {
            LOGGER.severe("An exception occurred: " + e.getMessage());
        }
    }
}
