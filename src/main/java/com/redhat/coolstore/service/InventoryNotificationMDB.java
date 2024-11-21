
package com.redhat.coolstore.service;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import io.quarkus.smallrye.reactivemessaging.jms.IncomingJmsMessage; // Updated import statement for Quarkus SmallRye Reactive Messaging

import javax.inject.Inject;

import java.util.Hashtable;

public class InventoryNotificationMDB {

    private static final int LOW_THRESHOLD = 50;

    @Inject
    private CatalogService catalogService;

    private final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
    private final static String JMS_FACTORY = "TCF";
    private final static String TOPIC = "topic/orders";

    public void onMessage(@IncomingJmsMessage String orderStr) {
        Order order = Transformers.jsonToOrder(orderStr);
        order.getItemList().forEach(orderItem -> {
            int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
            int new_quantity = old_quantity - orderItem.getQuantity();
            if (new_quantity < LOW_THRESHOLD) {
                System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
            } else {
                orderItem.setQuantity(new_quantity);
            }
        });
    }

    public void init() {
        // Initialization logic remains the same
    }

    public void close() {
        // Closing logic remains the same
    }
}
