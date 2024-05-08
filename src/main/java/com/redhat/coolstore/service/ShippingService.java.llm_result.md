Model ID: gpt-4
## Reasoning

The first step is to replace the `javax.ejb` import statements with `jakarta.ejb` as per the issues 1 and 2. This is because Quarkus uses the Jakarta EE APIs instead of the older Java EE APIs.

Next, we need to remove the `@Stateless` and `@Remote` annotations as per issues 3 and 4. Stateless EJBs are not supported in Quarkus, and REST services are stateless by nature, making the `@Stateless` annotation unnecessary. Remote EJBs are also not supported in Quarkus, and should be replaced with REST functionality.

We then need to add the `@ApplicationScoped` annotation to the class. This is because Stateless EJBs can be converted to a CDI bean by replacing the `@Stateless` annotation with a scope, such as `@ApplicationScoped`.

Finally, we need to add the `@Path`, `@GET`, and `@QueryParam` annotations to the public methods. This is to specify the actual path to the REST service, and to specify the HTTP method and any query parameters.

## Updated File

```java
package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.ejb.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;

import com.redhat.coolstore.model.ShoppingCart;

@Path("/shipping")
@ApplicationScoped
public class ShippingService {

    @GET
    @Path("/calculateShipping")
    public double calculateShipping(@QueryParam("sc") ShoppingCart sc) {

        if (sc != null) {

            if (sc.getCartItemTotal() >= 0 && sc.getCartItemTotal() < 25) {

                return 2.99;

            } else if (sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 50) {

                return 4.99;

            } else if (sc.getCartItemTotal() >= 50 && sc.getCartItemTotal() < 75) {

                return 6.99;

            } else if (sc.getCartItemTotal() >= 75 && sc.getCartItemTotal() < 100) {

                return 8.99;

            } else if (sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 10000) {

                return 10.99;

            }

        }

        return 0;

    }

    @GET
    @Path("/calculateShippingInsurance")
    public double calculateShippingInsurance(@QueryParam("sc") ShoppingCart sc) {

        if (sc != null) {

            if (sc.getCartItemTotal() >= 25 && sc.getCartItemTotal() < 100) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.02);

            } else if (sc.getCartItemTotal() >= 100 && sc.getCartItemTotal() < 500) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.015);

            } else if (sc.getCartItemTotal() >= 500 && sc.getCartItemTotal() < 10000) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.01);

            }

        }

        return 0;
    }

    private static double getPercentOfTotal(double value, double percentOfTotal) {
        return BigDecimal.valueOf(value * percentOfTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

}
```

## Additional Information (optional)

The `pom.xml` file should be updated to include the Quarkus dependencies, and any dependencies related to `javax.ejb` should be removed.