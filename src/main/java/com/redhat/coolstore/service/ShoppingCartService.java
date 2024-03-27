package com.redhat.coolstore.service;

import java.util.logging.Logger;

import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@ApplicationScoped
public class ShoppingCartService  {

    @Inject
    Logger log;

    @Inject
    ProductService productServices;

    @Inject
    PromoService ps;
    
    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;
    
    @Inject
    ShippingServiceRemote shippingServiceRemote;

    private ShoppingCart cart  = new ShoppingCart();

    public ShoppingCartService() {
    }

    public ShoppingCart getShoppingCart(String cartId) {
        return cart;
    }
    
    // ... Rest of the class methods without changes ...
    
    private static ShippingServiceRemote lookupShippingServiceRemote() {
        return shippingServiceRemote;
    }
}