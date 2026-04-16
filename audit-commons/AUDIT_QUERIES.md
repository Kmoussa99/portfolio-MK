# 🔍 Audit Logs Orange — Requêtes Elasticsearch & Guide Kibana

## 📋 Table des matières
- [Démarrage ELK](#démarrage-elk)
- [Configuration Kibana](#configuration-kibana)
- [Requêtes Elasticsearch](#requêtes-elasticsearch)
- [Actions auditées](#actions-auditées)

---

## 🚀 Démarrage ELK

```bash
# Démarrer la stack ELK avec les services existants
docker compose --profile elk up -d

# Vérifier qu'Elasticsearch est prêt
curl http://localhost:9200/_cluster/health?pretty

# Accéder à Kibana
open http://localhost:5601
```

---

## ⚙️ Configuration Kibana

### 1. Créer l'Index Pattern
1. Aller dans **Kibana → Management → Stack Management → Data Views**
2. Cliquer **Create data view**
3. Nom : `audit-logs-*`
4. Index pattern : `audit-logs-*`
5. Timestamp field : `@timestamp`
6. Sauvegarder

### 2. Vérifier les données
Aller dans **Discover** → Sélectionner `audit-logs-*`

---

## 🔎 Requêtes Elasticsearch

### ① Voir TOUS les audit logs (dernières 24h)
```json
GET audit-logs-*/_search
{
  "query": {
    "range": {
      "@timestamp": {
        "gte": "now-24h",
        "lte": "now"
      }
    }
  },
  "sort": [{ "@timestamp": "desc" }],
  "size": 100
}
```

### ② Chercher toutes les actions d'un utilisateur spécifique
```json
GET audit-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "audit.username": "moussa.kone" } }
      ]
    }
  },
  "sort": [{ "@timestamp": "desc" }],
  "size": 50
}
```

### ③ Chercher par action métier (ex: toutes les créations d'applications)
```json
GET audit-logs-*/_search
{
  "query": {
    "term": { "audit.action.keyword": "CREATE_APPLICATION" }
  },
  "sort": [{ "@timestamp": "desc" }]
}
```

### ④ Chercher toutes les actions en ÉCHEC
```json
GET audit-logs-*/_search
{
  "query": {
    "term": { "audit.result.keyword": "FAILURE" }
  },
  "sort": [{ "@timestamp": "desc" }],
  "size": 50
}
```

### ⑤ Chercher par service source (ex: seulement developer-portal)
```json
GET audit-logs-*/_search
{
  "query": {
    "term": { "audit.sourceService.keyword": "developer-portal" }
  },
  "sort": [{ "@timestamp": "desc" }]
}
```

### ⑥ Chercher par période spécifique
```json
GET audit-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "range": {
            "@timestamp": {
              "gte": "2026-03-01T00:00:00Z",
              "lte": "2026-03-05T23:59:59Z"
            }
          }
        }
      ]
    }
  },
  "sort": [{ "@timestamp": "desc" }]
}
```

### ⑦ Chercher les actions d'un utilisateur sur un endpoint précis
```json
GET audit-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "audit.username": "moussa.kone" } },
        { "wildcard": { "audit.requestUri.keyword": "/api/applications*" } }
      ]
    }
  },
  "sort": [{ "@timestamp": "desc" }]
}
```

### ⑧ Chercher les requêtes lentes (> 2 secondes)
```json
GET audit-logs-*/_search
{
  "query": {
    "range": {
      "audit.durationMs": { "gte": 2000 }
    }
  },
  "sort": [{ "audit.durationMs": "desc" }]
}
```

### ⑨ Compter les actions par utilisateur (agrégation)
```json
GET audit-logs-*/_search
{
  "size": 0,
  "aggs": {
    "actions_par_user": {
      "terms": {
        "field": "audit.username.keyword",
        "size": 20
      },
      "aggs": {
        "actions": {
          "terms": { "field": "audit.action.keyword" }
        }
      }
    }
  }
}
```

### ⑩ Timeline des actions par jour (histogram)
```json
GET audit-logs-*/_search
{
  "size": 0,
  "aggs": {
    "timeline": {
      "date_histogram": {
        "field": "@timestamp",
        "calendar_interval": "day"
      },
      "aggs": {
        "par_action": {
          "terms": { "field": "audit.action.keyword" }
        }
      }
    }
  }
}
```

### ⑪ Chercher par adresse IP client
```json
GET audit-logs-*/_search
{
  "query": {
    "term": { "audit.clientIp.keyword": "192.168.1.100" }
  },
  "sort": [{ "@timestamp": "desc" }]
}
```

### ⑫ Chercher par rôle utilisateur
```json
GET audit-logs-*/_search
{
  "query": {
    "term": { "audit.roles.keyword": "admin" }
  },
  "sort": [{ "@timestamp": "desc" }]
}
```

---

## 📌 Actions auditées

### Developer Portal (`developer-portal`)
| Action | Endpoint | Description |
|--------|----------|-------------|
| `CREATE_APPLICATION` | `POST /api/applications` | Création d'application |
| `UPDATE_APPLICATION` | `PUT /api/applications/{id}` | Mise à jour d'application |
| `DELETE_APPLICATION` | `DELETE /api/applications/{id}` | Suppression d'application |
| `ADD_APPLICATION_MEMBER` | `POST /api/applications/{id}/members` | Ajout de membre |
| `REMOVE_APPLICATION_MEMBER` | `DELETE /api/applications/{id}/members/{devId}` | Retrait de membre |
| `CREATE_SUBSCRIPTION` | `POST /api/subscriptions` | Souscription à une API |
| `UNSUBSCRIBE_API` | `POST /api/subscriptions/{id}/unsubscribe` | Désabonnement |
| `DELETE_SUBSCRIPTION` | `DELETE /api/subscriptions/{id}` | Suppression souscription |
| `CREATE_DEVELOPER` | `POST /api/developers` | Création compte développeur |
| `UPDATE_DEVELOPER` | `PUT /api/developers/{id}` | Mise à jour développeur |
| `DELETE_DEVELOPER` | `DELETE /api/developers/{id}` | Suppression développeur |
| `CREATE_CATALOG_API` | `POST /api/catalog/apis` | Ajout API au catalogue |
| `UPDATE_CATALOG_API` | `PUT /api/catalog/apis/{id}` | Mise à jour API catalogue |
| `DELETE_CATALOG_API` | `DELETE /api/catalog/apis/{id}` | Suppression API catalogue |
| `CREATE_WSO2_APP` | `POST /api/provisioning/wso2-app` | Provisioning app WSO2 |
| `CREATE_WSO2_API` | `POST /api/provisioning/api` | Création API WSO2 |
| `CREATE_WSO2_SUBSCRIPTION` | `POST /api/provisioning/subscription` | Souscription WSO2 |
| `CREATE_CATALOG_ENTRY` | `POST /api/provisioning/catalog` | Entrée catalogue via provisioning |
| `SYNC_CATALOG_FROM_WSO2` | `POST /api/provisioning/catalog/sync-from-wso2` | Sync WSO2→catalogue |

### Auth Service (`auth-service`)
| Action | Endpoint | Description |
|--------|----------|-------------|
| `CREATE_USER` | `POST /api/provisioning/user` | Création utilisateur Keycloak+WSO2 |
| `CREATE_TEST_USER` | `POST /api/provisioning/test-user` | Création utilisateur de test |
| `ASSIGN_ROLES` | `POST /api/provisioning/assign-roles` | Assignation de rôles |

### ❌ NON audités (exclus volontairement)
| Endpoint | Raison |
|----------|--------|
| `POST /api/auth/token` | Connexion — exclu |
| `POST /api/auth/refresh` | Refresh token — exclu |
| `POST /api/auth/logout` | Déconnexion — exclu |
| `POST /api/auth/register` | Inscription — exclu |
| `GET /api/auth/verify-email` | Vérification email — exclu |
| `/actuator/**` | Health checks — exclu |
| `/swagger/**`, `/v3/api-docs/**` | Documentation — exclu |

---

## 🏗️ Structure d'un audit log dans Elasticsearch

```json
{
  "@timestamp": "2026-03-05T14:32:10.456Z",
  "log_type": "AUDIT",
  "audit": {
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
    "parameters": {
      "req": "ApplicationCreateRequest(name=MyApp, description=Test app, ownerId=dev-123)"
    },
    "result": "SUCCESS",
    "httpStatus": 201,
    "errorMessage": null,
    "durationMs": 56,
    "sourceService": "developer-portal",
    "clientIp": "192.168.1.100",
    "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

