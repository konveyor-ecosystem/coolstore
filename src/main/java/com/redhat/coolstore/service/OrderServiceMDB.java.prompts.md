
<s>[INST] <<SYS>>
You are an AI Assistant trained on migrating enterprise JavaEE code to Quarkus.
<</SYS>>

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
# Input Information

## Input File

File name: "src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java"
Source file contents:
```java
package com.redhat.coolstore.service;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@MessageDriven(name = "OrderServiceMDB", activationConfig = {
	@ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "topic/orders"),
	@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
	@ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
public class OrderServiceMDB implements MessageListener { 

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Override
	public void onMessage(Message rcvMessage) {
		System.out.println("\nMessage recd !");
		TextMessage msg = null;
		try {
				if (rcvMessage instanceof TextMessage) {
						msg = (TextMessage) rcvMessage;
						String orderStr = msg.getBody(String.class);
						System.out.println("Received order: " + orderStr);
						Order order = Transformers.jsonToOrder(orderStr);
						System.out.println("Order object is " + order);
						orderService.save(order);
						order.getItemList().forEach(orderItem -> {
							catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
						});
				}
		} catch (JMSException e) {
			throw new RuntimeException(e);
		}
	}

}
```

## Issues

### Issue 1
Issue to fix: "Replace the `javax.ejb` import statement with `jakarta.ejb` "
Line number: 3
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 2
Issue to fix: "Replace the `javax.ejb` import statement with `jakarta.ejb` "
Line number: 4
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 3
Issue to fix: "Replace the `javax.inject` import statement with `jakarta.inject` "
Line number: 5
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/bmt/ManagedComponent.java b/src/main/java/org/jboss/as/quickstarts/bmt/ManagedComponent.java
index 01916cb..00f1bc4 100644
--- a/src/main/java/org/jboss/as/quickstarts/bmt/ManagedComponent.java
+++ b/src/main/java/org/jboss/as/quickstarts/bmt/ManagedComponent.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.bmt;
 
-import javax.ejb.Stateless;
-import javax.inject.Inject;
-import javax.persistence.PersistenceContext;
-import javax.transaction.Status;
-import javax.transaction.UserTransaction;
-import javax.ejb.TransactionManagement;
-import javax.ejb.TransactionManagementType;
-import javax.persistence.EntityManager;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
+import jakarta.inject.Inject;
+import jakarta.transaction.Transactional;
 
 /**
  * A session bean for updating a database table within a JTA transaction
@@ -36,22 +32,8 @@ import javax.persistence.EntityManager;
  * for injection into other components (eg the {@linkplain TransactionServlet}): - it becomes eligible for other components to
  * be injected; - it becomes eligible for Container Managed Transactions (although this example does not use CMT)
  */
-@Stateless
-@TransactionManagement(TransactionManagementType.BEAN)
-// tell the container not to manage transactions
+@ApplicationScoped
 public class ManagedComponent {
-    /**
-     * Ask the container to inject an Entity Manager (EM). As a consequence the EM will be automatically enlisted into any new
-     * transactions started by the managed component.
-     *
-     */
-    @PersistenceContext
-    private EntityManager entityManager;
-
-    // Inject a UserTransaction for manual transaction demarcation.
-    @Inject
-    private UserTransaction userTransaction;
-
     // Inject a utility class for updating JPA entities
     @Inject
     private UnManagedComponent helper;
@@ -67,6 +49,7 @@ public class ManagedComponent {
      * @return a string representing the keys values pairs if no key is provided, or the key value pair if one is provided, or
      *         the error if anything went wrong
      */
+    @Transactional
     public String updateKeyValueDatabase(String key, String value) {
         /*
          * Since this is a session bean method we are guaranteed to be thread safe so it is OK to use the injected Entity
@@ -74,30 +57,15 @@ public class ManagedComponent {
          * method call
          */
         try {
-            userTransaction.begin();
-
+            Log.info("Beginning container-managed transaction");
             /*
              * Since the bean is managed by the container the Entity Manager (EM) and JTA transaction manager (TM) cooperate so
              * there is no need to tell the EM about the transaction. Compare this with the UnManagedComponent class where the
              * developer is managing the EM himself and therefore must explicitly tell the EM to join the transaction
              */
-            String result = helper.updateKeyValueDatabase(entityManager, key, value);
-
-            userTransaction.commit();
-
-            return result;
+            return helper.updateKeyValueDatabaseLogic(key, value);
         } catch (Exception e) {
             return e.getMessage();
-        } finally {
-            /*
-             * Clean up
-             */
-            try {
-                if (userTransaction.getStatus() == Status.STATUS_ACTIVE)
-                    userTransaction.rollback();
-            } catch (Throwable e) {
-                // ignore
-            }
         }
     }
 }
```
### Issue 4
Issue to fix: "Replace the `javax.jms` import statement with `jakarta.jms` "
Line number: 6
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java b/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java
index 7088d9c..93108f0 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java
@@ -16,27 +16,21 @@
  */
 package org.jboss.as.quickstarts.cmt.ejb;
 
-import java.rmi.RemoteException;
 import java.util.List;
 
-import javax.ejb.EJBException;
-import javax.ejb.Stateless;
-import javax.ejb.TransactionAttribute;
-import javax.ejb.TransactionAttributeType;
-import javax.inject.Inject;
-import javax.jms.JMSException;
-import javax.naming.NamingException;
-import javax.persistence.EntityManager;
-import javax.persistence.PersistenceContext;
-import javax.transaction.HeuristicMixedException;
-import javax.transaction.HeuristicRollbackException;
-import javax.transaction.NotSupportedException;
-import javax.transaction.RollbackException;
-import javax.transaction.SystemException;
+import jakarta.inject.Inject;
+import jakarta.enterprise.context.ApplicationScoped;
+import jakarta.persistence.EntityExistsException;
+import jakarta.persistence.EntityManager;
+import jakarta.persistence.PersistenceContext;
+import jakarta.transaction.RollbackException;
+import jakarta.transaction.Transactional;
+import jakarta.transaction.Transactional.TxType;
 
+import org.hibernate.exception.ConstraintViolationException;
 import org.jboss.as.quickstarts.cmt.model.Customer;
 
-@Stateless
+@ApplicationScoped
 public class CustomerManagerEJB {
 
     @PersistenceContext
@@ -48,9 +42,17 @@ public class CustomerManagerEJB {
     @Inject
     private InvoiceManagerEJB invoiceManager;
 
-    @TransactionAttribute(TransactionAttributeType.REQUIRED)
-    public void createCustomer(String name) throws RemoteException, JMSException {
-        logMessageManager.logCreateCustomer(name);
+    @Transactional(TxType.REQUIRED)
+    public void createCustomer(String name) throws RollbackException, EntityExistsException {
+        try {
+            logMessageManager.logCreateCustomer(name);
+        } catch (RollbackException e) {
+            if (e.getCause() instanceof ConstraintViolationException) {
+                throw new EntityExistsException(e.getCause());
+            } else {
+                throw e;
+            }
+        }
 
         Customer c1 = new Customer();
         c1.setName(name);
@@ -62,7 +64,7 @@ public class CustomerManagerEJB {
         // the invoice is not delivered when we cause an EJBException
         // after the fact but before the transaction is committed.
         if (!nameIsValid(name)) {
-            throw new EJBException("Invalid name: customer names should only contain letters & '-'");
+            throw new IllegalArgumentException("Invalid name: customer names should only contain letters & '-'");
         }
     }
 
@@ -74,16 +76,8 @@ public class CustomerManagerEJB {
      * List all the customers.
      *
      * @return
-     * @throws NamingException
-     * @throws NotSupportedException
-     * @throws SystemException
-     * @throws SecurityException
-     * @throws IllegalStateException
-     * @throws RollbackException
-     * @throws HeuristicMixedException
-     * @throws HeuristicRollbackException
      */
-    @TransactionAttribute(TransactionAttributeType.NEVER)
+    @Transactional(TxType.NEVER)
     @SuppressWarnings("unchecked")
     public List<Customer> listCustomers() {
         return entityManager.createQuery("select c from Customer c").getResultList();
```
### Issue 5
Issue to fix: "Replace the `javax.jms` import statement with `jakarta.jms` "
Line number: 7
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 6
Issue to fix: "Replace the `javax.jms` import statement with `jakarta.jms` "
Line number: 8
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 7
Issue to fix: "Replace the `javax.jms` import statement with `jakarta.jms` "
Line number: 9
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 8
Issue to fix: "Enterprise Java Beans (EJBs) are not supported in Quarkus. CDI must be used.
 Please replace the `@MessageDriven` annotation with a CDI scope annotation like `@ApplicationScoped`."
Line number: 14
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 9
Issue to fix: "The `destinationLookup` property can be migrated by annotating a message handler method (potentially `onMessage`) with the
 `org.eclipse.microprofile.reactive.messaging.Incoming` annotation, indicating the name of the queue as a value:
 
 Before:
 ```
 @MessageDriven(name = "HelloWorldQueueMDB", activationConfig = 
 public class MessageListenerImpl implements MessageListener 
 }}
 ```
 
 After:
 ```
 public class MessageListenerImpl implements MessageListener 
 }}
 ```"
Line number: 15
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 10
Issue to fix: "The `destinationLookup` property can be migrated by annotating a message handler method (potentially `onMessage`) with the
 `org.eclipse.microprofile.reactive.messaging.Incoming` annotation, indicating the name of the queue as a value:
 
 Before:
 ```
 @MessageDriven(name = "HelloWorldQueueMDB", activationConfig = 
 public class MessageListenerImpl implements MessageListener 
 }}
 ```
 
 After:
 ```
 public class MessageListenerImpl implements MessageListener 
 }}
 ```"
Line number: 16
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 11
Issue to fix: "The `destinationLookup` property can be migrated by annotating a message handler method (potentially `onMessage`) with the
 `org.eclipse.microprofile.reactive.messaging.Incoming` annotation, indicating the name of the queue as a value:
 
 Before:
 ```
 @MessageDriven(name = "HelloWorldQueueMDB", activationConfig = 
 public class MessageListenerImpl implements MessageListener 
 }}
 ```
 
 After:
 ```
 public class MessageListenerImpl implements MessageListener 
 }}
 ```"
Line number: 17
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 12
Issue to fix: "References to JavaEE/JakartaEE JMS elements should be removed and replaced with their Quarkus SmallRye/Microprofile equivalents."
Line number: 6
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java b/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java
index 7088d9c..93108f0 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/ejb/CustomerManagerEJB.java
@@ -16,27 +16,21 @@
  */
 package org.jboss.as.quickstarts.cmt.ejb;
 
-import java.rmi.RemoteException;
 import java.util.List;
 
-import javax.ejb.EJBException;
-import javax.ejb.Stateless;
-import javax.ejb.TransactionAttribute;
-import javax.ejb.TransactionAttributeType;
-import javax.inject.Inject;
-import javax.jms.JMSException;
-import javax.naming.NamingException;
-import javax.persistence.EntityManager;
-import javax.persistence.PersistenceContext;
-import javax.transaction.HeuristicMixedException;
-import javax.transaction.HeuristicRollbackException;
-import javax.transaction.NotSupportedException;
-import javax.transaction.RollbackException;
-import javax.transaction.SystemException;
+import jakarta.inject.Inject;
+import jakarta.enterprise.context.ApplicationScoped;
+import jakarta.persistence.EntityExistsException;
+import jakarta.persistence.EntityManager;
+import jakarta.persistence.PersistenceContext;
+import jakarta.transaction.RollbackException;
+import jakarta.transaction.Transactional;
+import jakarta.transaction.Transactional.TxType;
 
+import org.hibernate.exception.ConstraintViolationException;
 import org.jboss.as.quickstarts.cmt.model.Customer;
 
-@Stateless
+@ApplicationScoped
 public class CustomerManagerEJB {
 
     @PersistenceContext
@@ -48,9 +42,17 @@ public class CustomerManagerEJB {
     @Inject
     private InvoiceManagerEJB invoiceManager;
 
-    @TransactionAttribute(TransactionAttributeType.REQUIRED)
-    public void createCustomer(String name) throws RemoteException, JMSException {
-        logMessageManager.logCreateCustomer(name);
+    @Transactional(TxType.REQUIRED)
+    public void createCustomer(String name) throws RollbackException, EntityExistsException {
+        try {
+            logMessageManager.logCreateCustomer(name);
+        } catch (RollbackException e) {
+            if (e.getCause() instanceof ConstraintViolationException) {
+                throw new EntityExistsException(e.getCause());
+            } else {
+                throw e;
+            }
+        }
 
         Customer c1 = new Customer();
         c1.setName(name);
@@ -62,7 +64,7 @@ public class CustomerManagerEJB {
         // the invoice is not delivered when we cause an EJBException
         // after the fact but before the transaction is committed.
         if (!nameIsValid(name)) {
-            throw new EJBException("Invalid name: customer names should only contain letters & '-'");
+            throw new IllegalArgumentException("Invalid name: customer names should only contain letters & '-'");
         }
     }
 
@@ -74,16 +76,8 @@ public class CustomerManagerEJB {
      * List all the customers.
      *
      * @return
-     * @throws NamingException
-     * @throws NotSupportedException
-     * @throws SystemException
-     * @throws SecurityException
-     * @throws IllegalStateException
-     * @throws RollbackException
-     * @throws HeuristicMixedException
-     * @throws HeuristicRollbackException
      */
-    @TransactionAttribute(TransactionAttributeType.NEVER)
+    @Transactional(TxType.NEVER)
     @SuppressWarnings("unchecked")
     public List<Customer> listCustomers() {
         return entityManager.createQuery("select c from Customer c").getResultList();
```
### Issue 13
Issue to fix: "References to JavaEE/JakartaEE JMS elements should be removed and replaced with their Quarkus SmallRye/Microprofile equivalents."
Line number: 7
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 14
Issue to fix: "References to JavaEE/JakartaEE JMS elements should be removed and replaced with their Quarkus SmallRye/Microprofile equivalents."
Line number: 8
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
### Issue 15
Issue to fix: "References to JavaEE/JakartaEE JMS elements should be removed and replaced with their Quarkus SmallRye/Microprofile equivalents."
Line number: 9
Solved example:
```diff
diff --git a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
index 2a80846..5165d25 100644
--- a/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
+++ b/src/main/java/org/jboss/as/quickstarts/cmt/mdb/HelloWorldMDB.java
@@ -16,14 +16,10 @@
  */
 package org.jboss.as.quickstarts.cmt.mdb;
 
-import java.util.logging.Logger;
+import org.eclipse.microprofile.reactive.messaging.Incoming;
 
-import javax.ejb.ActivationConfigProperty;
-import javax.ejb.MessageDriven;
-import javax.jms.JMSException;
-import javax.jms.Message;
-import javax.jms.MessageListener;
-import javax.jms.TextMessage;
+import io.quarkus.logging.Log;
+import jakarta.enterprise.context.ApplicationScoped;
 
 /**
  * <p>
@@ -33,28 +29,11 @@ import javax.jms.TextMessage;
  * @author Serge Pagop (spagop@redhat.com)
  *
  */
-@MessageDriven(name = "HelloWorldMDB", activationConfig = {
-    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
-    @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/CMTQueue"),
-    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge") })
-public class HelloWorldMDB implements MessageListener {
+@ApplicationScoped
+public class HelloWorldMDB {
 
-    private static final Logger LOGGER = Logger.getLogger(HelloWorldMDB.class.toString());
-
-    /**
-     * @see MessageListener#onMessage(Message)
-     */
-    public void onMessage(Message rcvMessage) {
-        TextMessage msg = null;
-        try {
-            if (rcvMessage instanceof TextMessage) {
-                msg = (TextMessage) rcvMessage;
-                LOGGER.info("Received Message: " + msg.getText());
-            } else {
-                LOGGER.warning("Message of wrong type: " + rcvMessage.getClass().getName());
-            }
-        } catch (JMSException e) {
-            throw new RuntimeException(e);
-        }
+    @Incoming("CMTQueue")
+    public void onMessage(String msg) {
+        Log.info("Received Message: " + msg);
     }
 }
```
# Output Instructions

Structure your output in Markdown format such as:

## Reasoning

Write the step by step reasoning in this markdown section. If you are unsure of a step or reasoning, clearly state you are unsure and why.

## Updated File

```java
// Write the updated file for Quarkus in this section. If the file should be removed, make the content of the updated file a comment explaining it should be removed.
```

## Additional Information (optional)

If you have any additonal details or steps that need to be performed, put it here

[/INST]
