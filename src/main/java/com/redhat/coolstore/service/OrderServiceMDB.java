package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.TextMessage;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;
import io.quarkus.logging.Log;

@ApplicationScoped
public class OrderServiceMDB {

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("topic/orders")
	public void onMessage(String msg) {
		Log.info("Message recd !");
		TextMessage textMsg = null;
		try {
			textMsg = (TextMessage) msg;
			String orderStr = textMsg.getText();
			Log.info("Received order: " + orderStr);
			Order order = Transformers.jsonToOrder(orderStr);
			Log.info("Order object is " + order);
			orderService.save(order);
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
