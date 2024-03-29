package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.ejb.Remove;
import javax.ejb.Stateful;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.redhat.coolstore.model.ShoppingCart;

@Stateful
@Path("/shipping")
public class ShippingService {

    @GET
    @Path("/calculate-shipping")
    @Produces(MediaType.APPLICATION_JSON)
    public double calculateShipping(@QueryParam("cartItemTotal") double cartItemTotal) {

        if (cartItemTotal >= 0 && cartItemTotal < 25) {

            return 2.99;

        } else if (cartItemTotal >= 25 && cartItemTotal < 50) {

            return 4.99;

        } else if (cartItemTotal >= 50 && cartItemTotal < 75) {

            return 6.99;

        } else if (cartItemTotal >= 75 && cartItemTotal < 100) {

            return 8.99;

        } else if (cartItemTotal >= 100 && cartItemTotal < 10000) {

            return 10.99;

        }

        return 0;

    }

    @GET
    @Path("/calculate-shipping-insurance")
    @Produces(MediaType.APPLICATION_JSON)
    public double calculateShippingInsurance(@QueryParam("cartItemTotal") double cartItemTotal) {

        if (cartItemTotal >= 25 && cartItemTotal < 100) {

            return getPercentOfTotal(cartItemTotal, 0.02);

        } else if (cartItemTotal >= 100 && cartItemTotal < 500) {

            return getPercentOfTotal(cartItemTotal, 0.015);

        } else if (cartItemTotal >= 500 && cartItemTotal < 10000) {

            return getPercentOfTotal(cartItemTotal, 0.01);

        }

        return 0;
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
