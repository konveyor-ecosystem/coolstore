package com.redhat.coolstore.service;

import jakarta.inject.Inject;
import jakarta.jms.TextMessage;
import jakarta.jms.TextMessageListener;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB implements TextMessageListener  {

    @Inject
    OrderService orderService;

    @Inject
    CatalogService catalogService;

    @Override
    public void onMessage(TextMessage message) {
        System.out.println("\nMessage recd !");
        try {
                String orderStr = message.getText();
                System.out.println("Received order: " + orderStr);
                Order order = Transformers.jsonToOrder(orderStr);
                System.out.println("Order object is " + order);
                orderService.save(order);
                order.getItemList().forEach(orderItem -> {
                    catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
                });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
