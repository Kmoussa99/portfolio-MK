package com.orange.audit.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Auto-configuration du module d'audit Orange.
 *
 * <p>Active le scan des composants du package com.orange.audit
 * et active le traitement asynchrone pour l'envoi des logs.</p>
 */
@Configuration
@ComponentScan(basePackages = "com.orange.audit")
@EnableAsync
public class AuditAutoConfiguration {
}

