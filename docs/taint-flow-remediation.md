# Taint Flow Remediation

## Identified Taint Flow

The rgctl security analysis identified **1 vulnerable taint flow** in the original monolith.

**Flow**: User-controlled input from REST path parameter `quantity` in `CartEndpoint` flows into
business logic without validation. A negative or zero quantity could corrupt cart state and produce
negative totals.

**Entry point**: `POST /cart/{cartId}/{itemId}/{quantity}` and `DELETE /cart/{cartId}/{itemId}/{quantity}`

## Remediation Applied

Bean Validation constraints were added to affected path parameters in `CartEndpoint.java`:

```java
// Before (vulnerable):
public ShoppingCart add(@PathParam("quantity") int quantity)

// After (remediated):
public ShoppingCart add(@PathParam("quantity") @Min(value = 1, message = "Quantity must be at least 1") int quantity)
```

Similar constraint applied to `delete()` method.

String path parameters also hardened with `@NotBlank` on `cartId` and `itemId`.

## Validation Behaviour

- `quantity < 1` → HTTP 400 Bad Request with validation error message
- `cartId` blank → HTTP 400 Bad Request
- `itemId` blank → HTTP 400 Bad Request

## Test Coverage

See `CartEndpointTest.java` for negative-case validation tests.

## References

- OWASP Input Validation Cheat Sheet
- Jakarta Bean Validation 3.0 specification
- Quarkus Validation guide: https://quarkus.io/guides/validation
