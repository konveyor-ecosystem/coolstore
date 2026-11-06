package com.redhat.coolstore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link PromoService} (plain JUnit, no Quarkus).
 *
 * <p>{@code PromoService} sits in the migrated hotspot (rest→service w=14,
 * service→model w=37). These tests pin down the promotion rules carried over
 * from the legacy EAP service community:
 * <ul>
 *   <li>25% off catalog item 329299</li>
 *   <li>free shipping once the cart item total reaches 75.00</li>
 * </ul>
 */
class PromoServiceTest {

    private PromoService promoService;

    @BeforeEach
    void setUp() {
        promoService = new PromoService();
    }

    private Product product(String itemId, double price) {
        Product p = new Product();
        p.setItemId(itemId);
        p.setName("Product " + itemId);
        p.setDesc("test product");
        p.setPrice(price);
        return p;
    }

    private ShoppingCartItem item(Product p, int quantity) {
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(p);
        sci.setQuantity(quantity);
        sci.setPrice(p.getPrice());
        return sci;
    }

    @Test
    @DisplayName("item 329299 gets 25% off: promoSavings == price*0.25*-1 and price == price*0.75")
    void applyCartItemPromotions_promoItem329299() {
        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(item(product("329299", 100.0), 1));

        promoService.applyCartItemPromotions(cart);

        ShoppingCartItem sci = cart.getShoppingCartItemList().get(0);
        assertEquals(100.0 * 0.25 * -1, sci.getPromoSavings(), 1e-9);
        assertEquals(100.0 * 0.75, sci.getPrice(), 1e-9);

        // Characterize how ShoppingCartService.priceShoppingCart accumulates the
        // per-item values into the cart totals (same formulas as production code):
        cart.setCartItemPromoSavings(
                cart.getCartItemPromoSavings() + sci.getPromoSavings() * sci.getQuantity());
        cart.setCartItemTotal(cart.getCartItemTotal() + sci.getPrice() * sci.getQuantity());

        assertEquals(-25.0, cart.getCartItemPromoSavings(), 1e-9);
        assertEquals(75.0, cart.getCartItemTotal(), 1e-9);
    }

    @Test
    @DisplayName("items without a promotion are left untouched")
    void applyCartItemPromotions_nonPromoItemUntouched() {
        ShoppingCart cart = new ShoppingCart();
        cart.addShoppingCartItem(item(product("165614", 50.0), 3));

        promoService.applyCartItemPromotions(cart);

        ShoppingCartItem sci = cart.getShoppingCartItemList().get(0);
        assertEquals(0.0, sci.getPromoSavings(), 1e-9);
        assertEquals(50.0, sci.getPrice(), 1e-9);
    }

    @Test
    @DisplayName("cartItemTotal 75.00 -> shippingPromoSavings == -shippingTotal and shippingTotal == 0")
    void applyShippingPromotions_freeShippingAt75() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(75.00);
        double shippingBefore = 8.99;
        cart.setShippingTotal(shippingBefore);

        promoService.applyShippingPromotions(cart);

        assertEquals(-shippingBefore, cart.getShippingPromoSavings(), 1e-9);
        assertEquals(0.0, cart.getShippingTotal(), 1e-9);
    }

    @Test
    @DisplayName("cartItemTotal 74.99 -> NOT free shipping, nothing changes")
    void applyShippingPromotions_notFreeAt74_99() {
        ShoppingCart cart = new ShoppingCart();
        cart.setCartItemTotal(74.99);
        cart.setShippingTotal(8.99);

        promoService.applyShippingPromotions(cart);

        assertEquals(0.0, cart.getShippingPromoSavings(), 1e-9);
        assertEquals(8.99, cart.getShippingTotal(), 1e-9);
    }

    @Test
    @DisplayName("empty cart -> no NPE and no changes")
    void emptyCart_noNpe_noChanges() {
        ShoppingCart cart = new ShoppingCart();

        assertDoesNotThrow(() -> promoService.applyCartItemPromotions(cart));
        assertDoesNotThrow(() -> promoService.applyShippingPromotions(cart));

        assertTrue(cart.getShoppingCartItemList().isEmpty());
        assertEquals(0.0, cart.getCartItemTotal(), 1e-9);
        assertEquals(0.0, cart.getCartItemPromoSavings(), 1e-9);
        assertEquals(0.0, cart.getShippingTotal(), 1e-9);
        assertEquals(0.0, cart.getShippingPromoSavings(), 1e-9);
        assertEquals(0.0, cart.getCartTotal(), 1e-9);

        // null carts are tolerated too (defensive null checks in the service)
        assertDoesNotThrow(() -> promoService.applyCartItemPromotions(null));
        assertDoesNotThrow(() -> promoService.applyShippingPromotions(null));
    }
}
