package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.RequestScoped; // Replaced javax.enterprise with jakarta.enterprise
import jakarta.inject.Inject; // Replaced javax.inject with jakarta.inject
import jakarta.ws.rs.*; // Replaced javax.ws.rs with jakarta.ws.rs
import jakarta.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.service.ProductService;

@RequestScoped
@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProductEndpoint implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = -7227732980791688773L;

    @Inject
    private ProductService pm;


    @GET
    @Path("/")
    public List<Product> listAll() {
        return pm.getProducts();
    }

    @GET
    @Path("/{itemId}")
    public Product getProduct(@PathParam("itemId") String itemId) {
        return pm.getProductByItemId(itemId);
    }

}