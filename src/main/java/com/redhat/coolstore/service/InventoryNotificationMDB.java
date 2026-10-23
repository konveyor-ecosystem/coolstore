package com.redhat.coolstore.service;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.reactive.messaging.annotations.Blocking;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

/** Low-stock notifier (was a WebLogic t3/JNDI TopicSubscriber). */
@ApplicationScoped
public class InventoryNotificationMDB {

    static final int LOW_THRESHOLD = 50;

    @Inject
    Logger log;

    @Inject
    CatalogService catalogService;

    @Incoming("orders")
    @Blocking
    public void onMessage(String orderStr) {
        Order order = Transformers.jsonToOrder(orderStr);
        order.getItemList().forEach(orderItem -> {
            CatalogItemEntity item = catalogService.getCatalogItemById(orderItem.getProductId());
            if (item == null || item.getInventory() == null) {
                return;
            }
            int newQuantity = item.getInventory().getQuantity() - orderItem.getQuantity();
            if (newQuantity < LOW_THRESHOLD) {
                log.warning("Inventory for item " + orderItem.getProductId() + " is below threshold ("
                        + LOW_THRESHOLD + "), contact supplier!");
            }
        });
    }
}
