
package com.redhat.coolstore.service;

import java.util.logging.Logger;
import javax.ejb.Stateless;
import jakarta.annotation.Resource;
import jakarta.inject.Inject; // Updated import statement to jakarta.inject
import jakarta.jms.JMSContext;
import jakarta.jms.Topic;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@Stateless
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    private transient JMSContext context;

    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;

    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart));
    }
}
