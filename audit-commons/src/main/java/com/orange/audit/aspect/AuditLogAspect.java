package com.orange.audit.aspect;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.orange.audit.annotation.AuditLogOrange;
import com.orange.audit.model.AuditLogEntry;
import com.orange.audit.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Aspect AOP qui intercepte toutes les méthodes annotées {@link AuditLogOrange}.
 *
 * <p>Il capture :
 * <ul>
 *   <li>L'utilisateur (depuis le JWT dans SecurityContext)</li>
 *   <li>L'action effectuée</li>
 *   <li>Les paramètres (avec masquage des données sensibles)</li>
 *   <li>Le résultat (succès/échec, code HTTP)</li>
 *   <li>La durée d'exécution</li>
 *   <li>Les métadonnées HTTP (URI, méthode, IP client)</li>
 * </ul>
 *
 * <p>Les endpoints d'authentification (/api/auth/**) sont automatiquement exclus.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;
    private final HttpServletRequest httpServletRequest;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    /** Préfixes d'URI à exclure de l'audit — configurable via audit.excluded-uri-prefixes */
    @Value("${audit.excluded-uri-prefixes:/api/auth/,/actuator/,/swagger,/v3/api-docs}")
    private String excludedUriPrefixesConfig;

    private List<String> excludedUriPrefixes;

    public AuditLogAspect(AuditLogService auditLogService, HttpServletRequest httpServletRequest) {
        this.auditLogService = auditLogService;
        this.httpServletRequest = httpServletRequest;
    }

    @jakarta.annotation.PostConstruct
    void init() {
        this.excludedUriPrefixes = List.of(excludedUriPrefixesConfig.split(","));
        log.info("AuditLogAspect initialisé — service={}, exclusions={}", serviceName, excludedUriPrefixes);
    }

    @Around("@annotation(auditLogOrange)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, AuditLogOrange auditLogOrange) throws Throwable {

        // ── Exclure les URI d'auth/connexion ──
        String requestUri = getRequestUri();
        if (isExcludedUri(requestUri)) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        AuditLogEntry.AuditLogEntryBuilder builder = AuditLogEntry.builder();

        // ── Métadonnées de base ──
        builder.timestamp(Instant.now());
        builder.action(auditLogOrange.action());
        builder.sourceService(serviceName);
        builder.traceId(UUID.randomUUID().toString());

        // ── Informations HTTP ──
        builder.httpMethod(getHttpMethod());
        builder.requestUri(requestUri);
        builder.clientIp(getClientIp());

        // ── Informations sur la méthode ──
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        builder.methodName(signature.getMethod().getName());
        builder.controllerClass(joinPoint.getTarget().getClass().getSimpleName());

        // ── Informations utilisateur (JWT) ──
        extractUserInfo(builder);

        // ── Paramètres (avec masquage des données sensibles) ──
        Map<String, Object> params = extractParameters(signature, joinPoint.getArgs(),
                Set.of(auditLogOrange.sensitiveParams()));
        builder.parameters(params);

        // ── Exécution de la méthode ──
        Object result;
        try {
            result = joinPoint.proceed();

            // ── Résultat succès ──
            builder.result("SUCCESS");
            builder.httpStatus(extractHttpStatus(result));

        } catch (Throwable ex) {
            // ── Résultat échec ──
            builder.result("FAILURE");
            builder.httpStatus(500);
            builder.errorMessage(ex.getMessage());
            throw ex; // On relance l'exception pour ne pas casser le flux
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            builder.durationMs(duration);

            // ── Envoi asynchrone vers ELK ──
            try {
                auditLogService.sendAuditLog(builder.build());
            } catch (Exception e) {
                log.warn("Erreur envoi audit log: {}", e.getMessage());
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  Méthodes utilitaires
    // ═══════════════════════════════════════════════════════════

    /**
     * Extrait les informations de l'utilisateur connecté depuis le SecurityContext.
     *
     * <p>Supporte 3 scénarios courants :</p>
     * <ol>
     *   <li><b>JWT (Keycloak / Spring OAuth2 Resource Server)</b> — principal est un {@code Jwt}</li>
     *   <li><b>UsernamePasswordAuthenticationToken + details(Map)</b> — cas où un filtre custom valide
     *       le token et stocke les claims dans {@code authentication.getDetails()}</li>
     *   <li><b>String principal</b> — fallback, on prend le nom du principal</li>
     * </ol>
     *
     * <p>Les rôles sont extraits depuis (par priorité) :</p>
     * <ol>
     *   <li>{@code realm_access.roles} (Keycloak)</li>
     *   <li>{@code roles} (claim directe)</li>
     *   <li>{@code GrantedAuthority} de Spring Security</li>
     * </ol>
     */
    private void extractUserInfo(AuditLogEntry.AuditLogEntryBuilder builder) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                builder.userId("anonymous");
                builder.username("anonymous");
                return;
            }

            Object principal = authentication.getPrincipal();

            // ═══ Cas 1 : JWT (Spring OAuth2 Resource Server / Keycloak) ═══
            if (principal instanceof Jwt jwt) {
                builder.userId(jwt.getSubject());
                builder.username(firstNonNull(
                        jwt.getClaimAsString("preferred_username"),
                        jwt.getClaimAsString("email"),
                        jwt.getSubject()
                ));
                builder.email(jwt.getClaimAsString("email"));
                extractRolesFromClaims(builder, jwt.getClaims());
                return;
            }

            // ═══ Cas 2 & 3 : UsernamePasswordAuthenticationToken ou autre ═══
            builder.username(authentication.getName());

            // Essayer de récupérer les infos depuis authentication.getDetails()
            // (rempli par des filtres custom qui valident le JWT et stockent les claims)
            Object details = authentication.getDetails();
            if (details instanceof Map<?, ?> detailsMap) {
                Object sub = detailsMap.get("sub");
                if (sub != null) builder.userId(sub.toString());

                Object email = detailsMap.get("email");
                if (email != null) builder.email(email.toString());

                Object preferredUsername = detailsMap.get("preferred_username");
                if (preferredUsername != null) builder.username(preferredUsername.toString());

                extractRolesFromClaims(builder, detailsMap);
            }

            // Fallback rôles : depuis les GrantedAuthority de Spring Security
            if (builder.build().getRoles() == null || builder.build().getRoles().isEmpty()) {
                Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                if (authorities != null && !authorities.isEmpty()) {
                    builder.roles(authorities.stream()
                            .map(GrantedAuthority::getAuthority)
                            .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                            .toList());
                }
            }

        } catch (Exception e) {
            log.debug("Impossible d'extraire les infos utilisateur: {}", e.getMessage());
            builder.userId("unknown");
            builder.username("unknown");
        }
    }

    /**
     * Extrait les rôles depuis une map de claims JWT.
     * Cherche dans : realm_access.roles (Keycloak), roles (claim directe).
     */
    @SuppressWarnings("unchecked")
    private void extractRolesFromClaims(AuditLogEntry.AuditLogEntryBuilder builder, Map<?, ?> claims) {
        // 1. realm_access.roles (Keycloak)
        Object realmAccessObj = claims.get("realm_access");
        if (realmAccessObj instanceof Map<?, ?> realmAccessMap) {
            Object rolesObj = realmAccessMap.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                builder.roles(roles.stream()
                        .filter(r -> r instanceof String)
                        .map(Object::toString)
                        .toList());
                return;
            }
        }
        // 2. roles (claim directe, Auth0 / custom IdP)
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof Collection<?> roles) {
            builder.roles(roles.stream()
                    .filter(r -> r instanceof String)
                    .map(Object::toString)
                    .toList());
        }
    }

    private String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "unknown";
    }

    private Map<String, Object> extractParameters(MethodSignature signature, Object[] args, Set<String> sensitiveParams) {
        Map<String, Object> params = new HashMap<>();
        String[] paramNames = signature.getParameterNames();

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                String paramName = paramNames[i];

                // Ignorer les objets HttpServletRequest, HttpServletResponse, Jwt etc.
                if (args[i] instanceof HttpServletRequest
                        || args[i] instanceof jakarta.servlet.http.HttpServletResponse
                        || args[i] instanceof Jwt
                        || args[i] instanceof Authentication) {
                    continue;
                }

                // Masquer les paramètres sensibles
                if (sensitiveParams.stream().anyMatch(s -> paramName.toLowerCase().contains(s.toLowerCase()))) {
                    params.put(paramName, "***MASKED***");
                } else {
                    try {
                        params.put(paramName, sanitizeValue(args[i]));
                    } catch (Exception e) {
                        params.put(paramName, "[non-sérialisable]");
                    }
                }
            }
        }
        return params;
    }

    /**
     * Sanitise la valeur pour éviter de logger des objets trop volumineux.
     */
    private Object sanitizeValue(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s.length() > 500 ? s.substring(0, 500) + "..." : s;
        if (value instanceof Number || value instanceof Boolean) return value;
        // Pour les DTOs, on retourne le toString() tronqué
        String str = value.toString();
        return str.length() > 1000 ? str.substring(0, 1000) + "..." : str;
    }

    private int extractHttpStatus(Object result) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        return 200;
    }

    private String getRequestUri() {
        try {
            return httpServletRequest.getRequestURI();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getHttpMethod() {
        try {
            return httpServletRequest.getMethod();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getClientIp() {
        try {
            String xForwardedFor = httpServletRequest.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return httpServletRequest.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isExcludedUri(String uri) {
        if (uri == null) return false;
        return excludedUriPrefixes.stream().anyMatch(prefix -> uri.startsWith(prefix.trim()));
    }
}

