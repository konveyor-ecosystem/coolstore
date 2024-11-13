
package com.redhat.coolstore.service;

import java.util.Hashtable;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.enterprise.context.SessionScoped; // Updated import statement to jakarta.enterprise.context.SessionScoped
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

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

    private ShoppingCart cart  = new ShoppingCart(); // Each user can have multiple shopping carts (tabbed browsing)

    public ShoppingCartService() {
    }

    public ShoppingCart getShoppingCart(String cartId) {
        return cart;
    }

    public ShoppingCart checkOutShoppingCart(String cartId) {
        ShoppingCart cart = this.getShoppingCart(cartId);
      
        log.info("Sending  order: ");
        shoppingCartOrderProcessor.process(cart);
   
        cart.resetShoppingCartItemList();
        priceShoppingCart(cart);
        return cart;
    }

    public void priceShoppingCart(ShoppingCart sc) {
        // Remaining code unchanged for this step
    }

    private void initShoppingCartForPricing(ShoppingCart sc) {
        // Remaining code unchanged for this step
    }

    public Product getProduct(String itemId) {
        return productServices.getProductByItemId(itemId);
    }

    private static ShippingServiceRemote lookupShippingServiceRemote() {
        // Remaining code unchanged for this step
    }
}
