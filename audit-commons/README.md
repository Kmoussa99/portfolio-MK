# 🔍 audit-commons — `@AuditLogOrange`

**Module Java Spring Boot réutilisable** qui trace automatiquement **qui fait quoi, quand, et le résultat** dans votre application, et envoie le tout vers **Elasticsearch (ELK)**.

> 📦 **Librairie (JAR)** — pas un microservice. Se branche sur n'importe quel projet Spring Boot 3.x.

---

## ✨ Ce que ça fait

Quand vous annotez une méthode avec `@AuditLogOrange` :

```json
{
  "timestamp": "2026-03-05T14:32:10.400Z",
  "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "username": "moussa.kone",
  "email": "moussa.kone@orange.com",
  "roles": ["admin", "subscriber"],
  "action": "CREATE_APPLICATION",
  "httpMethod": "POST",
  "requestUri": "/api/applications",
  "methodName": "create",
  "controllerClass": "ApplicationController",
  "parameters": { "req": "ApplicationCreateRequest(name=MyApp)" },
  "result": "SUCCESS",
  "httpStatus": 201,
  "durationMs": 56,
  "sourceService": "developer-portal",
  "clientIp": "192.168.1.100",
  "traceId": "a1b2c3d4-e5f6-..."
}
```

**👤 QUI** → `userId`, `username`, `email`, `roles` (extrait automatiquement du JWT)
**🎯 QUOI** → `action`, `httpMethod`, `requestUri`, `parameters`
**📊 RÉSULTAT** → `result` (SUCCESS/FAILURE), `httpStatus`, `errorMessage`, `durationMs`
**📍 OÙ** → `sourceService`, `clientIp`, `traceId`

---

## 🚀 Installation (3 étapes)

### Étape 1 — Ajouter la dépendance Maven

```xml
<dependency>
    <groupId>com.orange</groupId>
    <artifactId>audit-commons</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Sans Nexus ?** Builder le JAR localement d'abord :
> ```bash
> cd audit-commons && mvn clean install -DskipTests
> ```

### Étape 2 — Configurer `application.yaml`

```yaml
# ═══ Configuration minimale ═══
spring:
  application:
    name: mon-service  # Apparaîtra dans sourceService

audit:
  elasticsearch:
    url: https://localhost:9200        # URL Elasticsearch
    enabled: true                      # true pour activer l'envoi vers ES
    username: elastic                  # Basic Auth username
    password: elastic123               # Basic Auth password
    api-key:                           # Laisser vide si Basic Auth (remplir en prod)
    insecure-tls: true                 # true = skip TLS verify (dev), false = strict (prod)
    index-prefix: audit-logs           # Préfixe index ES → audit-logs-2026.03.05

  # URI à exclure de l'audit (séparées par des virgules)
  excluded-uri-prefixes: /api/auth/,/actuator/,/swagger,/v3/api-docs
```

### Étape 3 — Ajouter `logback-spring.xml`

Créez `src/main/resources/logback-spring.xml` :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="spring.application.name"
                    source="spring.application.name" defaultValue="my-service"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Inclure la config audit (fichier local + Logstash) -->
    <include resource="logback-audit.xml"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**C'est tout.** L'auto-configuration Spring Boot fait le reste.

---

## 🎯 Utilisation

### Annoter vos méthodes

```java
import com.orange.audit.annotation.AuditLogOrange;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @AuditLogOrange(action = "CREATE_PRODUCT")
    @PostMapping
    public ResponseEntity<Product> create(@RequestBody ProductRequest req) {
        // ... votre code
    }

    @AuditLogOrange(action = "LIST_PRODUCTS")
    @GetMapping
    public ResponseEntity<List<Product>> list() {
        // ... votre code
    }

    @AuditLogOrange(action = "DELETE_PRODUCT")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        // ... votre code
    }

    // Masquer les paramètres sensibles
    @AuditLogOrange(action = "CREATE_USER", sensitiveParams = {"password", "secret"})
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody UserRequest req) {
        // password sera loggé comme "***MASKED***"
    }
}
```

### Convention de nommage des actions

| Préfixe | Signification | Exemple |
|---------|---------------|---------|
| `CREATE_` | Création | `CREATE_APPLICATION` |
| `UPDATE_` | Modification | `UPDATE_USER` |
| `DELETE_` | Suppression | `DELETE_SUBSCRIPTION` |
| `LIST_` | Liste / recherche | `LIST_PRODUCTS` |
| `GET_` | Lecture unitaire | `GET_PRODUCT` |
| `SYNC_` | Synchronisation | `SYNC_CATALOG_FROM_WSO2` |
| `ASSIGN_` | Attribution | `ASSIGN_ROLES` |

---

## 👤 Comment le "qui" est extrait

L'aspect extrait automatiquement l'identité depuis le `SecurityContext` Spring Security. Il supporte **3 scénarios** :

| Scénario | Principal | Champs extraits |
|----------|-----------|-----------------|
| **JWT Keycloak** (OAuth2 Resource Server) | `Jwt` | `sub`, `preferred_username`, `email`, `realm_access.roles` |
| **Token custom** (filtre + `setDetails(Map)`) | `String` + `details(Map)` | `sub`, `preferred_username`, `email`, `realm_access.roles` ou `roles` |
| **Basic Auth / autre** | `String` | `authentication.getName()` + `GrantedAuthority` comme rôles |
| **Pas authentifié** | — | `userId=anonymous`, `username=anonymous` |

### Pour que ça marche avec votre filtre custom

Si vous avez un filtre custom qui valide le JWT, stockez les claims dans `authentication.setDetails()` :

```java
// Dans votre filtre custom
Map<String, Object> jwtClaims = decodeJwt(token);  // sub, email, preferred_username, realm_access...

UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
    username, null, authorities
);
auth.setDetails(jwtClaims);  // ← L'audit les récupérera automatiquement

SecurityContextHolder.getContext().setAuthentication(auth);
```

---

## 🔎 Requêtes Elasticsearch

### Vérifier que ça marche

```bash
# Vérifier l'index
curl -sk -u elastic:elastic123 'https://localhost:9200/_cat/indices/audit-logs-*?v'

# Voir les derniers logs
curl -sk -u elastic:elastic123 'https://localhost:9200/audit-logs-*/_search?pretty&size=5'
```

### Chercher par utilisateur (QUI fait quoi)

```bash
curl -sk -u elastic:elastic123 'https://localhost:9200/audit-logs-*/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"query":{"match":{"username":"moussa.kone"}},"sort":[{"timestamp":"desc"}],"size":20}'
```

### Chercher par action

```bash
curl -sk -u elastic:elastic123 'https://localhost:9200/audit-logs-*/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"query":{"term":{"action.keyword":"CREATE_APPLICATION"}}}'
```

### Qui a fait quoi aujourd'hui (timeline)

```bash
curl -sk -u elastic:elastic123 'https://localhost:9200/audit-logs-*/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{
    "query":{"range":{"timestamp":{"gte":"now-24h"}}},
    "sort":[{"timestamp":"desc"}],
    "size":50,
    "_source":["timestamp","username","email","action","httpMethod","requestUri","result","httpStatus"]
  }'
```

### Toutes les actions en échec

```bash
curl -sk -u elastic:elastic123 'https://localhost:9200/audit-logs-*/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"query":{"term":{"result.keyword":"FAILURE"}},"sort":[{"timestamp":"desc"}]}'
```

### Comptage des actions par utilisateur (agrégation)

```bash
curl -sk -u elastic:elastic123 'https://localhost:9200/audit-logs-*/_search?pretty' \
  -H 'Content-Type: application/json' \
  -d '{
    "size":0,
    "aggs":{
      "par_user":{
        "terms":{"field":"username.keyword","size":20},
        "aggs":{"actions":{"terms":{"field":"action.keyword"}}}
      }
    }
  }'
```

### Dans Kibana (Dev Tools)

Accéder à `http://localhost:5601` → **Dev Tools**, puis :

```json
GET audit-logs-*/_search
{
  "query": { "match": { "username": "moussa.kone" } },
  "sort": [{ "timestamp": "desc" }],
  "size": 50
}
```

---

## ⚙️ Configuration avancée

### Par environnement

| Propriété | Dev | Prod |
|-----------|-----|------|
| `audit.elasticsearch.url` | `https://localhost:9200` | `https://elasticsearch.internal:9200` |
| `audit.elasticsearch.username` | `elastic` | `${ES_USERNAME}` |
| `audit.elasticsearch.password` | `elastic123` | `${ES_PASSWORD}` |
| `audit.elasticsearch.api-key` | *(vide)* | `${ES_API_KEY}` |
| `audit.elasticsearch.insecure-tls` | `true` | `false` |
| `audit.elasticsearch.index-prefix` | `audit-logs` | `audit-logs-prod` |
| `audit.excluded-uri-prefixes` | `/api/auth/,/actuator/,...` | `/api/auth/,/actuator/,...` |

### Docker Compose

```yaml
environment:
  AUDIT_ELASTICSEARCH_URL: https://host.docker.internal:9200
  AUDIT_ELASTICSEARCH_ENABLED: "true"
  AUDIT_ELASTICSEARCH_USERNAME: elastic
  AUDIT_ELASTICSEARCH_PASSWORD: elastic123
  AUDIT_ELASTICSEARCH_API_KEY: ""
  AUDIT_ELASTICSEARCH_INSECURE_TLS: "true"
```

### Kubernetes (ConfigMap)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: audit-config
data:
  AUDIT_ELASTICSEARCH_URL: "https://elasticsearch.svc:9200"
  AUDIT_ELASTICSEARCH_ENABLED: "true"
  AUDIT_ELASTICSEARCH_INSECURE_TLS: "false"
```

---

## 📁 Structure du module

```
audit-commons/
├── pom.xml                          # Dépendances (spring-aop, security, jackson)
├── README.md                        # Ce fichier
├── AUDIT_QUERIES.md                 # Requêtes Elasticsearch détaillées
└── src/main/
    ├── java/com/orange/audit/
    │   ├── annotation/
    │   │   └── AuditLogOrange.java  # L'annotation @AuditLogOrange
    │   ├── aspect/
    │   │   └── AuditLogAspect.java  # Intercepteur AOP (capture qui/quoi/résultat)
    │   ├── config/
    │   │   └── AuditAutoConfiguration.java  # Auto-config Spring Boot
    │   ├── model/
    │   │   └── AuditLogEntry.java   # Modèle de données (ce qui est envoyé vers ES)
    │   └── service/
    │       └── AuditLogService.java # Envoi vers ES (HTTPS + auth) + fichier local
    └── resources/
        ├── logback-audit.xml        # Config Logback (Logstash + fichier local)
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## 🔄 Intégration dans un nouveau projet

### 1. Copier le dossier `audit-commons/` dans votre workspace

### 2. Builder le JAR

```bash
cd audit-commons && mvn clean install -DskipTests
```

### 3. Ajouter la dépendance dans votre `pom.xml`

```xml
<dependency>
    <groupId>com.orange</groupId>
    <artifactId>audit-commons</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 4. Si Docker multi-stage build

Dans votre `Dockerfile` :

```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Builder audit-commons d'abord
COPY audit-commons/pom.xml /audit-commons/pom.xml
COPY audit-commons/src /audit-commons/src
RUN mvn -f /audit-commons/pom.xml install -DskipTests -B -q

# Puis votre projet
COPY mon-projet/pom.xml .
RUN mvn dependency:go-offline -B
COPY mon-projet/src src
RUN mvn package -DskipTests -B
```

Et dans `docker-compose.yml`, mettre le `build context` à la racine :

```yaml
services:
  mon-service:
    build:
      context: .                    # Racine du workspace (pour accéder à audit-commons/)
      dockerfile: mon-projet/Dockerfile
```

### 5. Annoter vos méthodes et c'est terminé ✅

---

## ❓ FAQ

**Q: Est-ce que ça ralentit mon API ?**
Non. L'envoi vers Elasticsearch est **asynchrone** (`@Async`). La latence ajoutée est < 1ms.

**Q: Que se passe-t-il si Elasticsearch est down ?**
Un warning est loggé, et les audit logs sont toujours écrits dans le **fichier local** (`logs/audit-logs.json`).

**Q: Est-ce que ça marche sans Spring Security ?**
Oui, mais `userId` et `username` seront `anonymous`. L'annotation fonctionne quand même pour tracer les actions.

**Q: Comment ajouter des champs custom ?**
Étendre `AuditLogEntry` avec de nouveaux champs, et modifier `AuditLogAspect.auditMethod()`.

**Q: Compatible Spring Boot 2.x ?**
Non. Ce module utilise Spring Boot 3.x / Jakarta EE (pas javax). Pour Spring Boot 2, remplacer les imports `jakarta.*` par `javax.*`.

---

## 📋 Prérequis

- **Java 21+**
- **Spring Boot 3.2+**
- **Elasticsearch 7.x ou 8.x** (HTTPS recommandé)
- Optionnel : **Kibana** pour la visualisation
- Optionnel : **Logstash** pour le pipeline de logs

