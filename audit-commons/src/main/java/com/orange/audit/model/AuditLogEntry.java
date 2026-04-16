package com.orange.audit.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modèle d'entrée d'audit envoyé vers ELK.
 * Chaque champ correspond à un champ indexé dans Elasticsearch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {

    /** Horodatage de l'action */
    private Instant timestamp;

    /** Identifiant unique de l'utilisateur (sub du JWT) */
    private String userId;

    /** Nom d'utilisateur (preferred_username du JWT) */
    private String username;

    /** Email de l'utilisateur */
    private String email;

    /** Rôles de l'utilisateur extraits du JWT */
    private List<String> roles;

    /** Action métier (ex: "CREATE_APPLICATION", "DELETE_SUBSCRIPTION") */
    private String action;

    /** Méthode HTTP (GET, POST, PUT, DELETE) */
    private String httpMethod;

    /** URI de la requête (ex: /api/applications) */
    private String requestUri;

    /** Nom complet de la méthode Java appelée */
    private String methodName;

    /** Classe du contrôleur */
    private String controllerClass;

    /** Paramètres de la requête (masqués pour les données sensibles) */
    private Map<String, Object> parameters;

    /** Résultat de l'opération : SUCCESS ou FAILURE */
    private String result;

    /** Code HTTP de la réponse */
    private int httpStatus;

    /** Message d'erreur en cas d'échec */
    private String errorMessage;

    /** Durée d'exécution en millisecondes */
    private long durationMs;

    /** Nom du microservice source (ex: "developer-portal", "auth-service") */
    private String sourceService;

    /** Adresse IP du client */
    private String clientIp;

    /** Trace ID pour corrélation distribuée */
    private String traceId;
}

