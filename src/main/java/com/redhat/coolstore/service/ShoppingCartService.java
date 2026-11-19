package com.redhat.coolstore.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

/**
 * Formerly an EJB @Stateful session bean (one cart per HTTP session). On Quarkus the cart
 * state is held in an in-memory map keyed by cartId (risk R1): horizontally scaled pods do
 * not share this state; externalizing cart state (e.g. Redis / HTTP session) is tracked as
 * follow-up work for the OpenShift rollout.
 */
@ApplicationScoped
public class ShoppingCartService {

    @Inject
    Logger log;

    @Inject
    ProductService productServices;

    @Inject
    PromoService ps;

    @Inject
    ShippingService shippingService;

    @Inject
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    private final Map<String, ShoppingCart> carts = new ConcurrentHashMap<>();

    public ShoppingCart getShoppingCart(String cartId) {
        return carts.computeIfAbsent(cartId, id -> new ShoppingCart());
    }

    public ShoppingCart checkOutShoppingCart(String cartId) {
        ShoppingCart cart = this.getShoppingCart(cartId);

        log.info("Sending order");
        shoppingCartOrderProcessor.process(cart);

        cart.resetShoppingCartItemList();
        priceShoppingCart(cart);
        return cart;
    }

    public void priceShoppingCart(ShoppingCart sc) {
        if (sc != null) {
            initShoppingCartForPricing(sc);

            if (sc.getShoppingCartItemList() != null && sc.getShoppingCartItemList().size() > 0) {
                ps.applyCartItemPromotions(sc);

                for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
                    sc.setCartItemPromoSavings(
                            sc.getCartItemPromoSavings() + sci.getPromoSavings() * sci.getQuantity());
                    sc.setCartItemTotal(sc.getCartItemTotal() + sci.getPrice() * sci.getQuantity());
                }

                sc.setShippingTotal(shippingService.calculateShipping(sc));

                if (sc.getCartItemTotal() >= 25) {
                    sc.setShippingTotal(sc.getShippingTotal()
                            + shippingService.calculateShippingInsurance(sc));
                }
            }

            ps.applyShippingPromotions(sc);

            sc.setCartTotal(sc.getCartItemTotal() + sc.getShippingTotal());
        }
    }

    private void initShoppingCartForPricing(ShoppingCart sc) {
        sc.setCartItemTotal(0);
        sc.setCartItemPromoSavings(0);
        sc.setShippingTotal(0);
        sc.setShippingPromoSavings(0);
        sc.setCartTotal(0);

        for (ShoppingCartItem sci : sc.getShoppingCartItemList()) {
            Product p = getProduct(sci.getProduct().getItemId());
            if (p != null) {
                sci.setProduct(p);
                sci.setPrice(p.getPrice());
            }
            sci.setPromoSavings(0);
        }
    }

    public Product getProduct(String itemId) {
        return productServices.getProductByItemId(itemId);
    }
}
