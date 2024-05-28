I will give you a JavaEE file for which I want to take one step towards migrating to Quarkus.

I will provide you with static source code analysis information highlighting an issue which needs to be addressed.

I will also provide you with an example of how a similar issue was solved in the past via a solved example.

You can refer to the solved example for a pattern of how to update the input Java EE file to Quarkus.

Fix only the problem described. Other problems will be solved in subsequent steps so it is unnecessary to handle them now.

Before attempting to migrate the code to Quarkus reason through what changes are required and why.

Pay attention to changes you make and impacts to external dependencies in the pom.xml as well as changes to imports we need to consider.

Remember when updating or adding annotations that the class must be imported.

As you make changes that impact the pom.xml or imports, be sure you explain what needs to be updated.

After you have shared your step by step thinking, provide a full output of the updated file.

# Input information

## Input File

File name: "src/main/java/com/redhat/coolstore/service/ShippingService.java"
Source file contents:
```java
package com.redhat.coolstore.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.ejb.Remote;
import javax.ejb.Stateless;

import com.redhat.coolstore.model.ShoppingCart;

@Stateless
@Remote
public class ShippingService implements ShippingServiceRemote {

    @Override
    public double calculateShipping(ShoppingCart sc) {

        if (sc != null) {

            if (sc.getCartItemTotal() &gt;= 0 &amp;&amp; sc.getCartItemTotal() &lt; 25) {

                return 2.99;

            } else if (sc.getCartItemTotal() &gt;= 25 &amp;&amp; sc.getCartItemTotal() &lt; 50) {

                return 4.99;

            } else if (sc.getCartItemTotal() &gt;= 50 &amp;&amp; sc.getCartItemTotal() &lt; 75) {

                return 6.99;

            } else if (sc.getCartItemTotal() &gt;= 75 &amp;&amp; sc.getCartItemTotal() &lt; 100) {

                return 8.99;

            } else if (sc.getCartItemTotal() &gt;= 100 &amp;&amp; sc.getCartItemTotal() &lt; 10000) {

                return 10.99;

            }

        }

        return 0;

    }

    @Override
    public double calculateShippingInsurance(ShoppingCart sc) {

        if (sc != null) {

            if (sc.getCartItemTotal() &gt;= 25 &amp;&amp; sc.getCartItemTotal() &lt; 100) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.02);

            } else if (sc.getCartItemTotal() &gt;= 100 &amp;&amp; sc.getCartItemTotal() &lt; 500) {

                return getPercentOfTotal(sc.getCartItemTotal(), 0.015);

            } else if (sc.getCartItemTotal() &gt;= 500 &amp;&amp; sc.getCartItemTotal() &lt; 10000) {

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

## Issues

### incident 0
incident to fix: "Replace the `javax.ejb` import statement with `jakarta.ejb` "
Line number: 6
### incident 1
incident to fix: "Replace the `javax.ejb` import statement with `jakarta.ejb` "
Line number: 7
### incident 2
incident to fix: "Remote EJBs are not supported in Quarkus, and therefore its use must be removed and replaced with REST functionality. In order to do this:
 1. Replace the `@Remote` annotation on the class with a `@jakarta.ws.rs.Path(&#34;&lt;endpoint&gt;&#34;)` annotation. An endpoint must be added to the annotation in place of `&lt;endpoint&gt;` to specify the actual path to the REST service.
 2. Remove `@Stateless` annotations if present. Given that REST services are stateless by nature, it makes it unnecessary.
 3. For every public method on the EJB being converted, do the following:
 - Annotate the method with `@jakarta.ws.rs.GET`
 - Annotate the method with `@jakarta.ws.rs.Path(&#34;&lt;endpoint&gt;&#34;)` and give it a proper endpoint path. As a rule of thumb, the method name can be used as endpoint, for instance:
 ```
 @Path(&#34;/increment&#34;)
 public void increment() 
 ```
 - Add `@jakarta.ws.rs.QueryParam(&#34;&lt;param-name&gt;&#34;)` to any method parameters if needed, where `&lt;param-name&gt;` is a name for the parameter."
Line number: 12
### incident 3
incident to fix: "Stateless EJBs can be converted to a cdi bean by importing `jakarta.enterprise.context.ApplicationScoped` and replacing the `@Stateless` annotation with a scope eg `@ApplicationScoped`
"
Line number: 11

# Output Instructions
Structure your output in Markdown format such as:

## Reasoning
Write the step by step reasoning in this markdown section. If you are unsure of a step or reasoning, clearly state you are unsure and why.

## Updated File
```java
// Write the updated file for Quarkus in this section. If the file should be removed, make the content of the updated file a comment explaining it should be removed.
```

## Additional Information (optional)

If you have any additional details or steps that need to be performed, put it here.

