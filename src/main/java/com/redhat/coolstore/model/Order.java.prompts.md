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

File name: "src/main/java/com/redhat/coolstore/model/Order.java"
Source file contents:
```java
package com.redhat.coolstore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = &#34;ORDERS&#34;)
public class Order implements Serializable {

	private static final long serialVersionUID = -1L;

	@Id
	@GeneratedValue
	private long orderId;

	private String customerName;

	private String customerEmail;

	private double orderValue;

	private double retailPrice;

	private double discount;

	private double shippingFee;

	private double shippingDiscount;

	@Column(name=&#34;TOTAL_PRICE&#34;)

	
	@OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name=&#34;ORDER_ID&#34;)
	private List&lt;OrderItem&gt; itemList = new ArrayList&lt;&gt;();

	public Order() {}

	public long getOrderId() {
		return orderId;
	}

	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public void setCustomerEmail(String customerEmail) {
		this.customerEmail = customerEmail;
	}

	public double getOrderValue() {
		return orderValue;
	}

	public void setOrderValue(double orderValue) {
		this.orderValue = orderValue;
	}

	public double getRetailPrice() {
		return retailPrice;
	}

	public void setRetailPrice(double retailPrice) {
		this.retailPrice = retailPrice;
	}

	public double getDiscount() {
		return discount;
	}

	public void setDiscount(double discount) {
		this.discount = discount;
	}

	public double getShippingFee() {
		return shippingFee;
	}

	public void setShippingFee(double shippingFee) {
		this.shippingFee = shippingFee;
	}

	public double getShippingDiscount() {
		return shippingDiscount;
	}

	public void setShippingDiscount(double shippingDiscount) {
		this.shippingDiscount = shippingDiscount;
	}

	public void setItemList(List&lt;OrderItem&gt; itemList) {
		this.itemList = itemList;
	}

	public List&lt;OrderItem&gt; getItemList() {
		return itemList;
	}

	@Override
	public String toString() {
		return &#34;Order [orderId=&#34; + orderId
				+ &#34;, customerName=&#34; + customerName
				+ &#34;, customerEmail=&#34; + customerEmail
				+ &#34;, orderValue=&#34; + orderValue
				+ &#34;, retailPrice=&#34; + retailPrice
				+ &#34;, discount=&#34; + discount
				+ &#34;, shippingFee=&#34; + shippingFee
				+ &#34;, shippingDiscount=&#34; + shippingDiscount
				+ &#34;, itemList=&#34; + itemList 
				+ &#34;]&#34;;
	}

}
```

## Issues

### incident 0
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 7
### incident 1
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 8
### incident 2
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 9
### incident 3
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 10
### incident 4
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 11
### incident 5
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 12
### incident 6
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 13
### incident 7
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 14
### incident 8
incident to fix: "Replace the `javax.persistence` import statement with `jakarta.persistence` "
Line number: 15

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

