package com.redhat.coolstore.service;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

import io.smallrye.reactive.messaging.annotations.Blocking;

/** Replaces the @MessageDriven topic/orders consumer. */
@ApplicationScoped
public class OrderServiceMDB {

	private static final Logger LOG = Logger.getLogger(OrderServiceMDB.class.getName());

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("orders")
	@Blocking
	@Transactional
	public void onMessage(String orderStr) {
		LOG.info("Received order: " + orderStr);
		Order order = Transformers.jsonToOrder(orderStr);
		orderService.save(order);
		order.getItemList().forEach(orderItem ->
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity()));
	}
}
