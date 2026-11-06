package com.redhat.coolstore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.redhat.coolstore.model.ShoppingCart;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for {@link ShippingService} shipping fee and
 * shipping insurance bands (plain JUnit, no Quarkus).
 *
 * <p>Band boundaries are the ones inherited from the legacy implementation:
 * shipping 2.99 / 4.99 / 6.99 / 8.99 / 10.99 by cart item total, 0.0 at
 * 10000 and above (or for a null cart); insurance 2% / 1.5% / 1% with
 * HALF_UP rounding to 2 decimal places.
 */
class ShippingServiceTest {

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService();
    }

    /** ShoppingCart with only cartItemTotal set, other totals 0. */
    private ShoppingCart cartWithTotal(double cartItemTotal) {
        ShoppingCart sc = new ShoppingCart();
        sc.setCartItemTotal(cartItemTotal);
        return sc;
    }

    @ParameterizedTest(name = "calculateShipping({0}) == {1}")
    @CsvSource({
            "0.0,      2.99",
            "24.99,    2.99",
            "25.0,     4.99",
            "49.99,    4.99",
            "50.0,     6.99",
            "74.99,    6.99",
            "75.0,     8.99",
            "99.99,    8.99",
            "100.0,   10.99",
            "9999.99, 10.99",
            "10000.0,   0.0"
    })
    void calculateShipping_bands(double cartItemTotal, double expectedShipping) {
        assertEquals(expectedShipping,
                shippingService.calculateShipping(cartWithTotal(cartItemTotal)), 1e-9);
    }

    @Test
    void calculateShipping_nullCart_returnsZero() {
        assertEquals(0.0, shippingService.calculateShipping(null), 1e-9);
    }

    @ParameterizedTest(name = "calculateShippingInsurance({0}) == {1}")
    @CsvSource({
            // below 25: no insurance
            "24.99,     0.0",
            // 25 <= total < 100: 2% (HALF_UP, 2 decimals)
            "25.0,      0.5",
            "99.99,     2.0",
            // 100 <= total < 500: 1.5%
            "100.0,     1.5",
            "499.99,    7.5",
            // 500 <= total < 10000: 1%
            "500.0,     5.0",
            "9999.99, 100.0",
            // 10000 and above: no insurance
            "10000.0,   0.0"
    })
    void calculateShippingInsurance_bands(double cartItemTotal, double expectedInsurance) {
        assertEquals(expectedInsurance,
                shippingService.calculateShippingInsurance(cartWithTotal(cartItemTotal)), 1e-9);
    }

    @Test
    void calculateShippingInsurance_nullCart_returnsZero() {
        assertEquals(0.0, shippingService.calculateShippingInsurance(null), 1e-9);
    }
}
