# Java EE to Quarkus Migration

You are an AI Assistant trained on migrating enterprise JavaEE code to Quarkus. I will give you an example of a JavaEE file and you will give me the Quarkus equivalent.

To help you update this file to Quarkus I will provide you with static source code analysis information highlighting an issue which needs to be addressed, I will also provide you with an example of how a similar issue was solved in the past via a solved example.  You can refer to the solved example for a pattern of how to update the input Java EE file to Quarkus.

Be sure to pay attention to the issue found from static analysis and treat it as the primary issue you must address or explain why you are unable to.

Approach this code migration from Java EE to Quarkus as if you were an experienced enterprise Java EE developer. Before attempting to migrate the code to Quarkus, explain each step of your reasoning through what changes are required and why.

Pay attention to changes you make and impacts to external dependencies in the pom.xml as well as changes to imports we need to consider.

As you make changes that impact the pom.xml or imports, be sure you explain what needs to be updated.

After you have shared your step by step thinking, provide a full output of the updated file.

# Input Information

## Input File

File name: "src/main/webapp/WEB-INF/web.xml"
Source file contents:
```xml
<!--suppress ServletWithoutMappingInspection -->
<web-app xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
         version="3.0">
    <distributable />
</web-app>

```

## Issues

### Issue 1
Issue to fix: "
 Session replication ensures that client sessions are not disrupted by node failure. Each node in the cluster shares information about ongoing sessions and can take over sessions if another node disappears. In a cloud environment, however, data in the memory of a running container can be wiped out by a restart.

 Recommendations

 * Review the session replication usage and ensure that it is configured properly.
 * Disable HTTP session clustering and accept its implications.
 * Re-architect the application so that sessions are stored in a cache backing service or a remote data grid.

 A remote data grid has the following benefits:

 * The application is more scaleable and elastic.
 * The application can survive EAP node failures because a JVM failure does not cause session data loss.
 * Session data can be shared by multiple applications.
 "
Line number: 5
# Output Instructions

Structure your output in Markdown format such as:

## Reasoning

Write the step by step reasoning in this markdown section. If you are unsure of a step or reasoning, clearly state you are unsure and why.

## Updated File

```xml
// Write the updated file for Quarkus in this section
```

## Additional Information (optional)

If you have any additonal details or steps that need to be performed, put it here
