package com.bantuops.backend.service;

import com.bantuops.backend.dto.AbsenceRequest;
import com.bantuops.backend.dto.AttendanceData;
import com.bantuops.backend.dto.DelayCalculation;
import com.bantuops.backend.entity.AttendanceRecord;
import com.bantuops.backend.entity.AttendanceViolation;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.WorkSchedule;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.repository.AttendanceRepository;
import com.bantuops.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service de gestion RH avec méthodes sécurisées pour l'assiduité
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4 pour la gestion des retards et absences
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class HRManagementService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuditService auditService;

    /**
     * Enregistre l'assiduité d'un employé avec validation des horaires
     * Exigences: 3.1, 3.2, 3.3, 3.4
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public AttendanceRecord recordAttendance(Long employeeId, AttendanceData attendanceData) {
        log.info("Enregistrement de l'assiduité pour l'employé ID: {}, date: {}", 
                employeeId, attendanceData.getWorkDate());

        // Validation de l'employé
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        if (!employee.isCurrentlyEmployed()) {
            throw new BusinessRuleException("Impossible d'enregistrer l'assiduité pour un employé inactif");
        }

        // Vérification de l'existence d'un enregistrement pour cette date
        if (attendanceRepository.existsByEmployeeIdAndWorkDate(employeeId, attendanceData.getWorkDate())) {
            throw new BusinessRuleException("Un enregistrement d'assiduité existe déjà pour cette date");
        }

        // Validation des données d'assiduité
        businessRuleValidator.validateAttendanceData(attendanceData);

        // Création de l'enregistrement d'assiduité
        AttendanceRecord attendanceRecord = AttendanceRecord.builder()
            .employee(employee)
            .workDate(attendanceData.getWorkDate())
            .scheduledStartTime(parseTime(employee.getWorkStartTime()))
            .scheduledEndTime(parseTime(employee.getWorkEndTime()))
            .actualStartTime(attendanceData.getActualStartTime())
            .actualEndTime(attendanceData.getActualEndTime())
            .attendanceType(determineAttendanceType(attendanceData))
            .justification(attendanceData.getJustification())
            .status(AttendanceRecord.AttendanceStatus.PENDING)
            .build();

        // Calcul des retards et heures travaillées
        calculateAttendanceMetrics(attendanceRecord);

        // Sauvegarde
        AttendanceRecord savedRecord = attendanceRepository.save(attendanceRecord);

        // Audit
        auditService.logAttendanceRecord(employeeId, savedRecord.getId(), "ATTENDANCE_RECORDED");

        log.info("Assiduité enregistrée avec succès pour l'employé ID: {}, record ID: {}", 
                employeeId, savedRecord.getId());

        return savedRecord;
    }

    /**
     * Calcule le retard d'un employé selon les règles d'entreprise
     * Exigences: 3.1, 3.2, 3.3, 3.4
     */
    public DelayCalculation calculateDelay(AttendanceRecord record, WorkSchedule schedule) {
        log.debug("Calcul du retard pour l'enregistrement ID: {}", record.getId());

        if (record.getActualStartTime() == null || record.getScheduledStartTime() == null) {
            return DelayCalculation.builder()
                .employeeId(record.getEmployee().getId())
                .employeeNumber(record.getEmployee().getEmployeeNumber())
                .employeeName(record.getEmployee().getFullName())
                .scheduledStartTime(record.getScheduledStartTime())
                .actualStartTime(record.getActualStartTime())
                .delayMinutes(0)
                .delayCategory("NONE")
                .requiresJustification(false)
                .requiresApproval(false)
                .build();
        }

        // Calcul du retard en minutes
        Duration delay = Duration.between(record.getScheduledStartTime(), record.getActualStartTime());
        int delayMinutes = (int) delay.toMinutes();

        if (delayMinutes <= 0) {
            return DelayCalculation.builder()
                .employeeId(record.getEmployee().getId())
                .employeeNumber(record.getEmployee().getEmployeeNumber())
                .employeeName(record.getEmployee().getFullName())
                .scheduledStartTime(record.getScheduledStartTime())
                .actualStartTime(record.getActualStartTime())
                .delayMinutes(0)
                .delayCategory("NONE")
                .requiresJustification(false)
                .requiresApproval(false)
                .build();
        }

        // Application des règles de tolérance (5 minutes de grâce par défaut)
        int toleranceMinutes = schedule != null && schedule.getFlexibilityMinutes() != null ? 
            schedule.getFlexibilityMinutes() : 5;
        int effectiveDelay = Math.max(0, delayMinutes - toleranceMinutes);

        // Calcul de la pénalité selon les règles configurées
        java.math.BigDecimal penaltyAmount = calculateDelayPenalty(effectiveDelay, record.getEmployee().getBaseSalary());

        // Détermination de la catégorie de retard
        String delayCategory = determineDelayCategory(effectiveDelay);
        
        return DelayCalculation.builder()
            .employeeId(record.getEmployee().getId())
            .employeeNumber(record.getEmployee().getEmployeeNumber())
            .employeeName(record.getEmployee().getFullName())
            .scheduledStartTime(record.getScheduledStartTime())
            .actualStartTime(record.getActualStartTime())
            .delayMinutes(effectiveDelay)
            .delayCategory(delayCategory)
            .penaltyAmount(penaltyAmount)
            .requiresJustification(effectiveDelay > 15)
            .requiresApproval(effectiveDelay > 30)
            .applicableRule("Règlement intérieur - Article sur les retards")
            .recommendedAction(getRecommendedAction(delayCategory))
            .build();
    }

    /**
     * Enregistre une absence avec gestion des justificatifs
     * Exigences: 3.1, 3.2, 3.3, 3.4
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public AttendanceRecord recordAbsence(Long employeeId, AbsenceRequest absenceRequest) {
        log.info("Enregistrement d'absence pour l'employé ID: {}, date: {}, type: {}", 
                employeeId, absenceRequest.getStartDate(), absenceRequest.getAbsenceType());

        // Validation de l'employé
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        // Validation de la demande d'absence
        businessRuleValidator.validateAbsenceRequest(absenceRequest);

        // Vérification de l'existence d'un enregistrement pour cette date
        Optional<AttendanceRecord> existingRecord = attendanceRepository
            .findByEmployeeIdAndWorkDate(employeeId, absenceRequest.getStartDate());

        AttendanceRecord attendanceRecord;
        if (existingRecord.isPresent()) {
            // Mise à jour de l'enregistrement existant
            attendanceRecord = existingRecord.get();
            attendanceRecord.setAttendanceType(AttendanceRecord.AttendanceType.ABSENT);
            attendanceRecord.setAbsenceType(mapAbsenceType(absenceRequest.getAbsenceType()));
            attendanceRecord.setJustification(absenceRequest.getReason());
            attendanceRecord.setIsPaidAbsence(absenceRequest.getIsPaidAbsence());
            attendanceRecord.setStatus(AttendanceRecord.AttendanceStatus.PENDING);
        } else {
            // Création d'un nouvel enregistrement
            attendanceRecord = AttendanceRecord.builder()
                .employee(employee)
                .workDate(absenceRequest.getStartDate())
                .scheduledStartTime(parseTime(employee.getWorkStartTime()))
                .scheduledEndTime(parseTime(employee.getWorkEndTime()))
                .attendanceType(AttendanceRecord.AttendanceType.ABSENT)
                .absenceType(mapAbsenceType(absenceRequest.getAbsenceType()))
                .justification(absenceRequest.getReason())
                .isPaidAbsence(absenceRequest.getIsPaidAbsence())
                .status(AttendanceRecord.AttendanceStatus.PENDING)
                .totalHoursWorked(0.0)
                .build();
        }

        // Sauvegarde
        AttendanceRecord savedRecord = attendanceRepository.save(attendanceRecord);

        // Audit
        auditService.logAttendanceRecord(employeeId, savedRecord.getId(), "ABSENCE_RECORDED");

        log.info("Absence enregistrée avec succès pour l'employé ID: {}, record ID: {}", 
                employeeId, savedRecord.getId());

        return savedRecord;
    }

    /**
     * Vérifie les règles d'assiduité et génère les violations
     * Exigences: 3.4, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public List<AttendanceViolation> checkAttendanceRules(Long employeeId, LocalDate date) {
        log.debug("Vérification des règles d'assiduité pour l'employé ID: {}, date: {}", employeeId, date);

        List<AttendanceViolation> violations = new ArrayList<>();

        // Récupération de l'enregistrement d'assiduité
        Optional<AttendanceRecord> recordOpt = attendanceRepository.findByEmployeeIdAndWorkDate(employeeId, date);
        
        if (recordOpt.isEmpty()) {
            // Absence non justifiée
            violations.add(createViolation(employeeId, date, 
                AttendanceViolation.ViolationType.UNAUTHORIZED_ABSENCE,
                "Absence non déclarée"));
            return violations;
        }

        AttendanceRecord record = recordOpt.get();

        // Vérification des retards répétés
        checkRepeatedDelays(employeeId, date, violations);

        // Vérification des absences fréquentes
        checkFrequentAbsences(employeeId, date, violations);

        // Vérification des justifications manquantes
        checkMissingJustifications(record, violations);

        // Vérification des heures supplémentaires non autorisées
        checkUnauthorizedOvertime(record, violations);

        log.debug("Vérification terminée. {} violations trouvées pour l'employé ID: {}", 
                violations.size(), employeeId);

        return violations;
    }

    // Méthodes utilitaires privées

    private AttendanceRecord.AttendanceType determineAttendanceType(AttendanceData attendanceData) {
        if (attendanceData.getActualStartTime() == null && attendanceData.getActualEndTime() == null) {
            return AttendanceRecord.AttendanceType.ABSENT;
        }
        
        if (attendanceData.getActualStartTime() != null && attendanceData.getActualEndTime() != null) {
            // Vérifier si c'est une demi-journée
            Duration workedDuration = Duration.between(
                attendanceData.getActualStartTime(), 
                attendanceData.getActualEndTime()
            );
            if (workedDuration.toHours() < 4) {
                return AttendanceRecord.AttendanceType.HALF_DAY;
            }
        }

        return AttendanceRecord.AttendanceType.PRESENT;
    }

    private void calculateAttendanceMetrics(AttendanceRecord record) {
        if (record.getActualStartTime() != null && record.getActualEndTime() != null) {
            // Calcul des heures travaillées
            Duration workedDuration = Duration.between(
                record.getActualStartTime(), 
                record.getActualEndTime()
            );
            record.setTotalHoursWorked(workedDuration.toMinutes() / 60.0);

            // Calcul du retard
            if (record.getScheduledStartTime() != null) {
                Duration delay = Duration.between(
                    record.getScheduledStartTime(), 
                    record.getActualStartTime()
                );
                record.setDelayMinutes(Math.max(0, (int) delay.toMinutes()));
            }

            // Calcul du départ anticipé
            if (record.getScheduledEndTime() != null) {
                Duration earlyDeparture = Duration.between(
                    record.getActualEndTime(),
                    record.getScheduledEndTime()
                );
                record.setEarlyDepartureMinutes(Math.max(0, (int) earlyDeparture.toMinutes()));
            }

            // Calcul des heures supplémentaires (si dépassement de 8h/jour)
            double standardHours = 8.0;
            if (record.getTotalHoursWorked() > standardHours) {
                record.setOvertimeHours(record.getTotalHoursWorked() - standardHours);
            }
        }
    }

    private LocalTime parseTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            log.warn("Impossible de parser l'heure: {}", timeString);
            return null;
        }
    }

    private java.math.BigDecimal calculateDelayPenalty(int delayMinutes, java.math.BigDecimal baseSalary) {
        if (delayMinutes <= 0 || baseSalary == null) {
            return null;
        }

        // Calcul de la pénalité: 1/30 du salaire journalier par heure de retard
        // Salaire journalier = salaire mensuel / 22 jours ouvrables
        // Salaire horaire = salaire journalier / 8 heures
        java.math.BigDecimal dailySalary = baseSalary.divide(java.math.BigDecimal.valueOf(22), 2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal hourlySalary = dailySalary.divide(java.math.BigDecimal.valueOf(8), 2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal penaltyRate = hourlySalary.divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);

        return penaltyRate.multiply(java.math.BigDecimal.valueOf(delayMinutes));
    }

    private String determineDelayCategory(int delayMinutes) {
        if (delayMinutes <= 0) {
            return "NONE";
        } else if (delayMinutes <= 15) {
            return "MINOR";
        } else if (delayMinutes <= 60) {
            return "MODERATE";
        } else {
            return "SEVERE";
        }
    }

    private String getRecommendedAction(String delayCategory) {
        return switch (delayCategory) {
            case "MINOR" -> "Avertissement verbal";
            case "MODERATE" -> "Avertissement écrit";
            case "SEVERE" -> "Sanction disciplinaire";
            default -> "Aucune action requise";
        };
    }

    private void checkRepeatedDelays(Long employeeId, LocalDate date, List<AttendanceViolation> violations) {
        // Vérifier les retards des 30 derniers jours
        LocalDate startDate = date.minusDays(30);
        List<AttendanceRecord> recentRecords = attendanceRepository
            .findByEmployeeIdAndWorkDateBetween(employeeId, startDate, date);

        long delayCount = recentRecords.stream()
            .filter(record -> record.getDelayMinutes() != null && record.getDelayMinutes() > 0)
            .count();

        // Seuil: plus de 5 retards en 30 jours
        if (delayCount > 5) {
            violations.add(createViolation(employeeId, date,
                AttendanceViolation.ViolationType.PATTERN_VIOLATION,
                String.format("Retards répétés: %d retards en 30 jours", delayCount)));
        }
    }

    private void checkFrequentAbsences(Long employeeId, LocalDate date, List<AttendanceViolation> violations) {
        // Vérifier les absences des 90 derniers jours
        LocalDate startDate = date.minusDays(90);
        List<AttendanceRecord> recentRecords = attendanceRepository
            .findByEmployeeIdAndWorkDateBetween(employeeId, startDate, date);

        long absenceCount = recentRecords.stream()
            .filter(record -> record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT)
            .count();

        // Seuil: plus de 10 absences en 90 jours
        if (absenceCount > 10) {
            violations.add(createViolation(employeeId, date,
                AttendanceViolation.ViolationType.PATTERN_VIOLATION,
                String.format("Absences fréquentes: %d absences en 90 jours", absenceCount)));
        }
    }

    private void checkMissingJustifications(AttendanceRecord record, List<AttendanceViolation> violations) {
        boolean needsJustification = record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT ||
                                   record.getAttendanceType() == AttendanceRecord.AttendanceType.LATE ||
                                   (record.getDelayMinutes() != null && record.getDelayMinutes() > 30);

        if (needsJustification && 
            (record.getJustification() == null || record.getJustification().trim().isEmpty())) {
            violations.add(createViolation(record.getEmployee().getId(), record.getWorkDate(),
                AttendanceViolation.ViolationType.MISSING_JUSTIFICATION,
                "Justification manquante pour " + record.getAttendanceType().getDescription()));
        }
    }

    private void checkUnauthorizedOvertime(AttendanceRecord record, List<AttendanceViolation> violations) {
        if (record.getOvertimeHours() != null && record.getOvertimeHours() > 2.0) {
            // Plus de 2h supplémentaires sans autorisation préalable
            violations.add(createViolation(record.getEmployee().getId(), record.getWorkDate(),
                AttendanceViolation.ViolationType.POLICY_BREACH,
                String.format("Heures supplémentaires non autorisées: %.1fh", record.getOvertimeHours())));
        }
    }

    private AttendanceViolation createViolation(Long employeeId, LocalDate date, 
                                              AttendanceViolation.ViolationType type, String description) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));
            
        return AttendanceViolation.builder()
            .employee(employee)
            .violationDate(date)
            .violationType(type)
            .description(description)
            .severityLevel(determineSeverity(type))
            .status(AttendanceViolation.ViolationStatus.ACTIVE)
            .build();
    }

    private AttendanceViolation.SeverityLevel determineSeverity(AttendanceViolation.ViolationType type) {
        return switch (type) {
            case UNAUTHORIZED_ABSENCE -> AttendanceViolation.SeverityLevel.SEVERE;
            case PATTERN_VIOLATION -> AttendanceViolation.SeverityLevel.MODERATE;
            case MISSING_JUSTIFICATION, POLICY_BREACH -> AttendanceViolation.SeverityLevel.MINOR;
            default -> AttendanceViolation.SeverityLevel.MINOR;
        };
    }

    private AttendanceRecord.AbsenceType mapAbsenceType(String absenceTypeString) {
        if (absenceTypeString == null) {
            return AttendanceRecord.AbsenceType.OTHER;
        }
        
        return switch (absenceTypeString) {
            case "SICK_LEAVE" -> AttendanceRecord.AbsenceType.SICK_LEAVE;
            case "ANNUAL_LEAVE" -> AttendanceRecord.AbsenceType.ANNUAL_LEAVE;
            case "MATERNITY_LEAVE" -> AttendanceRecord.AbsenceType.MATERNITY_LEAVE;
            case "PATERNITY_LEAVE" -> AttendanceRecord.AbsenceType.PATERNITY_LEAVE;
            case "BEREAVEMENT_LEAVE" -> AttendanceRecord.AbsenceType.BEREAVEMENT_LEAVE;
            case "PERSONAL_LEAVE" -> AttendanceRecord.AbsenceType.PERSONAL_LEAVE;
            case "TRAINING" -> AttendanceRecord.AbsenceType.TRAINING;
            case "MISSION" -> AttendanceRecord.AbsenceType.MISSION;
            default -> AttendanceRecord.AbsenceType.OTHER;
        };
    }
}