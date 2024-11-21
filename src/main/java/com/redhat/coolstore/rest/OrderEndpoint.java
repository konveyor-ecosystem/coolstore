
package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.RequestScoped; // Updated import from javax to jakarta
import jakarta.inject.Inject; // Updated import from javax to jakarta
import jakarta.ws.rs.Consumes; // Updated import from javax to jakarta
import jakarta.ws.rs.GET; // Updated import from javax to jakarta
import jakarta.ws.rs.Path; // Updated import from javax to jakarta
import jakarta.ws.rs.PathParam; // Updated import from javax to jakarta

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.service.OrderService;

@RequestScoped
@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
public class OrderEndpoint implements Serializable {

    private static final long serialVersionUID = -7227732980791688774L;

    @Inject
    private OrderService os;

    @GET
    @Path("/")
    public List<Order> listAll() {
        return os.getOrders();
    }

    @GET
    @Path("/{orderId}")
    public Order getOrder(@PathParam("orderId") long orderId) {
        return os.getOrderById(orderId);
    }

}
