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

File name: "src/main/java/com/redhat/coolstore/service/PromoService.java"
Source file contents:
```java
package com.redhat.coolstore.service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;

import com.redhat.coolstore.model.Promotion;
import com.redhat.coolstore.model.ShoppingCart;
import com.redhat.coolstore.model.ShoppingCartItem;

@ApplicationScoped
public class PromoService implements Serializable {

    private static final long serialVersionUID = 2088590587856645568L;

    private String name = null;

    private Set&lt;Promotion&gt; promotionSet = null;

    public PromoService() {

        promotionSet = new HashSet&lt;&gt;();

        promotionSet.add(new Promotion(&#34;329299&#34;, .25));

    }

    public void applyCartItemPromotions(ShoppingCart shoppingCart) {

        if (shoppingCart != null &amp;&amp; shoppingCart.getShoppingCartItemList().size() &gt; 0) {

            Map&lt;String, Promotion&gt; promoMap = new HashMap&lt;String, Promotion&gt;();

            for (Promotion promo : getPromotions()) {

                promoMap.put(promo.getItemId(), promo);

            }

            for (ShoppingCartItem sci : shoppingCart.getShoppingCartItemList()) {

                String productId = sci.getProduct().getItemId();

                Promotion promo = promoMap.get(productId);

                if (promo != null) {

                    sci.setPromoSavings(sci.getProduct().getPrice() * promo.getPercentOff() * -1);
                    sci.setPrice(sci.getProduct().getPrice() * (1 - promo.getPercentOff()));

                }

            }

        }

    }

    public void applyShippingPromotions(ShoppingCart shoppingCart) {

        if (shoppingCart != null) {

            //PROMO: if cart total is greater than 75, free shipping
            if (shoppingCart.getCartItemTotal() &gt;= 75) {

                shoppingCart.setShippingPromoSavings(shoppingCart.getShippingTotal() * -1);
                shoppingCart.setShippingTotal(0);

            }

        }

    }

    public Set&lt;Promotion&gt; getPromotions() {

        if (promotionSet == null) {

            promotionSet = new HashSet&lt;&gt;();

        }

        return new HashSet&lt;&gt;(promotionSet);

    }

    public void setPromotions(Set&lt;Promotion&gt; promotionSet) {

        if (promotionSet != null) {

            this.promotionSet = new HashSet&lt;&gt;(promotionSet);

        } else {

            this.promotionSet = new HashSet&lt;&gt;();

        }

    }

    @Override
    public String toString() {
        return &#34;PromoService [name=&#34; + name + &#34;, promotionSet=&#34; + promotionSet + &#34;]&#34;;
    }

}

```

## Issues

### incident 0
incident to fix: "Replace the `javax.enterprise` import statement with `jakarta.enterprise` "
Line number: 9

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

