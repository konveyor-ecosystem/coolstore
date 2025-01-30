package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.Inject;
import io.quarkus.micrometer.runtime.MicrometerEmitter;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    private transient JMSContext context;

    @Inject
    @Channel("ordersTopic")
    private MicrometerEmitter<String> ordersTopicEmitter;

    
  
    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersTopicEmitter.send(Transformers.shoppingCartToJson(cart));
    }



}