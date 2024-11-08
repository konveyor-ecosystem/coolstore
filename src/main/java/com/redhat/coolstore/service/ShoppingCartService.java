
package com.redhat.coolstore.service;

import java.util.Hashtable;
import java.util.logging.Logger;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@SessionScoped
public class ShoppingCartService  {

    @Inject
    Logger log;

    @Inject
    ProductService productServices;

    @Inject
    PromoService ps;

    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    private ShoppingCart cart  = new ShoppingCart(); //Each user can have multiple shopping carts (tabbed browsing)

    // ... rest of the class remains the same ...
}
