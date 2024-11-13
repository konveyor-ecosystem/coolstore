
package com.redhat.coolstore.service;

import jakarta.inject.Inject; // Updated import statement

import jakarta.enterprise.context.ApplicationScoped; // Updated import statement
import jakarta.jms.JMSException; // Updated import statement
import jakarta.jms.Message; // Updated import statement
import jakarta.jms.MessageListener; // Updated import statement
import jakarta.jms.TextMessage; // Updated import statement

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped // Replaced @MessageDriven with @ApplicationScoped
public class OrderServiceMDB implements MessageListener { 

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Override
	public void onMessage(Message rcvMessage) {
		System.out.println("\nMessage recd !");
		TextMessage msg = null;
		try {
				if (rcvMessage instanceof TextMessage) {
						msg = (TextMessage) rcvMessage;
						String orderStr = msg.getBody(String.class);
						System.out.println("Received order: " + orderStr);
						Order order = Transformers.jsonToOrder(orderStr);
						System.out.println("Order object is " + order);
						orderService.save(order);
						order.getItemList().forEach(orderItem -> {
							catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
						});
				}
		} catch (JMSException e) {
			throw new RuntimeException(e);
		}
	}

}
