package com.bantuops.backend.service;

import com.bantuops.backend.entity.AttendanceRecord;
import com.bantuops.backend.entity.AttendanceViolation;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.repository.AttendanceRepository;
import com.bantuops.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service de gestion des règles d'assiduité avec configuration flexible
 * Conforme aux exigences 3.4, 3.5, 3.6 pour la gestion des règles d'entreprise
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AttendanceRuleService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    // Configuration des règles via application.properties
    @Value("${bantuops.attendance.max-delays-per-month:5}")
    private int maxDelaysPerMonth;

    @Value("${bantuops.attendance.max-absences-per-quarter:10}")
    private int maxAbsencesPerQuarter;

    @Value("${bantuops.attendance.delay-tolerance-minutes:5}")
    private int delayToleranceMinutes;

    @Value("${bantuops.attendance.severe-delay-threshold:60}")
    private int severeDelayThreshold;

    @Value("${bantuops.attendance.justification-required-delay:15}")
    private int justificationRequiredDelay;

    @Value("${bantuops.attendance.auto-warning-enabled:true}")
    private boolean autoWarningEnabled;

    @Value("${bantuops.attendance.disciplinary-action-threshold:3}")
    private int disciplinaryActionThreshold;

    /**
     * Vérifie les règles d'assiduité avec alertes automatiques
     * Exigences: 3.4, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public List<AttendanceViolation> checkAttendanceRules(Long employeeId, LocalDate date) {
        log.info("Vérification des règles d'assiduité pour l'employé ID: {}, date: {}", employeeId, date);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        List<AttendanceViolation> violations = new ArrayList<>();

        // Vérification des retards répétés
        checkRepeatedDelays(employee, date, violations);

        // Vérification des absences fréquentes
        checkFrequentAbsences(employee, date, violations);

        // Vérification des patterns de comportement
        checkAttendancePatterns(employee, date, violations);

        // Vérification des justifications manquantes
        checkMissingJustifications(employee, date, violations);

        // Vérification des violations de politique
        checkPolicyViolations(employee, date, violations);

        // Génération d'alertes automatiques si configuré
        if (autoWarningEnabled && !violations.isEmpty()) {
            generateAutomaticAlerts(employee, violations);
        }

        log.info("Vérification terminée. {} violations trouvées pour l'employé ID: {}",
                violations.size(), employeeId);

        return violations;
    }

    /**
     * Applique la politique d'assiduité avec sanctions graduées
     * Exigences: 3.4, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public void applyAttendancePolicy(Long employeeId, AttendanceViolation violation) {
        log.info("Application de la politique d'assiduité pour l'employé ID: {}, violation: {}",
                employeeId, violation.getViolationType());

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        // Compter les violations précédentes
        int previousViolations = countPreviousViolations(employeeId, violation.getViolationType());

        // Déterminer l'action disciplinaire appropriée
        String disciplinaryAction = determineDisciplinaryAction(violation, previousViolations);

        // Appliquer la sanction
        applyDisciplinaryAction(employee, violation, disciplinaryAction);

        // Audit de l'action
        auditService.logDisciplinaryAction(employeeId, violation.getId(), disciplinaryAction);

        log.info("Politique d'assiduité appliquée pour l'employé ID: {}, action: {}",
                employeeId, disciplinaryAction);
    }

    /**
     * Valide les justifications avec workflow d'approbation
     * Exigences: 3.4, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public boolean validateJustification(Long attendanceRecordId, String justification, String approverUserId) {
        log.info("Validation de justification pour l'enregistrement ID: {}", attendanceRecordId);

        AttendanceRecord record = attendanceRepository.findById(attendanceRecordId)
                .orElseThrow(() -> new BusinessRuleException("Enregistrement d'assiduité non trouvé"));

        // Validation de la justification
        if (justification == null || justification.trim().length() < 10) {
            throw new BusinessRuleException("La justification doit contenir au moins 10 caractères");
        }

        // Vérification des documents requis selon le type d'absence
        boolean documentsRequired = requiresSupportingDocuments(record);
        if (documentsRequired && (justification.length() < 50)) {
            throw new BusinessRuleException("Des documents justificatifs détaillés sont requis pour ce type d'absence");
        }

        // Mise à jour de l'enregistrement
        record.setJustification(justification);
        record.setStatus(AttendanceRecord.AttendanceStatus.UNDER_REVIEW);
        attendanceRepository.save(record);

        // TODO: Implement logJustificationValidation method in AuditService or convert
        // approverUserId to Long
        // auditService.logJustificationValidation(record.getId(), approverUserId,
        // "JUSTIFICATION_SUBMITTED");

        log.info("Justification validée pour l'enregistrement ID: {}", attendanceRecordId);
        return true;
    }

    /**
     * Génère des alertes automatiques pour les violations
     */
    private void generateAutomaticAlerts(Employee employee, List<AttendanceViolation> violations) {
        for (AttendanceViolation violation : violations) {
            String alertMessage = generateAlertMessage(employee, violation);

            // Envoyer l'alerte (implémentation dépendante du système de notification)
            sendAlert(employee, violation, alertMessage);

            log.info("Alerte automatique générée pour l'employé {}: {}",
                    employee.getEmployeeNumber(), alertMessage);
        }
    }

    /**
     * Vérifie les retards répétés selon les règles configurées
     */
    private void checkRepeatedDelays(Employee employee, LocalDate date, List<AttendanceViolation> violations) {
        LocalDate startOfMonth = date.withDayOfMonth(1);
        List<AttendanceRecord> monthlyRecords = attendanceRepository
                .findByEmployeeIdAndWorkDateBetween(employee.getId(), startOfMonth, date);

        long delayCount = monthlyRecords.stream()
                .filter(record -> record.getDelayMinutes() != null && record.getDelayMinutes() > delayToleranceMinutes)
                .count();

        if (delayCount > maxDelaysPerMonth) {
            AttendanceViolation violation = AttendanceViolation.builder()
                    .employee(employee)
                    .violationDate(date)
                    .violationType(AttendanceViolation.ViolationType.PATTERN_VIOLATION)
                    .severityLevel(AttendanceViolation.SeverityLevel.MODERATE)
                    .description(String.format("Retards répétés: %d retards ce mois (limite: %d)",
                            delayCount, maxDelaysPerMonth))
                    .status(AttendanceViolation.ViolationStatus.ACTIVE)
                    .build();
            violations.add(violation);
        }
    }

    /**
     * Vérifie les absences fréquentes selon les règles configurées
     */
    private void checkFrequentAbsences(Employee employee, LocalDate date, List<AttendanceViolation> violations) {
        LocalDate startOfQuarter = date.minusMonths(3);
        List<AttendanceRecord> quarterlyRecords = attendanceRepository
                .findByEmployeeIdAndWorkDateBetween(employee.getId(), startOfQuarter, date);

        long absenceCount = quarterlyRecords.stream()
                .filter(record -> record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT ||
                        record.getAttendanceType() == AttendanceRecord.AttendanceType.UNAUTHORIZED_ABSENCE)
                .count();

        if (absenceCount > maxAbsencesPerQuarter) {
            AttendanceViolation violation = AttendanceViolation.builder()
                    .employee(employee)
                    .violationDate(date)
                    .violationType(AttendanceViolation.ViolationType.PATTERN_VIOLATION)
                    .severityLevel(AttendanceViolation.SeverityLevel.SEVERE)
                    .description(String.format("Absences fréquentes: %d absences ce trimestre (limite: %d)",
                            absenceCount, maxAbsencesPerQuarter))
                    .status(AttendanceViolation.ViolationStatus.ACTIVE)
                    .build();
            violations.add(violation);
        }
    }

    /**
     * Vérifie les patterns de comportement suspects
     */
    private void checkAttendancePatterns(Employee employee, LocalDate date, List<AttendanceViolation> violations) {
        LocalDate startDate = date.minusDays(30);
        List<AttendanceRecord> recentRecords = attendanceRepository
                .findByEmployeeIdAndWorkDateBetween(employee.getId(), startDate, date);

        // Pattern: Absences systématiques le lundi ou vendredi
        checkWeekendPattern(employee, recentRecords, violations, date);

        // Pattern: Retards systématiques après les pauses
        checkPostBreakDelayPattern(employee, recentRecords, violations, date);

        // Pattern: Départs anticipés fréquents
        checkEarlyDeparturePattern(employee, recentRecords, violations, date);
    }

    /**
     * Vérifie les justifications manquantes
     */
    private void checkMissingJustifications(Employee employee, LocalDate date, List<AttendanceViolation> violations) {
        LocalDate startDate = date.minusDays(7); // Vérifier la semaine passée
        List<AttendanceRecord> recentRecords = attendanceRepository
                .findByEmployeeIdAndWorkDateBetween(employee.getId(), startDate, date);

        for (AttendanceRecord record : recentRecords) {
            boolean needsJustification = (record.getDelayMinutes() != null
                    && record.getDelayMinutes() > justificationRequiredDelay) ||
                    record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT ||
                    record.getAttendanceType() == AttendanceRecord.AttendanceType.UNAUTHORIZED_ABSENCE;

            if (needsJustification &&
                    (record.getJustification() == null || record.getJustification().trim().isEmpty())) {

                AttendanceViolation violation = AttendanceViolation.builder()
                        .employee(employee)
                        .attendanceRecord(record)
                        .violationDate(record.getWorkDate())
                        .violationType(AttendanceViolation.ViolationType.MISSING_JUSTIFICATION)
                        .severityLevel(AttendanceViolation.SeverityLevel.MINOR)
                        .description("Justification manquante pour " + record.getAttendanceType().getDescription())
                        .status(AttendanceViolation.ViolationStatus.ACTIVE)
                        .build();
                violations.add(violation);
            }
        }
    }

    /**
     * Vérifie les violations de politique d'entreprise
     */
    private void checkPolicyViolations(Employee employee, LocalDate date, List<AttendanceViolation> violations) {
        // Vérifier les heures supplémentaires non autorisées
        LocalDate startOfWeek = date.minusDays(date.getDayOfWeek().getValue() - 1);
        List<AttendanceRecord> weeklyRecords = attendanceRepository
                .findByEmployeeIdAndWorkDateBetween(employee.getId(), startOfWeek, date);

        double totalOvertimeHours = weeklyRecords.stream()
                .filter(record -> record.getOvertimeHours() != null)
                .mapToDouble(AttendanceRecord::getOvertimeHours)
                .sum();

        // Limite légale au Sénégal: 20h supplémentaires par semaine
        if (totalOvertimeHours > 20.0) {
            AttendanceViolation violation = AttendanceViolation.builder()
                    .employee(employee)
                    .violationDate(date)
                    .violationType(AttendanceViolation.ViolationType.POLICY_BREACH)
                    .severityLevel(AttendanceViolation.SeverityLevel.SEVERE)
                    .description(String.format(
                            "Dépassement des heures supplémentaires autorisées: %.1fh cette semaine (limite: 20h)",
                            totalOvertimeHours))
                    .status(AttendanceViolation.ViolationStatus.ACTIVE)
                    .build();
            violations.add(violation);
        }
    }

    /**
     * Vérifie les patterns d'absence le lundi/vendredi
     */
    private void checkWeekendPattern(Employee employee, List<AttendanceRecord> records,
            List<AttendanceViolation> violations, LocalDate date) {
        Map<Integer, Long> absencesByDayOfWeek = records.stream()
                .filter(record -> record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT)
                .collect(Collectors.groupingBy(
                        record -> record.getWorkDate().getDayOfWeek().getValue(),
                        Collectors.counting()));

        long mondayAbsences = absencesByDayOfWeek.getOrDefault(1, 0L);
        long fridayAbsences = absencesByDayOfWeek.getOrDefault(5, 0L);

        if (mondayAbsences >= 3 || fridayAbsences >= 3) {
            AttendanceViolation violation = AttendanceViolation.builder()
                    .employee(employee)
                    .violationDate(date)
                    .violationType(AttendanceViolation.ViolationType.PATTERN_VIOLATION)
                    .severityLevel(AttendanceViolation.SeverityLevel.MODERATE)
                    .description(String.format("Pattern d'absences suspectes: %d lundis, %d vendredis absents",
                            mondayAbsences, fridayAbsences))
                    .status(AttendanceViolation.ViolationStatus.ACTIVE)
                    .build();
            violations.add(violation);
        }
    }

    /**
     * Vérifie les retards après les pauses
     */
    private void checkPostBreakDelayPattern(Employee employee, List<AttendanceRecord> records,
            List<AttendanceViolation> violations, LocalDate date) {
        // Cette méthode nécessiterait des données sur les pauses
        // Pour l'instant, on vérifie les retards en début d'après-midi
        long afternoonDelays = records.stream()
                .filter(record -> record.getActualStartTime() != null &&
                        record.getActualStartTime().isAfter(java.time.LocalTime.of(14, 0)) &&
                        record.getDelayMinutes() != null && record.getDelayMinutes() > 0)
                .count();

        if (afternoonDelays >= 5) {
            AttendanceViolation violation = AttendanceViolation.builder()
                    .employee(employee)
                    .violationDate(date)
                    .violationType(AttendanceViolation.ViolationType.PATTERN_VIOLATION)
                    .severityLevel(AttendanceViolation.SeverityLevel.MINOR)
                    .description("Pattern de retards après les pauses détecté")
                    .status(AttendanceViolation.ViolationStatus.ACTIVE)
                    .build();
            violations.add(violation);
        }
    }

    /**
     * Vérifie les départs anticipés fréquents
     */
    private void checkEarlyDeparturePattern(Employee employee, List<AttendanceRecord> records,
            List<AttendanceViolation> violations, LocalDate date) {
        long earlyDepartures = records.stream()
                .filter(record -> record.getEarlyDepartureMinutes() != null &&
                        record.getEarlyDepartureMinutes() > 15)
                .count();

        if (earlyDepartures >= 5) {
            AttendanceViolation violation = AttendanceViolation.builder()
                    .employee(employee)
                    .violationDate(date)
                    .violationType(AttendanceViolation.ViolationType.EARLY_DEPARTURE)
                    .severityLevel(AttendanceViolation.SeverityLevel.MODERATE)
                    .description(String.format("Départs anticipés fréquents: %d occurrences", earlyDepartures))
                    .status(AttendanceViolation.ViolationStatus.ACTIVE)
                    .build();
            violations.add(violation);
        }
    }

    /**
     * Compte les violations précédentes du même type
     */
    private int countPreviousViolations(Long employeeId, AttendanceViolation.ViolationType violationType) {
        // Cette méthode nécessiterait un repository pour AttendanceViolation
        // Pour l'instant, on retourne une valeur par défaut
        // TODO: Implement with AttendanceViolationRepository
        return 0;
    }

    /**
     * Détermine l'action disciplinaire appropriée
     */
    private String determineDisciplinaryAction(AttendanceViolation violation, int previousViolations) {
        return switch (violation.getSeverityLevel()) {
            case MINOR -> previousViolations == 0 ? "VERBAL_WARNING" : "WRITTEN_WARNING";
            case MODERATE -> previousViolations < 2 ? "WRITTEN_WARNING" : "FINAL_WARNING";
            case SEVERE -> previousViolations < 1 ? "FINAL_WARNING" : "SUSPENSION";
            case CRITICAL -> "TERMINATION_REVIEW";
        };
    }

    /**
     * Applique l'action disciplinaire
     */
    private void applyDisciplinaryAction(Employee employee, AttendanceViolation violation, String action) {
        violation.setPenaltyApplied(action);
        violation.setWarningIssued("VERBAL_WARNING".equals(action) || "WRITTEN_WARNING".equals(action));
        violation.setDisciplinaryAction(!"VERBAL_WARNING".equals(action));

        // Ici, on pourrait intégrer avec un système de gestion disciplinaire
        log.info("Action disciplinaire appliquée: {} pour l'employé {}", action, employee.getEmployeeNumber());
    }

    /**
     * Vérifie si des documents justificatifs sont requis
     */
    private boolean requiresSupportingDocuments(AttendanceRecord record) {
        return record.getAbsenceType() == AttendanceRecord.AbsenceType.SICK_LEAVE ||
                record.getAbsenceType() == AttendanceRecord.AbsenceType.MATERNITY_LEAVE ||
                record.getAbsenceType() == AttendanceRecord.AbsenceType.PATERNITY_LEAVE ||
                record.getAbsenceType() == AttendanceRecord.AbsenceType.BEREAVEMENT_LEAVE ||
                (record.getDelayMinutes() != null && record.getDelayMinutes() > 120); // Plus de 2h de retard
    }

    /**
     * Génère un message d'alerte personnalisé
     */
    private String generateAlertMessage(Employee employee, AttendanceViolation violation) {
        return String.format("Alerte assiduité - %s (%s): %s - Niveau: %s",
                employee.getFullName(),
                employee.getEmployeeNumber(),
                violation.getDescription(),
                violation.getSeverityLevel().getDescription());
    }

    /**
     * Envoie une alerte (à implémenter selon le système de notification)
     */
    private void sendAlert(Employee employee, AttendanceViolation violation, String message) {
        // Implémentation dépendante du système de notification
        // Pourrait envoyer un email, SMS, notification push, etc.
        log.info("Alerte envoyée: {}", message);
    }
}