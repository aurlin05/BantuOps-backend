package com.bantuops.backend.controller;

import com.bantuops.backend.dto.EmployeeRequest;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.service.BusinessRuleValidator;
import com.bantuops.backend.repository.EmployeeRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur REST pour la gestion des employés
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4 pour les APIs employés sécurisées
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Gestion des Employés", description = "API pour la gestion des employés")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final BusinessRuleValidator businessRuleValidator;

    /**
     * Crée un nouvel employé
     */
    @PostMapping
    @Operation(summary = "Créer un employé", 
               description = "Crée un nouvel employé avec validation complète des données")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Employé créé avec succès"),
        @ApiResponse(responseCode = "400", description = "Données d'employé invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "409", description = "Employé déjà existant")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Employee> createEmployee(
            @Valid @RequestBody EmployeeRequest request) {
        
        log.info("Création d'un nouvel employé: {} {}", request.getFirstName(), request.getLastName());
        
        try {
            // Validation des règles métier
            var validationResult = businessRuleValidator.validateEmployeeData(request);
            if (!validationResult.isValid()) {
                log.warn("Validation échouée pour l'employé: {}", validationResult.getErrors());
                return ResponseEntity.badRequest().build();
            }

            // Vérification de l'unicité du numéro d'employé
            if (employeeRepository.existsByEmployeeNumber(request.getEmployeeNumber())) {
                log.warn("Numéro d'employé déjà existant: {}", request.getEmployeeNumber());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            // Vérification de l'unicité de l'email
            if (employeeRepository.existsByPersonalInfoEmail(request.getEmail())) {
                log.warn("Email déjà existant: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            // Création de l'employé
            Employee employee = createEmployeeFromRequest(request);
            Employee savedEmployee = employeeRepository.save(employee);
            
            log.info("Employé créé avec succès: ID {}", savedEmployee.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(savedEmployee);
            
        } catch (Exception e) {
            log.error("Erreur lors de la création de l'employé: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Met à jour un employé existant
     */
    @PutMapping("/{employeeId}")
    @Operation(summary = "Mettre à jour un employé", 
               description = "Met à jour les informations d'un employé existant")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Employee> updateEmployee(
            @Parameter(description = "ID de l'employé") 
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeRequest request) {
        
        log.info("Mise à jour de l'employé {}: {} {}", employeeId, request.getFirstName(), request.getLastName());
        
        try {
            Optional<Employee> existingEmployee = employeeRepository.findById(employeeId);
            if (existingEmployee.isEmpty()) {
                log.warn("Employé non trouvé: {}", employeeId);
                return ResponseEntity.notFound().build();
            }

            // Validation des règles métier
            var validationResult = businessRuleValidator.validateEmployeeData(request);
            if (!validationResult.isValid()) {
                log.warn("Validation échouée pour l'employé: {}", validationResult.getErrors());
                return ResponseEntity.badRequest().build();
            }

            // Mise à jour de l'employé
            Employee employee = updateEmployeeFromRequest(existingEmployee.get(), request);
            Employee updatedEmployee = employeeRepository.save(employee);
            
            log.info("Employé mis à jour avec succès: ID {}", updatedEmployee.getId());
            return ResponseEntity.ok(updatedEmployee);
            
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour de l'employé: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère un employé par son ID
     */
    @GetMapping("/{employeeId}")
    @Operation(summary = "Récupérer un employé", 
               description = "Récupère les détails d'un employé par son ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Employee> getEmployee(
            @Parameter(description = "ID de l'employé") 
            @PathVariable Long employeeId) {
        
        log.info("Récupération de l'employé {}", employeeId);
        
        try {
            Optional<Employee> employee = employeeRepository.findById(employeeId);
            if (employee.isEmpty()) {
                log.warn("Employé non trouvé: {}", employeeId);
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(employee.get());
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'employé: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère la liste des employés avec pagination
     */
    @GetMapping
    @Operation(summary = "Lister les employés", 
               description = "Récupère la liste paginée des employés")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Page<Employee>> getEmployees(
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(description = "Filtrer par statut actif") 
            @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Filtrer par département") 
            @RequestParam(required = false) String department,
            @Parameter(description = "Recherche par nom") 
            @RequestParam(required = false) String search) {
        
        log.info("Récupération de la liste des employés");
        
        try {
            Page<Employee> employees;
            
            if (search != null && !search.trim().isEmpty()) {
                employees = employeeRepository.findByPersonalInfoFirstNameContainingIgnoreCaseOrPersonalInfoLastNameContainingIgnoreCase(
                    search, search, pageable);
            } else if (department != null && !department.trim().isEmpty()) {
                employees = employeeRepository.findByEmploymentInfoDepartment(department, pageable);
            } else if (isActive != null) {
                employees = employeeRepository.findByEmploymentInfoIsActive(isActive, pageable);
            } else {
                employees = employeeRepository.findAll(pageable);
            }
            
            return ResponseEntity.ok(employees);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des employés: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Désactive un employé (soft delete)
     */
    @PatchMapping("/{employeeId}/deactivate")
    @Operation(summary = "Désactiver un employé", 
               description = "Désactive un employé sans le supprimer définitivement")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Employee> deactivateEmployee(
            @Parameter(description = "ID de l'employé") 
            @PathVariable Long employeeId) {
        
        log.info("Désactivation de l'employé {}", employeeId);
        
        try {
            Optional<Employee> existingEmployee = employeeRepository.findById(employeeId);
            if (existingEmployee.isEmpty()) {
                log.warn("Employé non trouvé: {}", employeeId);
                return ResponseEntity.notFound().build();
            }

            Employee employee = existingEmployee.get();
            employee.getEmploymentInfo().setIsActive(false);
            Employee deactivatedEmployee = employeeRepository.save(employee);
            
            log.info("Employé désactivé avec succès: ID {}", deactivatedEmployee.getId());
            return ResponseEntity.ok(deactivatedEmployee);
            
        } catch (Exception e) {
            log.error("Erreur lors de la désactivation de l'employé: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Réactive un employé
     */
    @PatchMapping("/{employeeId}/activate")
    @Operation(summary = "Réactiver un employé", 
               description = "Réactive un employé précédemment désactivé")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Employee> activateEmployee(
            @Parameter(description = "ID de l'employé") 
            @PathVariable Long employeeId) {
        
        log.info("Réactivation de l'employé {}", employeeId);
        
        try {
            Optional<Employee> existingEmployee = employeeRepository.findById(employeeId);
            if (existingEmployee.isEmpty()) {
                log.warn("Employé non trouvé: {}", employeeId);
                return ResponseEntity.notFound().build();
            }

            Employee employee = existingEmployee.get();
            employee.getEmploymentInfo().setIsActive(true);
            Employee activatedEmployee = employeeRepository.save(employee);
            
            log.info("Employé réactivé avec succès: ID {}", activatedEmployee.getId());
            return ResponseEntity.ok(activatedEmployee);
            
        } catch (Exception e) {
            log.error("Erreur lors de la réactivation de l'employé: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Valide les données d'un employé
     */
    @PostMapping("/validate")
    @Operation(summary = "Valider les données d'employé", 
               description = "Valide les données d'employé selon les règles métier sénégalaises")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Map<String, Object>> validateEmployeeData(
            @Valid @RequestBody EmployeeRequest request) {
        
        log.info("Validation des données pour l'employé: {} {}", request.getFirstName(), request.getLastName());
        
        try {
            var validationResult = businessRuleValidator.validateEmployeeData(request);
            
            Map<String, Object> response = Map.of(
                "valid", validationResult.isValid(),
                "errors", validationResult.getErrors(),
                "warnings", validationResult.getWarnings()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur lors de la validation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Recherche d'employés par critères
     */
    @PostMapping("/search")
    @Operation(summary = "Rechercher des employés", 
               description = "Recherche d'employés selon des critères avancés")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Page<Employee>> searchEmployees(
            @RequestBody Map<String, Object> searchCriteria,
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.info("Recherche d'employés avec critères: {}", searchCriteria);
        
        try {
            // Implémentation de la recherche avancée
            Page<Employee> employees = employeeRepository.findAll(pageable); // Placeholder
            
            return ResponseEntity.ok(employees);
            
        } catch (Exception e) {
            log.error("Erreur lors de la recherche d'employés: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère les statistiques des employés
     */
    @GetMapping("/statistics")
    @Operation(summary = "Statistiques des employés", 
               description = "Récupère les statistiques générales des employés")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Map<String, Object>> getEmployeeStatistics() {
        
        log.info("Récupération des statistiques des employés");
        
        try {
            long totalEmployees = employeeRepository.count();
            long activeEmployees = employeeRepository.countByEmploymentInfoIsActive(true);
            long inactiveEmployees = totalEmployees - activeEmployees;
            
            Map<String, Object> statistics = Map.of(
                "totalEmployees", totalEmployees,
                "activeEmployees", activeEmployees,
                "inactiveEmployees", inactiveEmployees,
                "departmentCounts", employeeRepository.countByDepartment()
            );
            
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des statistiques: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Méthode utilitaire pour créer un employé à partir d'une requête
     */
    private Employee createEmployeeFromRequest(EmployeeRequest request) {
        // Cette méthode devrait être implémentée avec la logique de mapping
        // Pour l'instant, on retourne un placeholder
        return new Employee();
    }

    /**
     * Méthode utilitaire pour mettre à jour un employé à partir d'une requête
     */
    private Employee updateEmployeeFromRequest(Employee employee, EmployeeRequest request) {
        // Cette méthode devrait être implémentée avec la logique de mapping
        // Pour l'instant, on retourne l'employé existant
        return employee;
    }
}