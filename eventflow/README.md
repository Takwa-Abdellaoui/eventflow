# EventFlow — Microservices Platform

![CI/CD](https://github.com/YOUR_USERNAME/eventflow/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.6-black?logo=apachekafka)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)
![License](https://img.shields.io/badge/license-MIT-green)

> Plateforme e-commerce event-driven construite avec des microservices Spring Boot, Apache Kafka, Docker et un pipeline CI/CD GitHub Actions.

---

## Architecture

```
Client
  │
  ▼
┌─────────────────────────────────────────────────────┐
│                    API Gateway :8080                 │
└──────┬──────────────┬──────────────┬────────────────┘
       │              │              │
       ▼              ▼              ▼
  Order Service  Inventory Svc  Payment Svc   Notification Svc
    :8081           :8082          :8083           :8084
  orders_db      inventory_db   payments_db     (consumer only)
       │              │              │
       └──────────────┴──────────────┘
                      │
               Apache Kafka :9092
               ┌──────────────────┐
               │  order.created   │
               │inventory.reserved│
               │payment.processed │
               │  *.dlt (retry)   │
               └──────────────────┘
```

## Flux d'une commande

```
POST /api/v1/orders
       │
       ▼
  Order Service ──── order.created ────► Inventory Service
       │                                       │
       │                              inventory.reserved
       │                                       │
       │◄─────────────────────────────────────►│
       │                                       │
       │                              payment.processed
       │                                       │
       ▼                                       ▼
  CONFIRMED ◄──── payment.processed ◄── Payment Service
                                               │
                                               ▼
                                      Notification Service
                                       (log + email ready)
```

## Stack technique

| Couche | Technologie |
|--------|-------------|
| Backend | Java 17, Spring Boot 3.2, Spring Data JPA |
| Messaging | Apache Kafka 7.6, Spring Kafka, RetryableTopic, DLT |
| Base de données | PostgreSQL 16 (une DB par service) |
| Conteneurisation | Docker, Docker Compose |
| CI/CD | GitHub Actions |
| Monitoring | Prometheus, Grafana, Spring Actuator |
| Tests | JUnit 5, Mockito, AssertJ |

## Lancement rapide

### Prérequis
- Docker Desktop
- Java 17+
- Maven 3.9+

### Démarrer tout le projet

```bash
git clone https://github.com/YOUR_USERNAME/eventflow.git
cd eventflow
docker compose up --build
```

### URLs disponibles

| Service | URL | Description |
|---------|-----|-------------|
| Order Service | http://localhost:8081 | API commandes |
| Inventory Service | http://localhost:8082 | API inventaire |
| Payment Service | http://localhost:8083 | API paiements |
| Notification Service | http://localhost:8084 | Consumer Kafka |
| Kafka UI | http://localhost:8090 | Interface Kafka |
| Prometheus | http://localhost:9090 | Métriques |
| Grafana | http://localhost:3000 | Dashboards (admin/eventflow) |

## API Reference

### Créer une commande

```bash
POST http://localhost:8081/api/v1/orders
Content-Type: application/json

{
  "customerId": "CUST-001",
  "productId": "PROD-001",
  "quantity": 2,
  "totalAmount": 2599.98
}
```

### Suivre une commande

```bash
GET http://localhost:8081/api/v1/orders/{id}
GET http://localhost:8081/api/v1/orders/customer/{customerId}
```

### Produits disponibles (pré-chargés)

| ID | Produit | Stock | Prix |
|----|---------|-------|------|
| PROD-001 | Laptop Pro 15 | 50 | 1299.99€ |
| PROD-002 | Wireless Headphones | 200 | 149.99€ |
| PROD-003 | Mechanical Keyboard | 100 | 89.99€ |
| PROD-004 | 4K Monitor | 30 | 549.99€ |
| PROD-005 | USB-C Hub | 5 | 49.99€ |

### Lancer les tests

```bash
cd order-service && mvn test
cd ../inventory-service && mvn test
cd ../payment-service && mvn test
```

## Structure du projet

```
eventflow/
├── order-service/          # Gestion des commandes
├── inventory-service/      # Gestion du stock
├── payment-service/        # Traitement des paiements
├── notification-service/   # Notifications (Kafka consumer)
├── monitoring/
│   ├── prometheus/         # prometheus.yml
│   └── grafana/            # Dashboards
├── .github/
│   └── workflows/
│       └── ci.yml          # Pipeline CI/CD
└── docker-compose.yml      # Orchestration complète
```

## CI/CD Pipeline

Le pipeline GitHub Actions se déclenche à chaque push sur `main` :

```
push → Test → Build → Docker Build → DockerHub Push
```

### Configurer les secrets GitHub

```
Settings → Secrets → Actions → New repository secret

DOCKERHUB_USERNAME  →  votre username DockerHub
DOCKERHUB_TOKEN     →  Account Settings → Security → Access Token
```

## Auteur

**Yasmine** — [GitHub](https://github.com/YOUR_USERNAME)

---

*Projet réalisé pour approfondir les compétences DevOps : microservices, event-driven architecture, containerisation et CI/CD.*
