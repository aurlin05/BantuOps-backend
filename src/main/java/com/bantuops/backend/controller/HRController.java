package com.bantuops.backend.controller;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.entity.AttendanceRecord;
import com.bantuops.backend.entity.AttendanceViolation;
import com.bantuops.backend.entity.PayrollAdjustment;
import com.bantuops.backend.service.HRManagementService;
import com.bantuops.backend.service.HRReportService;
import com.bantuops.backend.service.PayrollAdjustmentService;
import com.bantuops.backend.service.AttendanceRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des ressources humaines
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4 pour les APIs RH sécurisées
 */
@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Gestion RH", description = "API pour la gestion des ressources humaines et assiduité")
@SecurityRequirement(name = "bearerAuth")
public class HRController {

    private final HRManagementService hrManagementService;
    private final HRReportService hrReportService;
    private final PayrollAdjustmentService payrollAdjustmentService;
    private final AttendanceRuleService attendanceRuleService;

    /**
     * Enregistre la présence d'un employé
     */
    @PostMapping("/attendance/record")
    @Operation(summary = "Enregistrer la présence", 
               description = "Enregistre la présence d'un employé avec validation des horaires")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Présence enregistrée avec succès"),
        @ApiResponse(responseCode = "400", description = "Données de présence invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "404", description = "Employé non trouvé")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<AttendanceRecord> recordAttendance(
            @Valid @RequestBody AttendanceRequest request) {
        
        log.info("Enregistrement de présence pour l'employé {} - date {}", 
                request.getEmployeeId(), request.getWorkDate());
        
        try {
            AttendanceRecord record = hrManagementService.recordAttendance(
                request.getEmployeeId(), 
                request.toAttendanceData()
            );
            
            log.info("Présence enregistrée avec succès pour l'employé {}", request.getEmployeeId());
            return ResponseEntity.status(HttpStatus.CREATED).body(record);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de présence: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calcule les retards d'un employé
     */
    @PostMapping("/attendance/calculate-delay")
    @Operation(summary = "Calculer les retards", 
               description = "Calcule les retards et pénalités selon les règles d'entreprise")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<DelayCalculation> calculateDelay(
            @Parameter(description = "ID de l'employé") 
            @RequestParam Long employeeId,
            @Parameter(description = "Date de travail") 
            @RequestParam LocalDate workDate) {
        
        log.info("Calcul de retard pour l'employé {} - date {}", employeeId, workDate);
        
        try {
            DelayCalculation calculation = hrManagementService.calculateDelay(employeeId, workDate);
            return ResponseEntity.ok(calculation);
            
        } catch (Exception e) {
            log.error("Erreur lors du calcul de retard: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enregistre une absence
     */
    @PostMapping("/absence/record")
    @Operation(summary = "Enregistrer une absence", 
               description = "Enregistre une absence avec justification")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<AttendanceRecord> recordAbsence(
            @Valid @RequestBody AbsenceRequest request) {
        
        log.info("Enregistrement d'absence pour l'employé {} - période {} à {}", 
                request.getEmployeeId(), request.getStartDate(), request.getEndDate());
        
        try {
            AttendanceRecord record = hrManagementService.recordAbsence(
                request.getEmployeeId(), 
                request
            );
            
            log.info("Absence enregistrée avec succès pour l'employé {}", request.getEmployeeId());
            return ResponseEntity.status(HttpStatus.CREATED).body(record);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement d'absence: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Vérifie les règles d'assiduité
     */
    @GetMapping("/attendance/check-rules/{employeeId}")
    @Operation(summary = "Vérifier les règles d'assiduité", 
               description = "Vérifie les violations des règles d'assiduité pour un employé")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<List<AttendanceViolation>> checkAttendanceRules(
            @Parameter(description = "ID de l'employé") 
            @PathVariable Long employeeId,
            @Parameter(description = "Date à vérifier") 
            @RequestParam LocalDate date) {
        
        log.info("Vérification des règles d'assiduité pour l'employé {} - date {}", employeeId, date);
        
        try {
            List<AttendanceViolation> violations = hrManagementService.checkAttendanceRules(employeeId, date);
            return ResponseEntity.ok(violations);
            
        } catch (Exception e) {
            log.error("Erreur lors de la vérification des règles: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère un rapport d'assiduité
     */
    @PostMapping("/reports/attendance")
    @Operation(summary = "Générer un rapport d'assiduité", 
               description = "Génère un rapport d'assiduité avec statistiques")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<AttendanceReport> generateAttendanceReport(
            @Parameter(description = "ID de l'employé (optionnel)") 
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = "Date de début") 
            @RequestParam LocalDate startDate,
            @Parameter(description = "Date de fin") 
            @RequestParam LocalDate endDate) {
        
        log.info("Génération de rapport d'assiduité pour la période {} - {}", startDate, endDate);
        
        try {
            AttendanceReport report = hrReportService.generateAttendanceReport(
                employeeId, startDate, endDate
            );
            
            log.info("Rapport d'assiduité généré avec succès");
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calcule les ajustements de paie liés à l'assiduité
     */
    @PostMapping("/payroll/calculate-adjustments")
    @Operation(summary = "Calculer les ajustements de paie", 
               description = "Calcule les ajustements de paie basés sur l'assiduité")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<List<PayrollAdjustment>> calculateAttendanceAdjustments(
            @Parameter(description = "ID de l'employé") 
            @RequestParam Long employeeId,
            @Parameter(description = "Période au format YYYY-MM") 
            @RequestParam YearMonth period) {
        
        log.info("Calcul d'ajustements de paie pour l'employé {} - période {}", employeeId, period);
        
        try {
            List<PayrollAdjustment> adjustments = payrollAdjustmentService.calculateAttendanceAdjustments(
                employeeId, period
            );
            
            return ResponseEntity.ok(adjustments);
            
        } catch (Exception e) {
            log.error("Erreur lors du calcul d'ajustements: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère l'historique d'assiduité d'un employé
     */
    @GetMapping("/attendance/employee/{employeeId}/history")
    @Operation(summary = "Historique d'assiduité", 
               description = "Récupère l'historique paginé d'assiduité d'un employé")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Page<AttendanceRecord>> getAttendanceHistory(
            @Parameter(description = "ID de l'employé") 
            @PathVariable Long employeeId,
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(description = "Date de début") 
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Date de fin") 
            @RequestParam(required = false) LocalDate endDate) {
        
        log.info("Récupération de l'historique d'assiduité pour l'employé {}", employeeId);
        
        try {
            Page<AttendanceRecord> history = hrManagementService.getAttendanceHistory(
                employeeId, pageable, startDate, endDate
            );
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'historique: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Configure les règles d'assiduité
     */
    @PostMapping("/attendance/rules/configure")
    @Operation(summary = "Configurer les règles d'assiduité", 
               description = "Configure les règles d'assiduité de l'entreprise")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> configureAttendanceRules(
            @Valid @RequestBody Map<String, Object> rulesConfig) {
        
        log.info("Configuration des règles d'assiduité");
        
        try {
            Map<String, Object> result = attendanceRuleService.configureRules(rulesConfig);
            
            log.info("Règles d'assiduité configurées avec succès");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Erreur lors de la configuration des règles: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère les statistiques RH du tableau de bord
     */
    @GetMapping("/dashboard/metrics")
    @Operation(summary = "Métriques RH du tableau de bord", 
               description = "Récupère les métriques RH pour le tableau de bord")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Map<String, Object>> getHRDashboardMetrics() {
        
        log.info("Récupération des métriques du tableau de bord RH");
        
        try {
            Map<String, Object> metrics = hrReportService.getDashboardMetrics();
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des métriques RH: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Exporte les données RH
     */
    @PostMapping("/export")
    @Operation(summary = "Exporter les données RH", 
               description = "Exporte les données RH de manière sécurisée")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> exportHRData(
            @Parameter(description = "Date de début") 
            @RequestParam LocalDate startDate,
            @Parameter(description = "Date de fin") 
            @RequestParam LocalDate endDate,
            @Parameter(description = "Type de données à exporter") 
            @RequestParam(required = false) String dataType) {
        
        log.info("Export de données RH demandé pour la période {} - {}", startDate, endDate);
        
        try {
            Map<String, Object> exportData = hrReportService.exportHRData(startDate, endDate, dataType);
            
            log.info("Export de données RH terminé avec succès");
            return ResponseEntity.ok(exportData);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'export des données RH: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Valide une justification d'absence
     */
    @PostMapping("/absence/validate-justification")
    @Operation(summary = "Valider une justification d'absence", 
               description = "Valide ou rejette une justification d'absence")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Map<String, Object>> validateJustification(
            @Parameter(description = "ID de l'enregistrement d'assiduité") 
            @RequestParam Long attendanceRecordId,
            @Parameter(description = "Statut de validation") 
            @RequestParam String validationStatus,
            @Parameter(description = "Commentaire de validation") 
            @RequestParam(required = false) String comment) {
        
        log.info("Validation de justification pour l'enregistrement {}", attendanceRecordId);
        
        try {
            Map<String, Object> result = hrManagementService.validateJustification(
                attendanceRecordId, validationStatus, comment
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Erreur lors de la validation de justification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}