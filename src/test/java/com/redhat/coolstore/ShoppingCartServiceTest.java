package com.redhat.coolstore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enterprise.audit.logging.model.AuditEvent;
import com.enterprise.audit.logging.model.AuditResult;
import com.enterprise.audit.logging.service.AuditLogger;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.*;
import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceTest {

    @Mock EntityManager em;
    @Mock Logger log;
    @Mock ShoppingCartOrderProcessor processor;
    @Spy PromoService promoService = new PromoService();
    @Spy ShippingService shippingService = new ShippingService();
    @Mock ProductService productService;
    @InjectMocks ShoppingCartService service;

    private Product product(String id, double price) {
        Product p = new Product();
        p.setItemId(id);
        p.setPrice(price);
        return p;
    }

    private ShoppingCartService wired(String id, double price) {
        lenient().when(productService.getProductByItemId(id)).thenReturn(product(id, price));
        return service;
    }

    @Test
    void pricesCartWithPromoAndShipping() {
        wired("329299", 10.00);
        ShoppingCart cart = service.getShoppingCart("c1");
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product("329299", 10.00));
        sci.setQuantity(2);
        sci.setPrice(10.00);
        cart.addShoppingCartItem(sci);

        service.priceShoppingCart(cart);

        assertEquals(15.0, cart.getCartItemTotal(), 1e-9);       // 2 x 7.50
        assertEquals(-5.0, cart.getCartItemPromoSavings(), 1e-9); // 2 x -2.50
        assertEquals(2.99, cart.getShippingTotal());
        assertEquals(17.99, cart.getCartTotal(), 1e-9);
    }

    @Test
    void freeShippingAt75OrMore() {
        wired("329299", 25.00);
        ShoppingCart cart = service.getShoppingCart("c2");
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product("329299", 25.00));
        sci.setQuantity(3);
        sci.setPrice(25.00);
        cart.addShoppingCartItem(sci);

        service.priceShoppingCart(cart);

        assertEquals(56.25, cart.getCartItemTotal()); // 3 x 18.75 (25% off)
        assertEquals(8.12, cart.getShippingTotal(), 1e-9); // 56.25 < 75: 6.99 + 1.13 insurance
    }

    @Test
    void checkoutResetsCartAndEmitsOrder() {
        wired("329299", 10.00);
        ShoppingCart cart = service.getShoppingCart("c3");
        ShoppingCartItem sci = new ShoppingCartItem();
        sci.setProduct(product("329299", 10.00));
        sci.setQuantity(2);
        sci.setPrice(10.00);
        cart.addShoppingCartItem(sci);

        ShoppingCart result = service.checkOutShoppingCart("c3");

        verify(processor).process(any(ShoppingCart.class));
        assertEquals(0, result.getShoppingCartItemList().size());
        assertEquals(0, result.getCartItemTotal());
        assertEquals(0, result.getCartTotal());
    }

    @Test
    void cartStateIsKeyedByCartId() {
        wired("329299", 10.00);
        ShoppingCart a = service.getShoppingCart("a");
        ShoppingCart b = service.getShoppingCart("b");
        assertNotSame(a, b);
        assertEquals(a, service.getShoppingCart("a"));
    }
}
