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

File name: "src/main/java/com/redhat/coolstore/model/ShoppingCart.java"
Source file contents:
```java
package com.redhat.coolstore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.Dependent;

@Dependent
public class ShoppingCart implements Serializable {

	private static final long serialVersionUID = -1108043957592113528L;

	private double cartItemTotal;

	private double cartItemPromoSavings;
	
	private double shippingTotal;
	
	private double shippingPromoSavings;
	
	private double cartTotal;
			
	private List<ShoppingCartItem> shoppingCartItemList = new ArrayList<ShoppingCartItem>();

	public ShoppingCart() {
		
	}
	
	public List<ShoppingCartItem> getShoppingCartItemList() {
		return shoppingCartItemList;
	}

	public void setShoppingCartItemList(List<ShoppingCartItem> shoppingCartItemList) {
		this.shoppingCartItemList = shoppingCartItemList;
	}

	public void resetShoppingCartItemList() {
		shoppingCartItemList = new ArrayList<ShoppingCartItem>();
	}

	public void addShoppingCartItem(ShoppingCartItem sci) {
		
		if ( sci != null ) {
			
			shoppingCartItemList.add(sci);
			
		}
		
	}
	
	public boolean removeShoppingCartItem(ShoppingCartItem sci) {
		
		boolean removed = false;
		
		if ( sci != null ) {
			
			removed = shoppingCartItemList.remove(sci);
			
		}
		
		return removed;
		
	}

	public double getCartItemTotal() {
		return cartItemTotal;
	}

	public void setCartItemTotal(double cartItemTotal) {
		this.cartItemTotal = cartItemTotal;
	}

	public double getShippingTotal() {
		return shippingTotal;
	}

	public void setShippingTotal(double shippingTotal) {
		this.shippingTotal = shippingTotal;
	}

	public double getCartTotal() {
		return cartTotal;
	}

	public void setCartTotal(double cartTotal) {
		this.cartTotal = cartTotal;
	}

	public double getCartItemPromoSavings() {
		return cartItemPromoSavings;
	}

	public void setCartItemPromoSavings(double cartItemPromoSavings) {
		this.cartItemPromoSavings = cartItemPromoSavings;
	}

	public double getShippingPromoSavings() {
		return shippingPromoSavings;
	}

	public void setShippingPromoSavings(double shippingPromoSavings) {
		this.shippingPromoSavings = shippingPromoSavings;
	}

	@Override
	public String toString() {
		return "ShoppingCart [cartItemTotal=" + cartItemTotal
				+ ", cartItemPromoSavings=" + cartItemPromoSavings
				+ ", shippingTotal=" + shippingTotal
				+ ", shippingPromoSavings=" + shippingPromoSavings
				+ ", cartTotal=" + cartTotal + ", shoppingCartItemList="
				+ shoppingCartItemList + "]";
	}
}

```

## Issues

### Issue 1
Issue to fix: "Replace the `javax.enterprise` import statement with `jakarta.enterprise` "
Line number: 7
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
