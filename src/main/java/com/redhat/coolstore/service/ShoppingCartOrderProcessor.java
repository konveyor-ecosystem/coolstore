package com.redhat.coolstore.service;

import java.util.logging.Logger;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Emitter;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class ShoppingCartOrderProcessor  {

    @Inject
    Logger log;

    @Inject
    private JMSContext context;

    @Resource(name = "java:/topic/orders")
    private Emitter<String> ordersEmitter;

    public Uni<Void> process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        return Uni.createFrom().voidItem(() -> ordersEmitter.emit(Transformers.shoppingCartToJson(cart)));
    }

}
