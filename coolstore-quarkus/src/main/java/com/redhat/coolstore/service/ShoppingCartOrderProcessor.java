package com.redhat.coolstore.service;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ShoppingCartOrderProcessor {

    private static final Logger LOG = Logger.getLogger(ShoppingCartOrderProcessor.class);

    @Inject
    @Channel("orders")
    Emitter<String> ordersEmitter;

    public void process(ShoppingCart cart) {
        LOG.info("Sending order from processor: ");
        String orderJson = Transformers.shoppingCartToJson(cart);
        ordersEmitter.send(orderJson);
        LOG.infof("Order sent to 'orders' channel: %s", orderJson);
    }
}
