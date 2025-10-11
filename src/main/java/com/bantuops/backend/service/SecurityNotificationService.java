package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.entity.User;
import com.bantuops.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service de notification pour les alertes de sécurité
 * Gère l'envoi de notifications aux utilisateurs, superviseurs et équipe de sécurité
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityNotificationService {

    private final UserRepository userRepository;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Envoie une notification à un utilisateur
     */
    @Async
    public void sendUserNotification(String userId, String subject, String message) {
        try {
            log.info("Envoi de notification à l'utilisateur: {} - Sujet: {}", userId, subject);
            
            userRepository.findByUsername(userId).ifPresentOrElse(
                user -> {
                    // Simuler l'envoi d'email/SMS
                    sendEmailNotification(user.getEmail(), subject, message);
                    log.info("Notification envoyée à l'utilisateur: {}", userId);
                },
                () -> log.warn("Utilisateur non trouvé pour la notification: {}", userId)
            );
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification à l'utilisateur: {}", userId, e);
        }
    }

    /**
     * Envoie une notification au superviseur d'un utilisateur
     */
    @Async
    public void sendSupervisorNotification(String userId, SecurityAlert alert) {
        try {
            log.info("Envoi de notification au superviseur pour l'utilisateur: {}", userId);
            
            userRepository.findByUsername(userId).ifPresent(user -> {
                if (user.getSupervisorId() != null) {
                    userRepository.findById(user.getSupervisorId()).ifPresent(supervisor -> {
                        String subject = "Alerte de sécurité - Employé sous votre supervision";
                        String message = formatSupervisorMessage(user, alert);
                        
                        sendEmailNotification(supervisor.getEmail(), subject, message);
                        log.info("Notification superviseur envoyée pour l'utilisateur: {}", userId);
                    });
                }
            });
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification au superviseur pour: {}", userId, e);
        }
    }

    /**
     * Envoie une alerte à l'équipe de sécurité
     */
    @Async
    public void sendSecurityTeamAlert(SecurityAlert alert) {
        try {
            log.warn("Envoi d'alerte à l'équipe de sécurité pour l'alerte: {}", alert.getId());
            
            List<User> securityTeam = userRepository.findByRole("SECURITY_ADMIN");
            
            String subject = "Alerte de sécurité - " + alert.getAlertType().getDescription();
            String message = formatSecurityTeamMessage(alert);
            
            securityTeam.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), subject, message);
                sendSmsNotification(admin.getPhoneNumber(), 
                    "Alerte sécurité: " + alert.getAlertType().getDescription());
            });
            
            log.warn("Alerte équipe de sécurité envoyée pour l'alerte: {}", alert.getId());
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi d'alerte à l'équipe de sécurité", e);
        }
    }

    /**
     * Envoie une notification à l'équipe de sécurité
     */
    @Async
    public void sendSecurityTeamNotification(String subject, String message) {
        try {
            log.info("Envoi de notification à l'équipe de sécurité: {}", subject);
            
            List<User> securityTeam = userRepository.findByRole("SECURITY_ADMIN");
            
            securityTeam.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), subject, message);
            });
            
            log.info("Notification équipe de sécurité envoyée");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification à l'équipe de sécurité", e);
        }
    }

    /**
     * Envoie une alerte critique de sécurité
     */
    @Async
    public void sendCriticalSecurityAlert(SecurityAlert alert) {
        try {
            log.error("Envoi d'alerte critique de sécurité pour l'alerte: {}", alert.getId());
            
            List<User> allAdmins = userRepository.findByRoleIn(List.of("ADMIN", "SECURITY_ADMIN", "SUPER_ADMIN"));
            
            String subject = "🚨 ALERTE CRITIQUE DE SÉCURITÉ - " + alert.getAlertType().getDescription();
            String message = formatCriticalAlertMessage(alert);
            
            allAdmins.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), subject, message);
                sendSmsNotification(admin.getPhoneNumber(), 
                    "ALERTE CRITIQUE: " + alert.getAlertType().getDescription());
                
                // Appel téléphonique pour les alertes critiques
                initiatePhoneCall(admin.getPhoneNumber(), 
                    "Alerte critique de sécurité détectée dans BantuOps");
            });
            
            log.error("Alerte critique envoyée à tous les administrateurs");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi d'alerte critique", e);
        }
    }

    /**
     * Envoie une notification d'urgence à tous les administrateurs
     */
    @Async
    public void sendEmergencyNotificationToAllAdmins(SecurityAlert alert) {
        try {
            log.error("Envoi de notification d'urgence à tous les administrateurs");
            
            List<User> allAdmins = userRepository.findByRoleIn(List.of("ADMIN", "SECURITY_ADMIN", "SUPER_ADMIN"));
            
            String subject = "🚨 URGENCE SÉCURITÉ - Action immédiate requise";
            String message = formatEmergencyMessage(alert);
            
            allAdmins.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), subject, message);
                sendSmsNotification(admin.getPhoneNumber(), 
                    "URGENCE SÉCURITÉ BantuOps - Intervention immédiate requise");
                initiatePhoneCall(admin.getPhoneNumber(), 
                    "Urgence sécurité BantuOps - Intervention immédiate requise");
            });
            
            log.error("Notification d'urgence envoyée à tous les administrateurs");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification d'urgence", e);
        }
    }

    /**
     * Envoie une notification d'urgence avec message personnalisé
     */
    @Async
    public void sendEmergencyNotificationToAllAdmins(String subject, String message) {
        try {
            log.error("Envoi de notification d'urgence personnalisée: {}", subject);
            
            List<User> allAdmins = userRepository.findByRoleIn(List.of("ADMIN", "SECURITY_ADMIN", "SUPER_ADMIN"));
            
            allAdmins.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), "🚨 " + subject, message);
                sendSmsNotification(admin.getPhoneNumber(), subject);
            });
            
            log.error("Notification d'urgence personnalisée envoyée");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification d'urgence personnalisée", e);
        }
    }

    /**
     * Envoie une notification aux administrateurs
     */
    @Async
    public void sendAdminNotification(String subject, String message) {
        try {
            log.info("Envoi de notification aux administrateurs: {}", subject);
            
            List<User> admins = userRepository.findByRole("ADMIN");
            
            admins.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), subject, message);
            });
            
            log.info("Notification administrateurs envoyée");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de notification aux administrateurs", e);
        }
    }

    /**
     * Envoie une alerte système critique
     */
    @Async
    public void sendCriticalSystemAlert(String subject, String message) {
        try {
            log.error("Envoi d'alerte système critique: {}", subject);
            
            List<User> systemAdmins = userRepository.findByRoleIn(List.of("SUPER_ADMIN", "SYSTEM_ADMIN"));
            
            systemAdmins.forEach(admin -> {
                sendEmailNotification(admin.getEmail(), "🚨 CRITIQUE - " + subject, message);
                sendSmsNotification(admin.getPhoneNumber(), "CRITIQUE: " + subject);
                initiatePhoneCall(admin.getPhoneNumber(), message);
            });
            
            log.error("Alerte système critique envoyée");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi d'alerte système critique", e);
        }
    }

    /**
     * Envoie une alerte critique générale
     */
    @Async
    public void sendCriticalAlert(String subject, String message) {
        try {
            log.error("Envoi d'alerte critique: {}", subject);
            
            List<User> criticalRecipients = userRepository.findByRoleIn(
                List.of("ADMIN", "SECURITY_ADMIN", "SUPER_ADMIN"));
            
            criticalRecipients.forEach(recipient -> {
                sendEmailNotification(recipient.getEmail(), "🚨 " + subject, message);
                sendSmsNotification(recipient.getPhoneNumber(), subject);
            });
            
            log.error("Alerte critique envoyée");
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi d'alerte critique", e);
        }
    }

    /**
     * Formate le message pour le superviseur
     */
    private String formatSupervisorMessage(User employee, SecurityAlert alert) {
        return String.format("""
            Bonjour,
            
            Une alerte de sécurité a été détectée pour l'employé sous votre supervision:
            
            Employé: %s (%s)
            Type d'alerte: %s
            Sévérité: %s
            Date/Heure: %s
            Description: %s
            
            Veuillez prendre les mesures appropriées et contacter l'équipe de sécurité si nécessaire.
            
            Cordialement,
            Système de sécurité BantuOps
            """,
            employee.getFullName(),
            employee.getUsername(),
            alert.getAlertType().getDescription(),
            alert.getSeverity().getLabel(),
            alert.getTimestamp().format(FORMATTER),
            alert.getDescription()
        );
    }

    /**
     * Formate le message pour l'équipe de sécurité
     */
    private String formatSecurityTeamMessage(SecurityAlert alert) {
        return String.format("""
            ALERTE DE SÉCURITÉ DÉTECTÉE
            
            ID Alerte: %s
            Type: %s
            Sévérité: %s
            Utilisateur: %s
            Date/Heure: %s
            IP: %s
            User-Agent: %s
            
            Description: %s
            
            Métadonnées: %s
            
            Action requise: Veuillez investiguer immédiatement.
            
            Système de sécurité BantuOps
            """,
            alert.getId(),
            alert.getAlertType().getDescription(),
            alert.getSeverity().getLabel(),
            alert.getUserId(),
            alert.getTimestamp().format(FORMATTER),
            alert.getIpAddress(),
            alert.getUserAgent(),
            alert.getDescription(),
            alert.getMetadata()
        );
    }

    /**
     * Formate le message d'alerte critique
     */
    private String formatCriticalAlertMessage(SecurityAlert alert) {
        return String.format("""
            🚨 ALERTE CRITIQUE DE SÉCURITÉ 🚨
            
            Une alerte de sécurité critique nécessite votre attention immédiate.
            
            ID Alerte: %s
            Type: %s
            Sévérité: %s
            Utilisateur: %s
            Date/Heure: %s
            IP: %s
            
            Description: %s
            
            INTERVENTION IMMÉDIATE REQUISE
            
            Veuillez vous connecter au système de sécurité pour plus de détails.
            
            Système de sécurité BantuOps
            """,
            alert.getId(),
            alert.getAlertType().getDescription(),
            alert.getSeverity().getLabel(),
            alert.getUserId(),
            alert.getTimestamp().format(FORMATTER),
            alert.getIpAddress(),
            alert.getDescription()
        );
    }

    /**
     * Formate le message d'urgence
     */
    private String formatEmergencyMessage(SecurityAlert alert) {
        return String.format("""
            🚨🚨 URGENCE SÉCURITÉ - ACTION IMMÉDIATE REQUISE 🚨🚨
            
            Une situation d'urgence de sécurité a été détectée dans le système BantuOps.
            
            Type d'incident: %s
            Sévérité: CRITIQUE
            Heure de détection: %s
            Utilisateur impliqué: %s
            
            Description: %s
            
            MESURES AUTOMATIQUES DÉCLENCHÉES
            
            Veuillez vous connecter IMMÉDIATEMENT au système pour évaluer la situation
            et prendre les mesures correctives nécessaires.
            
            En cas d'indisponibilité, contactez l'équipe de sécurité d'urgence.
            
            Système de sécurité BantuOps
            """,
            alert.getAlertType().getDescription(),
            alert.getTimestamp().format(FORMATTER),
            alert.getUserId(),
            alert.getDescription()
        );
    }

    /**
     * Simule l'envoi d'un email
     */
    private void sendEmailNotification(String email, String subject, String message) {
        // Dans un vrai système, intégrer avec un service d'email (SendGrid, AWS SES, etc.)
        log.info("EMAIL envoyé à {}: {}", email, subject);
    }

    /**
     * Simule l'envoi d'un SMS
     */
    private void sendSmsNotification(String phoneNumber, String message) {
        // Dans un vrai système, intégrer avec un service SMS (Twilio, AWS SNS, etc.)
        log.info("SMS envoyé à {}: {}", phoneNumber, message);
    }

    /**
     * Simule un appel téléphonique automatique
     */
    private void initiatePhoneCall(String phoneNumber, String message) {
        // Dans un vrai système, intégrer avec un service d'appel automatique
        log.error("APPEL AUTOMATIQUE initié vers {}: {}", phoneNumber, message);
    }
}