I will give you a JavaEE file for which I want to take one step towards migrating to Quarkus.

I will provide you with static source code analysis information highlighting an issue which needs to be addressed.

I will also provide you with an example of how a similar issue was solved in the past via a solved example.

You can refer to the solved example for a pattern of how to update the input Java EE file to Quarkus.

Fix only the problem described. Other problems will be solved in subsequent steps so it is unnecessary to handle them now.

Before attempting to migrate the code to Quarkus reason through what changes are required and why.

Pay attention to changes you make and impacts to external dependencies in the pom.xml as well as changes to imports we need to consider.

As you make changes that impact the pom.xml or imports, be sure you explain what needs to be updated.

After you have shared your step by step thinking, provide a full output of the updated file.
# Input Information

## Input File

File name: "src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java"
Source file contents:
```java
package com.redhat.coolstore.rest;

import java.io.Serializable;
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.service.OrderService;

@RequestScoped
@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
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

```

## Issues

### Issue 1
Issue to fix: "Replace the `javax.enterprise` import statement with `jakarta.enterprise` "
Line number: 6
### Issue 2
Issue to fix: "Replace the `javax.inject` import statement with `jakarta.inject` "
Line number: 7
### Issue 3
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 8
### Issue 4
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 9
### Issue 5
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 10
### Issue 6
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 11
### Issue 7
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 12
### Issue 8
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 13
# Output Instructions

Structure your output in Markdown format such as:

## Reasoning

Write the step by step reasoning in this markdown section. If you are unsure of a step or reasoning, clearly state you are unsure and why.

## Updated File

```java
// Write the updated file for Quarkus in this section
```

## Additional Information (optional)

If you have any additonal details or steps that need to be performed, put it here
