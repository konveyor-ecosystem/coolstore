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

File name: "src/main/java/com/redhat/coolstore/utils/Transformers.java"
Source file contents:
```java
package com.redhat.coolstore.utils;

import com.redhat.coolstore.model.CatalogItemEntity;
import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.model.OrderItem;
import com.redhat.coolstore.model.Product;
import com.redhat.coolstore.model.ShoppingCart;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Created by tqvarnst on 2017-03-30.
 */
public class Transformers {

    private static final String[] RANDOM_NAMES = {&#34;Sven Karlsson&#34;,&#34;Johan Andersson&#34;,&#34;Karl Svensson&#34;,&#34;Anders Johansson&#34;,&#34;Stefan Olson&#34;,&#34;Martin Ericsson&#34;};
    private static final String[] RANDOM_EMAILS = {&#34;sven@gmail.com&#34;,&#34;johan@gmail.com&#34;,&#34;karl@gmail.com&#34;,&#34;anders@gmail.com&#34;,&#34;stefan@gmail.com&#34;,&#34;martin@gmail.com&#34;};

    private static Logger log = Logger.getLogger(Transformers.class.getName());

    public static Product toProduct(CatalogItemEntity entity) {
        Product prod = new Product();
        prod.setItemId(entity.getItemId());
        prod.setName(entity.getName());
        prod.setDesc(entity.getDesc());
        prod.setPrice(entity.getPrice());
        if (entity.getInventory() != null) {
            prod.setLocation(entity.getInventory().getLocation());
            prod.setLink(entity.getInventory().getLink());
            prod.setQuantity(entity.getInventory().getQuantity());
        } else {
            log.warning(&#34;Inventory for &#34; + entity.getName() + &#34;[&#34; + entity.getItemId()+ &#34;] unknown and missing&#34;);
        }
        return prod;
    }

    public static String shoppingCartToJson(ShoppingCart cart) {
        JsonArrayBuilder cartItems = Json.createArrayBuilder();
        cart.getShoppingCartItemList().forEach(item -&gt; {
            cartItems.add(Json.createObjectBuilder()
                .add(&#34;productSku&#34;,item.getProduct().getItemId())
                .add(&#34;quantity&#34;,item.getQuantity())
            );
        });

        int randomNameAndEmailIndex = ThreadLocalRandom.current().nextInt(RANDOM_NAMES.length);

        JsonObject jsonObject = Json.createObjectBuilder()
            .add(&#34;orderValue&#34;, Double.valueOf(cart.getCartTotal()))
            .add(&#34;customerName&#34;,RANDOM_NAMES[randomNameAndEmailIndex])
            .add(&#34;customerEmail&#34;,RANDOM_EMAILS[randomNameAndEmailIndex])
            .add(&#34;retailPrice&#34;, cart.getShoppingCartItemList().stream().mapToDouble(i -&gt; i.getQuantity()*i.getPrice()).sum())
            .add(&#34;discount&#34;, Double.valueOf(cart.getCartItemPromoSavings()))
            .add(&#34;shippingFee&#34;, Double.valueOf(cart.getShippingTotal()))
            .add(&#34;shippingDiscount&#34;, Double.valueOf(cart.getShippingPromoSavings()))
            .add(&#34;items&#34;,cartItems) 
            .build();
        StringWriter w = new StringWriter();
        try (JsonWriter writer = Json.createWriter(w)) {
            writer.write(jsonObject);
        }
        return w.toString();
    }

    public static Order jsonToOrder(String json) {
        JsonReader jsonReader = Json.createReader(new StringReader(json));
        JsonObject rootObject = jsonReader.readObject();
        Order order = new Order();
        order.setCustomerName(rootObject.getString(&#34;customerName&#34;));
        order.setCustomerEmail(rootObject.getString(&#34;customerEmail&#34;));
        order.setOrderValue(rootObject.getJsonNumber(&#34;orderValue&#34;).doubleValue());
        order.setRetailPrice(rootObject.getJsonNumber(&#34;retailPrice&#34;).doubleValue());
        order.setDiscount(rootObject.getJsonNumber(&#34;discount&#34;).doubleValue());
        order.setShippingFee(rootObject.getJsonNumber(&#34;shippingFee&#34;).doubleValue());
        order.setShippingDiscount(rootObject.getJsonNumber(&#34;shippingDiscount&#34;).doubleValue());
        JsonArray jsonItems = rootObject.getJsonArray(&#34;items&#34;);
        List&lt;OrderItem&gt; items = new ArrayList&lt;OrderItem&gt;(jsonItems.size());
        for (JsonObject jsonItem : jsonItems.getValuesAs(JsonObject.class)) {
            OrderItem oi = new OrderItem();
            oi.setProductId(jsonItem.getString(&#34;productSku&#34;));
            oi.setQuantity(jsonItem.getInt(&#34;quantity&#34;));
            items.add(oi);
        }
        order.setItemList(items); 
        return order;
    }


}

```

## Issues

### incident 0
incident to fix: "Replace the `javax.json` import statement with `jakarta.json` "
Line number: 13
### incident 1
incident to fix: "Replace the `javax.json` import statement with `jakarta.json` "
Line number: 14
### incident 2
incident to fix: "Replace the `javax.json` import statement with `jakarta.json` "
Line number: 15
### incident 3
incident to fix: "Replace the `javax.json` import statement with `jakarta.json` "
Line number: 16
### incident 4
incident to fix: "Replace the `javax.json` import statement with `jakarta.json` "
Line number: 17
### incident 5
incident to fix: "Replace the `javax.json` import statement with `jakarta.json` "
Line number: 18

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

