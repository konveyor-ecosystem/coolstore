@RequestScoped
public class ShippingService {

    @Path("/calculate-shipping")
    @GET
    public Response calculateShipping(@QueryParam("cartItemTotal") Double cartItemTotal) {
        // Implement the shipping calculation logic here
    }

    @Path("/calculate-shipping-insurance")
    @GET
    public Response calculateShippingInsurance(@QueryParam("cartItemTotal") Double cartItemTotal) {
        // Implement the shipping insurance calculation logic here
    }
}
