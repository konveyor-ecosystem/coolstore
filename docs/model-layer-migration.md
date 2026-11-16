# Model & Persistence Layer Migration: Java EE → Quarkus 3.x

## Overview

This document describes the migration of the CoolStore Monolith's model and persistence layer from Java EE (JBoss/WildFly) to Quarkus 3.x.

**Migration Date:** 2026-11-16  
**Story:** 2 — Model & Persistence Layer Migration  
**Source:** `/workspace/repo/src/main/java/com/redhat/coolstore/model/`  
**Target:** `/workspace/repo/coolstore-quarkus/src/main/java/com/redhat/coolstore/model/`

---

## Key Migration Changes

### 1. Jakarta EE Namespace

All `javax.*` imports replaced with `jakarta.*` equivalents, as required by Quarkus 3.x (which is based on Jakarta EE 10).

| Old (Java EE) | New (Quarkus 3.x / Jakarta EE 10) |
|---|---|
| `javax.persistence.*` | `jakarta.persistence.*` |
| `javax.enterprise.context.*` | `jakarta.enterprise.context.*` |

### 2. Panache Entity Pattern

JPA entity classes that previously extended no base class now extend `PanacheEntityBase` from `io.quarkus.hibernate.orm.panache`. This enables the active-record pattern with built-in repository methods, eliminating the need for boilerplate DAO/repository classes.

| Class | Extends |
|---|---|
| `CatalogItemEntity` | `PanacheEntityBase` |
| `InventoryEntity` | `PanacheEntityBase` |
| `Order` | `PanacheEntityBase` |
| `OrderItem` | `PanacheEntityBase` |

### 3. Field Visibility

Panache entities use **public fields** (not private with getters/setters) for mapped columns, following the Panache convention. Getters and setters are retained for compatibility with existing service and REST layers.

### 4. Non-Entity Model Classes

Plain model classes (`Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem`) required no Panache changes — only the namespace update from `javax` to `jakarta` where applicable.

---

## Migrated Files

### Entity Classes (JPA / Panache)

| File | Table | Primary Key | Notes |
|---|---|---|---|
| `CatalogItemEntity.java` | `PRODUCT_CATALOG` | `itemId` (String) | OneToOne with `InventoryEntity` via `@PrimaryKeyJoinColumn` |
| `InventoryEntity.java` | `INVENTORY` | `itemId` (String) | Referenced by `CatalogItemEntity` |
| `Order.java` | `ORDERS` | `orderId` (long, generated) | OneToMany with `OrderItem`; `orphanRemoval = true` |
| `OrderItem.java` | `ORDER_ITEMS` | `id` (long, generated) | Owned by `Order` via `@JoinColumn(name = "ORDER_ID")` |

### Plain Model Classes (No JPA)

| File | Description |
|---|---|
| `Product.java` | DTO representing a catalog product with inventory info |
| `Promotion.java` | Simple POJO holding itemId and discount percentage |
| `ShoppingCart.java` | CDI `@Dependent`-scoped bean holding cart state and item list |
| `ShoppingCartItem.java` | Holds product, quantity, price, promo savings; converts to `OrderItem` |

---

## Database Migration (Flyway)

Flyway migration scripts were copied from the original monolith unchanged, as the schema is compatible:

| Script | Description |
|---|---|
| `V1_1__CreateSchema.sql` | Creates all tables (`PRODUCT_CATALOG`, `INVENTORY`, `ORDERS`, `ORDER_ITEMS`) |
| `V1_2__AddInitialData.sql` | Seeds initial product and inventory data |

**Location:** `src/main/resources/db/migration/`

Quarkus auto-detects Flyway scripts at this path when the `quarkus-flyway` extension is on the classpath.

---

## `application.properties` Configuration Required

The following properties should be set in `src/main/resources/application.properties` to support the persistence layer:

```properties
# DataSource
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME:coolstore}
quarkus.datasource.password=${DB_PASSWORD:coolstore}
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:coolstore}

# Hibernate ORM
quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.log.sql=false

# Flyway
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

---

## Test Coverage

Unit tests are located at:  
`src/test/java/com/redhat/coolstore/model/ModelTest.java`

| Test | Covers |
|---|---|
| `testShoppingCartAddItem` | Adding an item to the cart |
| `testShoppingCartRemoveItem` | Removing an item from the cart |
| `testShoppingCartReset` | Resetting the cart item list |
| `testShoppingCartItemToOrderItem` | Conversion from `ShoppingCartItem` to `OrderItem` |
| `testPromotionConstructor` | `Promotion` two-arg constructor |

These are pure unit tests with no Quarkus container required (`@QuarkusTest` not used), ensuring fast feedback without infrastructure dependencies.

---

## Decisions & Trade-offs

- **`PanacheEntityBase` over `PanacheEntity`**: `PanacheEntity` assumes a `Long id` primary key. Because `CatalogItemEntity` and `InventoryEntity` use a `String itemId` primary key, `PanacheEntityBase` is used to allow a custom `@Id` field.
- **Retained getters/setters**: Although Panache public fields make getters/setters optional, they are kept to avoid breaking downstream service and REST layers that call them directly.
- **`@Dependent` on `ShoppingCart`**: Replaces the previous `@SessionScoped` or similar scope. `@Dependent` allows injection wherever needed; callers that require session-level scoping should wrap it in a session-scoped producer if necessary.
