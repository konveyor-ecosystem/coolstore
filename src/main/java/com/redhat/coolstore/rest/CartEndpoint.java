import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;
import com.redhat.coolstore.service.ShoppingCartService;

@RequestScoped
@Path("/cart")
public class CartEndpoint {

    private static final long serialVersionUID = -7227732980791688773L;

    @Inject
    ShoppingCartService shoppingCartService;

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart getCart(@PathParam("id") String id) {
        return shoppingCartService.getShoppingCart(id);
    }

    @POST
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart addItemToCart(@PathParam("id") String id, ShoppingCartItem item) {
        return shoppingCartService.addItemToShoppingCart(id, item);
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ShoppingCart removeItemFromCart(@PathParam("id") String id, String productId) {
        return shoppingCartService.removeItemFromShoppingCart(id, productId);
    }
}
