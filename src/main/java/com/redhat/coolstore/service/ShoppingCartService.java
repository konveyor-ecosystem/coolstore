package com.redhat.coolstore.service;

import java.util.Hashtable;
import java.util.logging.Logger;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.promo.PromoService;
import com.redhat.coolstore.service.shipping.ShippingServiceRemote;
import com.redhat.coolstore.service.order.ShoppingCartOrderProcessor;

import jakarta.inject.Inject;
import jakarta.naming.Context;
import jakarta.naming.InitialContext;
import jakarta.naming.NamingException;

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

        cart.setShippingTotal(lookupShippingServiceRemote().calculateShipping(cart));

        if (cart.getCartItemTotal() >= 25) {
            cart.setShippingTotal(cart.getShippingTotal()
                    + lookupShippingServiceRemote().calculateShippingInsurance(cart));
        }

        ps.applyShippingPromotions(cart);

        cart.setCartTotal(cart.getCartItemTotal() + cart.getShippingTotal());

        return cart;
    }

    public Product getProduct(String itemId) {
        return productServices.getProductByItemId(itemId);
    }

	private static ShippingServiceRemote lookupShippingServiceRemote() {
        try {
            final Hashtable<String, String> jndiProperties = new Hashtable<>();
            jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");

            final Context context = new InitialContext(jndiProperties);

            return (ShippingServiceRemote) context.lookup("ejb:/ROOT/ShippingService!" + ShippingServiceRemote.class.getName());
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }
}

