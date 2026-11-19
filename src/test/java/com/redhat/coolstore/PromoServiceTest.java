package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.PromoService;

/** Characterization of promotions: item 329299 = 25% off; free shipping >= 75. */
class PromoServiceTest {

    private final PromoService promo = new PromoService();

    private ShoppingCartItem item(String id, double price, int qty) {
        Product p = new Product();
        p.setItemId(id);
        p.setPrice(price);
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setPrice(price);
        sci.setQuantity(qty);
        return sci;
    }

    @Test
    void promoItem329299Gets25PercentOff() {
        ShoppingCart cart = new ShoppingCart();
        cart.getShoppingCartItemList().add(item("329299", 10.00, 1));
        promo.applyCartItemPromotions(cart);
        ShoppingCartItem sci = cart.getShoppingCartItemList().get(0);
        assertEquals(7.50, sci.getPrice());
        assertEquals(-2.50, sci.getPromoSavings());
    }

    @Test
    void nonPromoItemUnchanged() {
        ShoppingCart cart = new ShoppingCart();
        cart.getShoppingCartItemList().add(item("165613", 4.15, 1));
        promo.applyCartItemPromotions(cart);
        ShoppingCartItem sci = cart.getShoppingCartItemList().get(0);
        assertEquals(4.15, sci.getPrice());
        assertEquals(0, sci.getPromoSavings());
    }

    @Test
    void freeShippingAtOrAbove75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(75);
        cart.setShippingTotal(8.99);
        promo.applyShippingPromotions(cart);
        assertEquals(0, cart.getShippingTotal());
        assertEquals(-8.99, cart.getShippingPromoSavings());
    }

    @Test
    void shippingChargedBelow75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(74.99);
        cart.setShippingTotal(6.99);
        promo.applyShippingPromotions(cart);
        assertEquals(6.99, cart.getShippingTotal());
        assertEquals(0, cart.getShippingPromoSavings());
    }
}
