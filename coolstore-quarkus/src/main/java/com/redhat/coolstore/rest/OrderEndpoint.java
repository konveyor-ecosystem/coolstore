package com.redhat.coolstore.rest;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.service.OrderService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.Serializable;
import java.util.List;

@ApplicationScoped
@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderEndpoint implements Serializable {

    private static final long serialVersionUID = -7227732980791688774L;

    @Inject
    private OrderService os;

    @GET
    @Path("/")
    @RolesAllowed({"user", "admin"})
    public List<Order> listAll() {
        return os.getOrders();
    }

    @GET
    @Path("/{orderId}")
    @RolesAllowed({"user", "admin"})
    public Order getOrder(@PathParam("orderId") long orderId) {
        return os.getOrderById(orderId);
    }
}
