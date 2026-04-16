package com.orange.audit.service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orange.audit.model.AuditLogEntry;

import jakarta.annotation.PostConstruct;

/**
 * Service d'envoi des audit logs directement vers Elasticsearch (HTTPS).
 *
 * <p><b>Pas besoin de Logstash.</b> Envoi direct via HTTP POST.</p>
 *
 * <p>Double sécurité :</p>
 * <ul>
 *   <li><b>Elasticsearch HTTPS</b> — POST vers l'index <code>audit-logs-YYYY.MM.dd</code></li>
 *   <li><b>Fichier local</b> — <code>logs/audit-logs.json</code> en secours</li>
 * </ul>
 *
 * <p>L'envoi est asynchrone (@Async) pour ne pas impacter la latence des endpoints.</p>
 */
@Service
public class AuditLogService {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final ObjectMapper objectMapper;

    @Value("${audit.elasticsearch.url:https://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${audit.elasticsearch.enabled:true}")
    private boolean elasticsearchEnabled;

    @Value("${audit.elasticsearch.username:elastic}")
    private String elasticsearchUsername;

    @Value("${audit.elasticsearch.password:elastic123}")
    private String elasticsearchPassword;

    @Value("${audit.elasticsearch.api-key:}")
    private String elasticsearchApiKey;

    @Value("${audit.elasticsearch.insecure-tls:true}")
    private boolean insecureTls;

    @Value("${audit.elasticsearch.index-prefix:audit-logs}")
    private String indexPrefix;

    /** SSLContext et HostnameVerifier pour skip TLS en dev */
    private SSLContext insecureSslContext;
    private HostnameVerifier insecureHostnameVerifier;

    public AuditLogService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    void init() {
        if (insecureTls) {
            try {
                insecureSslContext = SSLContext.getInstance("TLS");
                insecureSslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());
                insecureHostnameVerifier = (hostname, session) -> true;
            } catch (Exception e) {
                log.error("Erreur init SSL insecure: {}", e.getMessage());
            }
        }

        log.info("AuditLogService initialisé — ES={}, auth={}, tls-verify={}, index={}",
                elasticsearchUrl,
                elasticsearchApiKey != null && !elasticsearchApiKey.isBlank() ? "api-key" : "basic",
                !insecureTls,
                indexPrefix);
    }

    /**
     * Envoie un audit log de manière asynchrone vers Elasticsearch + fichier local.
     */
    @Async
    public void sendAuditLog(AuditLogEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);

            // ── 1. Fichier local de secours ──
            setMdc(entry);
            AUDIT_LOGGER.info(json);
            clearMdc();

            // ── 2. Envoi direct vers Elasticsearch ──
            if (elasticsearchEnabled) {
                sendToElasticsearch(json);
            }

        } catch (JsonProcessingException e) {
            log.error("Erreur sérialisation audit log: {}", e.getMessage());
        }
    }

    /**
     * POST direct vers Elasticsearch via HttpURLConnection.
     * Utilise HttpURLConnection (et non HttpClient) pour pouvoir désactiver
     * à la fois la vérification du certificat ET du hostname (insecure-tls=true).
     */
    private void sendToElasticsearch(String json) {
        HttpURLConnection conn = null;
        try {
            String index = indexPrefix + "-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = elasticsearchUrl + "/" + index + "/_doc";

            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();

            // ── Skip TLS + hostname verification (dev/preprod) ──
            if (conn instanceof HttpsURLConnection httpsConn && insecureTls && insecureSslContext != null) {
                httpsConn.setSSLSocketFactory(insecureSslContext.getSocketFactory());
                httpsConn.setHostnameVerifier(insecureHostnameVerifier);
            }

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);

            // ── Authentification ──
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey);
            } else if (elasticsearchUsername != null && !elasticsearchUsername.isBlank()) {
                String credentials = elasticsearchUsername + ":" + elasticsearchPassword;
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encoded);
            }

            // ── Envoi ──
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 300) {
                log.warn("Elasticsearch audit log failed [{}]", code);
            } else {
                log.debug("Audit log envoyé vers ES [{}] index={}", code, index);
            }
        } catch (Exception e) {
            log.warn("Impossible d'envoyer l'audit log vers Elasticsearch: {}", e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void setMdc(AuditLogEntry entry) {
        MDC.put("audit_action", entry.getAction());
        MDC.put("audit_userId", entry.getUserId() != null ? entry.getUserId() : "anonymous");
        MDC.put("audit_username", entry.getUsername() != null ? entry.getUsername() : "anonymous");
        MDC.put("audit_httpMethod", entry.getHttpMethod() != null ? entry.getHttpMethod() : "");
        MDC.put("audit_requestUri", entry.getRequestUri() != null ? entry.getRequestUri() : "");
        MDC.put("audit_result", entry.getResult() != null ? entry.getResult() : "");
        MDC.put("audit_httpStatus", String.valueOf(entry.getHttpStatus()));
        MDC.put("audit_durationMs", String.valueOf(entry.getDurationMs()));
        MDC.put("audit_sourceService", entry.getSourceService() != null ? entry.getSourceService() : "");
        MDC.put("audit_clientIp", entry.getClientIp() != null ? entry.getClientIp() : "");
    }

    private void clearMdc() {
        MDC.remove("audit_action");
        MDC.remove("audit_userId");
        MDC.remove("audit_username");
        MDC.remove("audit_httpMethod");
        MDC.remove("audit_requestUri");
        MDC.remove("audit_result");
        MDC.remove("audit_httpStatus");
        MDC.remove("audit_durationMs");
        MDC.remove("audit_sourceService");
        MDC.remove("audit_clientIp");
    }
}

