
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import javax.inject.Inject;
import io.smallrye.jms.JMSContext;
import io.smallrye.jms.Topic;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    JMSContext context;

    @Inject
    @Topic("orders")
    Topic ordersTopic;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
}
