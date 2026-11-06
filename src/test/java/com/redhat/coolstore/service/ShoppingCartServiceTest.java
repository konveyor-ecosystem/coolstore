package com.redhat.coolstore.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShoppingCartService} with Mockito mocks around the
 * service community (ProductService, ShoppingCartOrderProcessor,
 * ShippingServiceRemote) and the real {@link PromoService} as a @Spy.
 */
@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceTest {

    @Mock
    ProductService productServices;

    @Mock
    ShoppingCartOrderProcessor shoppingCartOrderProcessor;

    @Mock
    ShippingServiceRemote shippingService;

    @Spy
    PromoService ps = new PromoService();

    @InjectMocks
    ShoppingCartService shoppingCartService;

    private Product product(String itemId, double price) {
        Product p = new Product();
        p.setItemId(itemId);
        p.setName("Product " + itemId);
        p.setPrice(price);
        return p;
    }

    @Test
    @DisplayName("getShoppingCart: same cart instance per cartId, different cartId -> different cart")
    void getShoppingCart_cartIdKeyed() {
        ShoppingCart first = shoppingCartService.getShoppingCart("a");
        ShoppingCart second = shoppingCartService.getShoppingCart("a");
        assertSame(first, second, "same cartId must return the same cart instance");

        ShoppingCart other = shoppingCartService.getShoppingCart("b");
        assertNotSame(first, other, "different cartId must yield a different cart");
    }

    @Test
    @DisplayName("priceShoppingCart: totals accumulate price*qty and the 329299 promo (25% off) is applied")
    void priceShoppingCart_sumsTotals_andAppliesPromo() {
        Product promoProduct = product("329299", 100.0);
        Product regularProduct = product("165614", 50.0);
        when(productServices.getProductByItemId("329299")).thenReturn(promoProduct);
        when(productServices.getProductByItemId("165614")).thenReturn(regularProduct);
        when(shippingService.calculateShipping(any(ShoppingCart.class))).thenReturn(2.99);
        when(shippingService.calculateShippingInsurance(any(ShoppingCart.class))).thenReturn(0.0);

        ShoppingCart cart = shoppingCartService.getShoppingCart("cart1");
        ShoppingCartItem promoItem = new ShoppingCartItem();
        promoItem.setProduct(promoProduct);
        promoItem.setQuantity(2);
        promoItem.setPrice(100.0);
        ShoppingCartItem regularItem = new ShoppingCartItem();
        regularItem.setProduct(regularProduct);
        regularItem.setQuantity(1);
        regularItem.setPrice(50.0);
        cart.addShoppingCartItem(promoItem);
        cart.addShoppingCartItem(regularItem);

        shoppingCartService.priceShoppingCart(cart);

        // promo applied to 329299 only: 25% off -> unit price 75.0, savings -25.0 per unit
        assertEquals(75.0, promoItem.getPrice(), 1e-9);
        assertEquals(-25.0, promoItem.getPromoSavings(), 1e-9);
        assertEquals(50.0, regularItem.getPrice(), 1e-9);
        assertEquals(0.0, regularItem.getPromoSavings(), 1e-9);

        // cart totals accumulate price * quantity
        assertEquals(75.0 * 2 + 50.0 * 1, cart.getCartItemTotal(), 1e-9);
        assertEquals(-25.0 * 2, cart.getCartItemPromoSavings(), 1e-9);

        // shipping quoted at 2.99 but the free-shipping promo kicks in (total >= 75)
        assertEquals(-2.99, cart.getShippingPromoSavings(), 1e-9);
        assertEquals(0.0, cart.getShippingTotal(), 1e-9);
        assertEquals(200.0, cart.getCartTotal(), 1e-9);
    }

    @Test
    @DisplayName("checkOutShoppingCart: order processor invoked once and the cart is reset")
    void checkOutShoppingCart_sendsOrder_andResetsCart() {
        ShoppingCart cart = shoppingCartService.getShoppingCart("checkout-cart");
        ShoppingCartItem item = new ShoppingCartItem();
        item.setProduct(product("329299", 100.0));
        item.setQuantity(1);
        item.setPrice(100.0);
        cart.addShoppingCartItem(item);
        cart.setCartItemTotal(100.0);

        ShoppingCart result = shoppingCartService.checkOutShoppingCart("checkout-cart");

        assertNotNull(result, "checkout must return a cart");
        assertSame(cart, result, "checkout returns the cartId-keyed cart instance");
        verify(shoppingCartOrderProcessor, times(1)).process(cart);
        assertTrue(result.getShoppingCartItemList().isEmpty(), "cart item list must be reset");
        assertEquals(0.0, result.getCartItemTotal(), 1e-9, "cart is re-priced empty after checkout");
    }
}
