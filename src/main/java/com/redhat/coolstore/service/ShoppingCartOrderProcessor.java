package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.jms.Emitter;
import jakarta.jms.Channel;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    @Channel("orders")
    private Emitter<String> ordersEmitter;

    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;

    
  
    public void  process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersEmitter.emit("order", Transformers.shoppingCartToJson(cart));
    }



}
