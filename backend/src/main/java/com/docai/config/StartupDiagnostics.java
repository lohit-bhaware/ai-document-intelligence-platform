package com.docai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Temporary diagnostic listener to log the raw environment variables
 * and Spring's resolved property values at startup, before any beans
 * or database connections are created.
 *
 * DELETE THIS CLASS after the deployment issue is resolved.
 */
public class StartupDiagnostics implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();

        log.warn("=== DATASOURCE DIAGNOSTICS (remove after fix) ===");

        // 1. Log Raw Environment Variables via System.getenv()
        String pgHost = System.getenv("PGHOST");
        String pgPort = System.getenv("PGPORT");
        String pgDatabase = System.getenv("PGDATABASE");
        String pgUser = System.getenv("PGUSER");
        String pgPassword = System.getenv("PGPASSWORD");
        String databaseUrl = System.getenv("DATABASE_URL");

        log.warn("--- RAW ENVIRONMENT VARIABLES (System.getenv) ---");
        log.warn("PGHOST        = [{}]", pgHost);
        log.warn("PGPORT        = [{}]", pgPort);
        log.warn("PGDATABASE    = [{}]", pgDatabase);
        log.warn("PGUSER        = [{}]", pgUser);
        log.warn("PGPASSWORD    = [{}]", mask(pgPassword));
        log.warn("DATABASE_URL  = [{}]", maskUrl(databaseUrl));

        // 2. PGPORT Detailed Analysis
        log.warn("--- PGPORT ANALYSIS ---");
        if (pgPort != null) {
            log.warn("Length              : {}", pgPort.length());
            log.warn("Is Numeric          : {}", isNumeric(pgPort));
            log.warn("Contains Whitespace : {}", pgPort.matches(".*\\s+.*"));
            log.warn("Contains '${{'      : {}", pgPort.contains("${{"));
            log.warn("Contains '$'        : {}", pgPort.contains("$"));
            
            StringBuilder ascii = new StringBuilder();
            for (char c : pgPort.toCharArray()) {
                ascii.append((int) c).append(' ');
            }
            log.warn("PGPORT ASCII        : {}", ascii.toString().trim());
        } else {
            log.warn("PGPORT is null.");
        }

        // 3. Spring Environment Resolution
        log.warn("--- SPRING ENVIRONMENT RESOLUTION ---");
        
        log.warn("Property spring.datasource.url = {}",
                env.getProperty("spring.datasource.url"));
        
        log.warn("Property spring.datasource.username = {}",
                env.getProperty("spring.datasource.username"));
        
        log.warn("Property spring.datasource.password exists = {}",
                env.containsProperty("spring.datasource.password"));

        log.warn("=== END DATASOURCE DIAGNOSTICS ===");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            Integer.parseInt(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 2) return value;
        return value.substring(0, 1) + "****" + value.substring(value.length() - 1);
    }

    private static String maskUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:****@");
    }
}
