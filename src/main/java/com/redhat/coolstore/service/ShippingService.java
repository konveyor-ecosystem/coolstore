package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.enterprise.context.ApplicationScoped;

@Path("/shipping")
@ApplicationScoped
public class ShippingService {

    @GET
    @Path("/calculateShipping")
    public double calculateShipping(@QueryParam("cartTotal") double cartTotal) {
        if (cartTotal >= 0 && cartTotal < 25) {
            return 2.99;
        } else if (cartTotal >= 25 && cartTotal < 50) {
            return 4.99;
        } else if (cartTotal >= 50 && cartTotal < 75) {
            return 6.99;
        } else if (cartTotal >= 75 && cartTotal < 100) {
            return 8.99;
        } else if (cartTotal >= 100 && cartTotal < 10000) {
            return 10.99;
        }
        return 0;
    }

    @GET
    @Path("/calculateShippingInsurance")
    public double calculateShippingInsurance(@QueryParam("cartTotal") double cartTotal) {
        if (cartTotal >= 25 && cartTotal < 100) {
            return getPercentOfTotal(cartTotal, 0.02);
        } else if (cartTotal >= 100 && cartTotal < 500) {
            return getPercentOfTotal(cartTotal, 0.015);
        } else if (cartTotal >= 500 && cartTotal < 10000) {
            return getPercentOfTotal(cartTotal, 0.01);
        }
        return 0;
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}