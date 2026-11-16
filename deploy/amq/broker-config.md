# AMQ Broker Configuration for CoolStore Quarkus

## Overview

The CoolStore Quarkus application uses an AMQ Broker (ActiveMQ Artemis) deployed on OpenShift
to replace the JMS Topic `java:/topic/orders` previously managed by JBoss EAP / WildFly.

## Topic Configuration

- **Address**: `topic/orders`
- **Routing type**: `multicast` (pub/sub, equivalent to JMS Topic)
- **Consumers**:
  - `order-service` consumer group → `OrderServiceConsumer` (persists orders, updates inventory)
  - `inventory-notification` consumer group → `InventoryNotificationConsumer` (low-stock alerts)

Both consumers receive **every message independently** via separate consumer group IDs — replicating
the behaviour of two separate JMS MessageDrivenBeans subscribed to the same topic.

## Dead-Letter Address

Failed messages are routed to `DLQ.topic.orders`. Monitor via the AMQ management console
(exposed via OpenShift Route on port 8161).

## Credentials

Stored in OpenShift Secret `coolstore-amqp-secret`:
- `AMQP_HOST` — broker service hostname
- `AMQP_PORT` — 5672 (AMQP protocol)
- `AMQP_USERNAME` — broker admin username
- `AMQP_PASSWORD` — broker admin password (rotate on first deployment)

## Deployment

```bash
# Apply the broker CR (requires AMQ Broker Operator installed)
oc apply -f deploy/amq/activemqartemis-cr.yaml -n coolstore-dev

# Verify broker is running
oc get activemqartemis -n coolstore-dev
oc get pods -n coolstore-dev | grep amq
```

## Monitoring Dead-Letter Queue

```bash
# Check DLQ message count via management console
oc port-forward svc/coolstore-amq-broker-console-jolokia-0-svc 8161:8161 -n coolstore-dev
# Open browser: http://localhost:8161/console
```
