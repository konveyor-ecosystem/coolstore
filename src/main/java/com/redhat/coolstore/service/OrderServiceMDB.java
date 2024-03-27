package com.redhat.coolstore.service;

import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import javax.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB { 

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("topic/orders")
	public void onMessage(String orderStr) {
		System.out.println("\nMessage recd !");
		System.out.println("Received order: " + orderStr);
		Order order = Transformers.jsonToOrder(orderStr);
		System.out.println("Order object is " + order);
		orderService.save(order);
		order.getItemList().forEach(orderItem -> {
			catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
		});
	}

}