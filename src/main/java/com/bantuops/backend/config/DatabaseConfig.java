package com.bantuops.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@Slf4j
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Service to set audit context in database session
     */
    @Bean
    public DatabaseAuditService databaseAuditService(JdbcTemplate jdbcTemplate) {
        return new DatabaseAuditService(jdbcTemplate);
    }

    public static class DatabaseAuditService {
        private final JdbcTemplate jdbcTemplate;

        public DatabaseAuditService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        public void setAuditContext(Long userId, String userEmail, String userRole, 
                                  String clientIp, String userAgent, String sessionId) {
            try {
                jdbcTemplate.execute(String.format(
                    "SELECT set_audit_context(%d, '%s', '%s', %s, %s, %s)",
                    userId != null ? userId : 0,
                    userEmail != null ? userEmail.replace("'", "''") : "system",
                    userRole != null ? userRole.replace("'", "''") : "system",
                    clientIp != null ? "'" + clientIp + "'" : "NULL",
                    userAgent != null ? "'" + userAgent.replace("'", "''") + "'" : "NULL",
                    sessionId != null ? "'" + sessionId + "'" : "NULL"
                ));
                log.debug("Audit context set for user: {}", userEmail);
            } catch (Exception e) {
                log.warn("Failed to set audit context: {}", e.getMessage());
            }
        }

        public void clearAuditContext() {
            try {
                jdbcTemplate.execute("SELECT clear_audit_context()");
                log.debug("Audit context cleared");
            } catch (Exception e) {
                log.warn("Failed to clear audit context: {}", e.getMessage());
            }
        }
    }
}