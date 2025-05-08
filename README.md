# Coolstore - Quarkus Migration

This project is a migration of the Coolstore Java EE application to Quarkus.

## Migration Overview

The original Java EE application has been migrated to Quarkus with the following changes:

1. Replaced Java EE APIs with Jakarta EE and Quarkus extensions:
   - JAX-RS → RESTEasy Reactive
   - JPA → Hibernate ORM with Panache
   - CDI → Quarkus ArC
   - EJB → CDI beans with appropriate scopes
   - JMS → Reactive Messaging with AMQP

2. Database migrations:
   - Flyway for database schema management
   - PostgreSQL as the database

3. Messaging:
   - Replaced JMS with Reactive Messaging
   - AMQP for message transport

## Running the Application

### Prerequisites

- JDK 17+
- Maven 3.8+
- PostgreSQL database

### Development Mode

```bash
# Start PostgreSQL (if not already running)
docker run -it --rm --name postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=coolstore -p 5432:5432 postgres:14

# Run the application in dev mode
./mvnw compile quarkus:dev
```

This will start the application in development mode with hot reload enabled.

### Production Mode

```bash
# Package the application
./mvnw package

# Run the application
java -jar target/quarkus-app/quarkus-run.jar
```

## API Endpoints

- Products API: `/services/products`
- Cart API: `/services/cart/{cartId}`
- Orders API: `/services/orders`

## Health Check

- Health check endpoint: `/health`

## Configuration

The application configuration is in `src/main/resources/application.properties`. Key configurations include:

- Database connection details
- AMQP messaging configuration
- Logging settings

## Migration Notes

### Java EE to Jakarta EE

- Updated all `javax.*` imports to `jakarta.*`
- Replaced EJB annotations with CDI scopes

### JMS to Reactive Messaging

- Replaced JMS with Reactive Messaging
- Updated message handling to use reactive patterns

### Database Access

- Configured Flyway for database migrations
- Updated JPA entities to work with Hibernate ORM

### REST Endpoints

- Updated JAX-RS endpoints to use RESTEasy Reactive
- Maintained the same API paths for backward compatibility