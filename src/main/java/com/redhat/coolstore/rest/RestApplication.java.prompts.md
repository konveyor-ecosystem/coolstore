
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

File name: "src/main/java/com/redhat/coolstore/rest/RestApplication.java"
Source file contents:
```java
package com.redhat.coolstore.rest;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;


@ApplicationPath("/services")
public class RestApplication extends Application {

}

```

## Issues

### Issue 1
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 3
Solved example:
```diff
diff --git a/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java b/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
index 88d59ba..7d62ff8 100644
--- a/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
+++ b/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
@@ -1,7 +1,7 @@
 package org.jboss.examples.ticketmonster.rest;
 
-import javax.ws.rs.ApplicationPath;
-import javax.ws.rs.core.Application;
+import jakarta.ws.rs.ApplicationPath;
+import jakarta.ws.rs.core.Application;
 
 /**
  * A class extending {@link Application} and annotated with @ApplicationPath is the Java EE
```
### Issue 2
Issue to fix: "Replace the `javax.ws` import statement with `jakarta.ws` "
Line number: 4
Solved example:
```diff
diff --git a/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java b/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
index 88d59ba..7d62ff8 100644
--- a/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
+++ b/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
@@ -1,7 +1,7 @@
 package org.jboss.examples.ticketmonster.rest;
 
-import javax.ws.rs.ApplicationPath;
-import javax.ws.rs.core.Application;
+import jakarta.ws.rs.ApplicationPath;
+import jakarta.ws.rs.core.Application;
 
 /**
  * A class extending {@link Application} and annotated with @ApplicationPath is the Java EE
```
### Issue 3
Issue to fix: "
 JAX-RS activation is no longer necessary. You can set a root path like this but you don't have to.
 "
Line number: 7
Solved example:
```diff
diff --git a/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java b/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
index 88d59ba..7d62ff8 100644
--- a/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
+++ b/monolith/src/main/java/org/jboss/examples/ticketmonster/rest/JaxRsActivator.java
@@ -1,7 +1,7 @@
 package org.jboss.examples.ticketmonster.rest;
 
-import javax.ws.rs.ApplicationPath;
-import javax.ws.rs.core.Application;
+import jakarta.ws.rs.ApplicationPath;
+import jakarta.ws.rs.core.Application;
 
 /**
  * A class extending {@link Application} and annotated with @ApplicationPath is the Java EE
```
### Issue 4
Issue to fix: "
 JAX-RS activation is no longer necessary. You can set a root path like this but you don't have to.
 "
Line number: 8
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
