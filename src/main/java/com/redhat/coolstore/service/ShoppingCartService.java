package com.redhat.coolstore.service;

import org.jboss.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ShippingServiceRemote;

@SessionScoped
public class ShoppingCartService  {

    @Inject
    Logger log;

    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    private ShoppingCart cart  = new ShoppingCart(); //Each user can have multiple shopping carts (tabbed browsing)

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

        ShoppingCartItem[] sciArray = cart.getShoppingCartItemList().toArray(new ShoppingCartItem[0]);
        for (ShoppingCartItem sci : sciArray) {
            Product p = getProduct(sci.getProduct().getItemId());
            //if product exist
            if (p != null) {
                sci.setProduct(p);
                sci.setPrice(p.getPrice());
            }

            sci.setPromoSavings(0);
        }

        cart.setCartItemTotal(calculateCartItemTotal(sciArray));
        cart.setShippingTotal(calculateShippingTotal(cart));

        cart.setCartTotal(cart.getCartItemTotal() + cart.getShippingTotal());

        return cart;
    }

    @Inject
    private Product getProduct(String itemId) {
        // Implementation to inject and lookup Product EJB
    }

    private double calculateCartItemTotal(ShoppingCartItem[] sciArray) {
        double cartItemTotal = 0;
        for (ShoppingCartItem sci : sciArray) {
            cartItemTotal += sci.getPrice() * sci.getQuantity();
        }
        return cartItemTotal;
    }

    private double calculateShippingTotal(ShoppingCart cart) {
        double shippingTotal = 0;
        if (cart.getCartItemTotal() >= 25) {
            shippingTotal += lookupShippingServiceRemote().calculateShippingInsurance(cart);
        }
        shippingTotal += lookupShippingServiceRemote().calculateShipping(cart);
        return shippingTotal;
    }

    @Inject
    private ShippingServiceRemote lookupShippingServiceRemote();

}
