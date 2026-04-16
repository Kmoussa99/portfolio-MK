package com.orange.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation d'audit Orange — trace les actions utilisateur et les envoie vers ELK.
 *
 * <p>À placer sur les méthodes de contrôleurs ou de services que vous souhaitez auditer.
 * Les endpoints d'authentification (/api/auth/**) sont exclus par défaut.</p>
 *
 * <h3>Utilisation :</h3>
 * <pre>{@code
 * @AuditLogOrange(action = "CREATE_APPLICATION")
 * @PostMapping
 * public ResponseEntity<App> create(@RequestBody AppRequest req) { ... }
 * }</pre>
 *
 * @author Orange Developer Portal
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLogOrange {

    /**
     * Description de l'action auditée (ex: "CREATE_APPLICATION", "DELETE_SUBSCRIPTION").
     */
    String action();

    /**
     * Noms des paramètres sensibles à masquer dans les logs (ex: "password", "token").
     * Les valeurs de ces paramètres seront remplacées par "***".
     */
    String[] sensitiveParams() default {"password", "token", "secret", "refreshToken"};
}

