# PhishGuard

PhishGuard is a robust backend service designed to protect users from SMS-based phishing attacks. It provides a secure API endpoint to receive SMS messages, analyze them for malicious URLs using Google's Web Risk API, and manage user subscriptions for the protection service.

---

## ⚠️ Important Security Disclaimer (Production Readiness)

The primary focus of this project is on the application's **core logic**. As such, certain security features have been implemented in a simplified manner for demonstration purposes and are **not ready for a production environment**.

- **Secret Management**  
  All secrets, including the database password, API key, and encryption key, are currently stored in plain text in the `.env file` or passed as environment variables.  
  **Production Recommendation**: Use a secure vault system like HashiCorp Vault, AWS Secrets Manager, or Azure Key Vault.

- **API Authorization**  
  The API uses a single, static API key for authentication.  
  **Production Recommendation**: Use OAuth 2.0, JWT, or per-client API keys with revocation.

- **Encryption Key**  
  The AES encryption key is static and stored in properties.  
  **Production Recommendation**: Manage keys via a vault/KMS and rotate periodically.

---

## Core Features

- **Event-Driven & Asynchronous Processing**: Transactional outbox + Debezium + Kafka
- **Guaranteed At-Least-Once Delivery**
- **Resilient Analysis with Retries & Dead Letter Topic (DLT)**
- **Phishing URL Detection** via Google Web Risk API
- **Subscriber-Based Protection** (START/STOP commands)
- **Secure Data Handling** with AES/GCM-256 encryption
- **API Key Authentication**
- **Comprehensive Metrics** (Micrometer → Prometheus/Grafana)
- **Automated Data Cleanup** with ShedLock
- **Fail-Closed** If Web Risk returns **inconclusive/empty** or times out after retries, 
**reject** (e.g., `REJECTED_ANALYSIS_FAILED`) to prioritize safety.

---

## Production Hardening & Roadmap (Great TODOs)

Below is a pragmatic roadmap for taking PhishGuard from demo-grade to production-grade. Items are grouped by concern and include concrete next steps.

### 1. Deploying to a Cloud-Native Platform like Kubernetes
To provide a robust and scalable foundation, we could consider deploying the application to a managed Kubernetes platform like Google Kubernetes Engine (GKE).
*   **Securely Expose the Service:** We can use a Kubernetes `Ingress` backed by a cloud load balancer to manage incoming traffic.
*   **Centralize Authentication:** Placing an API Gateway in front of our service would allow us to centralize responsibilities like enforcing authentication (e.g., using JWT or client-specific API keys instead of a single static key), managing TLS certificates, and rate limiting. This strengthens security by protecting the application at the network edge.
*   **Manage Secrets Securely:** Instead of passing secrets directly as environment variables, we could use a dedicated secret management tool like Google Secret Manager or HashiCorp Vault.
*   **Automate Scaling:** We can define separate `Deployments` for different parts of our application and use a Horizontal Pod Autoscaler (HPA) to automatically scale them based on CPU or memory usage.
### 2. Improving Performance by Splitting the Application
As traffic increases, we could consider splitting the application into two distinct microservices to allow them to scale independently and improve fault isolation.
*   **Ingestion API (The "Fast Path"):** A lightweight service whose only job is to receive an SMS, validate it, save it to the database, and create an outbox event. It would respond very quickly with a `202 Accepted` status.
*   **Analysis Worker (The "Slow Path"):** A separate, scalable group of consumers that read from the Kafka topic. This service would handle the more intensive work: checking subscriptions, extracting URLs, calling the Web Risk API, and updating the final message status.
    This separation would ensure that a slowdown in the external Web Risk API doesn't impact our ability to accept new incoming messages.
### 3. Building a Comprehensive Observability Stack
To understand how the system is performing and to diagnose issues quickly, we could build out a dedicated observability stack.
*   **Metrics and Dashboards:** We can use Prometheus to scrape the metrics already exposed by Micrometer and visualize them in Grafana. Key dashboards could track API latency (p95/p99), cache hit/miss ratios, Kafka consumer lag, and the rate of failures from the Web Risk API.
*   **Centralized Logging:** We can configure our application to output logs in a structured JSON format and ship them to a centralized logging platform like the ELK/EFK stack or GCP Cloud Logging. This makes searching and analyzing logs across all services much easier.
*   **Proactive Alerting:** We could set up alerts in Prometheus or Grafana to notify us of critical issues, such as a spike in the Dead Letter Topic (DLT), high consumer lag, or a database connection pool running out of connections.
### 4. Planning for Database Growth and Resilience
Given the potential data volume, it's wise to plan our database strategy for long-term growth.
#### Estimated Data Volume
A quick back-of-the-envelope calculation helps illustrate the scale. Assuming 1.5 million messages per day:

      |  Assumed row size |  Raw/day | Raw/year | With 60% overhead\* (indexes, WAL, bloat) |
      |------------------:|---------:|---------:|------------------------------------------:|
      |          **2 KB** |  ~3.0 GB |  ~1.1 TB |                          **~1.8 TB/year** |
      |          **4 KB** |  ~6.0 GB |  ~2.2 TB |                          **~3.5 TB/year** |
      |          **6 KB** |  ~9.0 GB |  ~3.3 TB |                          **~5.3 TB/year** |
      |          **8 KB** | ~12.0 GB |  ~4.4 TB |                          **~7.0 TB/year** |
      |         **10 KB** | ~15.0 GB |  ~5.5 TB |                          **~8.8 TB/year** |

*\*Overhead includes indexes, transaction logs (WAL), and table bloat.*
#### Recommendations
*   **High Availability:** We should use a managed database service (like Cloud SQL for PostgreSQL) configured with a primary instance and at least one read replica. This ensures the database remains available during maintenance or an outage.
*   **Backups:** Enabling Point-in-Time Recovery (PITR) is crucial for disaster recovery.
*   **Partitioning:** At this volume, partitioning our largest tables (`sms_message`, `url_analysis_cache`) is highly recommended. We could partition them by date (e.g., one partition per day). This makes data management much more efficient, as our data retention policy can be implemented by simply `DROP`ping old partitions instead of running expensive `DELETE` queries.
### 5. Evolving the Testing Strategy
To ensure reliability as the system grows more complex, we could expand our testing strategy to include:
*   **End-to-End Integration Tests:** Using tools like Testcontainers to test the full flow from API request through Kafka to the final database update.
*   **Chaos and Load Testing:** Intentionally injecting failures (e.g., making the database or Kafka unavailable) and running load tests to validate that our retry mechanisms, circuit breakers, and autoscaling rules work as expected.
### 6. Establishing Operational Best Practices
Finally, to make the system easier to manage, we could create:
*   **Runbooks:** Simple, step-by-step guides for handling common incidents, such as a spike in Kafka lag or a Web Risk API outage.
*   **Centralized Documentation:** A place that links directly to the relevant Grafana dashboards and log queries for each component, making troubleshooting faster for everyone.

---

## API Endpoints

### Receive SMS
```http
POST /api/v1/sms/incoming
X-API-Key: <your-api-key>
```

Responses:
- `202 ACCEPTED` → Message queued for analysis
- `COMMAND_PROCESSED` → START/STOP handled
- `INVALID_COMMAND` → Invalid service command

### Check Analysis Status
```http
GET /api/v1/sms/{messageId}
```

---

## Configuration

All critical settings are passed as **environment variables**:

| Property | Description |
|----------|-------------|
| `PHISHGUARD_SECURITY_API_KEY` | API key for clients (required) |
| `PHISHGUARD_ENCRYPTION_KEY` | 32-byte AES key (required) |
| `PHISHGUARD_SERVICE_PHONE_NUMBER` | Number handling START/STOP commands |
| `GOOGLE_WEBRISK_API_KEY` | API key for Google Web Risk API |
| `PHISHGUARD_MESSAGES_RETENTION_DAYS` | Days to retain SMS logs |
| `PHISHGUARD_CACHE_RETENTION_DAYS` | Days to retain cache entries |

---

## Setup & Running the Project

### Option 1: Full Environment (with Docker Compose)

This will launch **PostgreSQL, Zookeeper, Kafka, Kafka Connect, Debezium connector, and the PhishGuard app**.

**Prerequisites**:
- Docker & Docker Compose
- `.env` file in project root (secrets)

Steps:
1. Create `.env` file with required secrets (see below).
2. Run:

```sh
docker compose up --build
```

Wait until `debezium-connector-setup` finishes → your system is fully operational.

---

### Option 2: Run Everything Without Docker Compose (raw Docker commands)

Use this option if you want to run **PostgreSQL, Zookeeper, Kafka, Kafka Connect, Debezium connector, and the PhishGuard app** as individual containers without Compose.

#### 1) Create a local `.env` file (reused with `--env-file`)

```dotenv
POSTGRES_USER=admin
POSTGRES_PASSWORD=123456
PHISHGUARD_ENCRYPTION_KEY=ThisIsAValid32CharacterTestKey!!
PHISHGUARD_SECURITY_API_KEY=YOUR_SUPER_SECRET_AND_RANDOMLY_GENERATED_API_KEY
GOOGLE_WEBRISK_API_KEY=YourGoogleCloudApiKeyGoesHere
PHISHGUARD_SERVICE_PHONE_NUMBER=+15551112222
```

#### 2) Ensure `./debezium/register-postgres-connector.json` exists with the following content

```json
{
  "name": "phishguard-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "config.providers": "env",
    "config.providers.env.class": "org.apache.kafka.common.config.provider.EnvVarConfigProvider",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "${env:CONNECT_DATABASE_USER}",
    "database.password": "${env:CONNECT_DATABASE_PASSWORD}",
    "database.dbname": "phishguard_db",
    "topic.prefix": "phishguard_db_server",
    "plugin.name": "pgoutput",
    "snapshot.mode": "always",
    "table.include.list": "public.outbox_events",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.route.by.field": "topic",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.table.field.event.key": "id",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
  }
}
```

> Place this file at `./debezium/register-postgres-connector.json` relative to where you run the commands.

#### 3) One-time: create an isolated Docker network for the services

**Bash (Linux/macOS):**
```bash
docker network create phishguard || true
```

#### 4) Start the infrastructure

**PostgreSQL**
```bash
docker run -d --name postgres   --network phishguard   -p 5432:5432   --env-file .env   -e POSTGRES_DB=phishguard_db   postgres:16-alpine   postgres -c wal_level=logical
```

Wait until Postgres is ready:
```bash
docker run --rm --network phishguard postgres:16-alpine   sh -c 'until pg_isready -h postgres -U "$POSTGRES_USER" -d phishguard_db; do sleep 2; done'
```

**Zookeeper**
```bash
docker run -d --name zookeeper   --network phishguard   -p 2181:2181   zookeeper:3.9.3
```

**Kafka**
```bash
docker run -d --name kafka   --network phishguard   -p 9093:9093   -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181   -e ALLOW_PLAINTEXT_LISTENER=yes   -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,EXTERNAL://:9093   -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092,EXTERNAL://localhost:9093   -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT   -e KAFKA_CFG_INTER_BROKER_LISTENER_NAME=PLAINTEXT   -e KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true   bitnami/kafka:3.7
```

**Kafka Connect**
```bash
docker run -d --name kafka-connect   --network phishguard   -p 8083:8083   --env-file .env   -e BOOTSTRAP_SERVERS=kafka:9092   -e GROUP_ID=phishguard-connect-cluster   -e CONFIG_STORAGE_TOPIC=phishguard_connect_configs   -e OFFSET_STORAGE_TOPIC=phishguard_connect_offsets   -e STATUS_STORAGE_TOPIC=phishguard_connect_status   -e CONNECT_PLUGIN_PATH=/kafka/connect   -e CONNECT_SESSION_TIMEOUT_MS=180000   -e CONNECT_CONFIG_PROVIDERS=env   -e CONNECT_CONFIG_PROVIDERS_ENV_CLASS=org.apache.kafka.common.config.provider.EnvVarConfigProvider   -e CONNECT_DATABASE_USER=$POSTGRES_USER   -e CONNECT_DATABASE_PASSWORD=$POSTGRES_PASSWORD   debezium/connect:2.6.2.Final
```

**Wait for Kafka Connect to be healthy**
```bash
until curl -sf http://localhost:8083/ >/dev/null; do sleep 2; done
```

**Register the Debezium connector**
```bash
docker run --rm --network phishguard   -v "$(pwd)/debezium:/debezium"   curlimages/curl:8.5.0   sh -c "STATUS=$(curl -s -o /dev/null -w '%{http_code}' http://kafka-connect:8083/connectors/phishguard-outbox-connector);   if [ $STATUS -eq 404 ]; then     curl -i -X POST -H 'Accept:application/json' -H 'Content-Type:application/json'       http://kafka-connect:8083/connectors/ -d @/debezium/register-postgres-connector.json;   else echo Connector status: $STATUS; fi"
```

#### 5) Start the PhishGuard application container

Fill in datasource credentials according to your `.env` values.

```bash
docker run -d --name phishguard-app   --network phishguard   -p 8080:8080 -p 5005:5005   --env-file .env   -e JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"   -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/phishguard_db   -e SPRING_DATASOURCE_USERNAME=admin   -e SPRING_DATASOURCE_PASSWORD=123456   -e SPRING_JPA_HIBERNATE_DDL_AUTO=validate   -e SPRING_LIQUIBASE_ENABLED=true   -e SPRING_LIQUIBASE_CHANGE_LOG=classpath:db/changelog/db.changelog-master.xml   -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092   vsvj159/phishguard-app:latest
```

---

## Example `.env` File

```dotenv
# Database
POSTGRES_USER=phishguard
POSTGRES_PASSWORD=phishguard_pass
POSTGRES_DB=phishguard_db

# Application secrets
PHISHGUARD_SECURITY_API_KEY=super-secret-key
PHISHGUARD_ENCRYPTION_KEY=12345678901234567890123456789012
PHISHGUARD_SERVICE_PHONE_NUMBER=+15557654321

# Google Web Risk
GOOGLE_WEBRISK_API_KEY=your-webrisk-api-key
```

---

## How to Use

- **Subscribe a user** → Send `START` to the service number.
- **Unsubscribe a user** → Send `STOP`.
- **Send message for analysis** → Use `/api/v1/sms/incoming`.
- **Check result** → Call `/api/v1/sms/{messageId}`.

---
