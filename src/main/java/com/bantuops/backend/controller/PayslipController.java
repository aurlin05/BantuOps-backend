package com.bantuops.backend.controller;

import com.bantuops.backend.dto.PayrollResult;
import com.bantuops.backend.dto.PayslipDocument;
import com.bantuops.backend.service.PayslipGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * Contrôleur REST pour la génération de bulletins de paie
 * Conforme aux exigences 1.3, 2.3, 2.4 pour la génération sécurisée de bulletins
 */
@RestController
@RequestMapping("/api/payslips")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Bulletins de Paie", description = "API pour la génération et gestion des bulletins de paie")
@SecurityRequirement(name = "bearerAuth")
public class PayslipController {

    private final PayslipGenerationService payslipGenerationService;

    /**
     * Génère un bulletin de paie standard
     */
    @PostMapping("/generate")
    @Operation(summary = "Générer un bulletin de paie", 
               description = "Génère un bulletin de paie conforme au format officiel sénégalais")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bulletin généré avec succès"),
        @ApiResponse(responseCode = "400", description = "Données de paie invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "404", description = "Employé non trouvé")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<PayslipDocument> generatePayslip(
            @Valid @RequestBody PayrollResult payrollResult) {
        
        log.info("Demande de génération de bulletin pour l'employé {}", payrollResult.getEmployeeId());
        
        try {
            PayslipDocument document = payslipGenerationService.generatePayslip(payrollResult);
            return ResponseEntity.ok(document);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du bulletin: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère un bulletin de paie avec template spécifique
     */
    @PostMapping("/generate/template/{templateName}")
    @Operation(summary = "Générer un bulletin avec template", 
               description = "Génère un bulletin de paie avec un template spécifique")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<PayslipDocument> generatePayslipWithTemplate(
            @Valid @RequestBody PayrollResult payrollResult,
            @Parameter(description = "Nom du template à utiliser") 
            @PathVariable String templateName) {
        
        log.info("Génération avec template {} pour l'employé {}", templateName, payrollResult.getEmployeeId());
        
        try {
            PayslipDocument document = payslipGenerationService.generatePayslipWithTemplate(
                payrollResult, templateName);
            return ResponseEntity.ok(document);
        } catch (Exception e) {
            log.error("Erreur lors de la génération avec template: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère un PDF sécurisé du bulletin
     */
    @PostMapping("/generate/secure-pdf")
    @Operation(summary = "Générer un PDF sécurisé", 
               description = "Génère un bulletin de paie au format PDF sécurisé avec mot de passe")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<byte[]> generateSecurePdf(
            @Valid @RequestBody PayrollResult payrollResult) {
        
        log.info("Génération PDF sécurisé pour l'employé {}", payrollResult.getEmployeeId());
        
        try {
            byte[] pdfContent = payslipGenerationService.generateSecurePdf(payrollResult);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "bulletin_paie_" + payrollResult.getEmployeeId() + "_" + 
                payrollResult.getPeriod().toString() + ".pdf");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
                
        } catch (Exception e) {
            log.error("Erreur lors de la génération PDF sécurisé: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère des bulletins en lot
     */
    @PostMapping("/generate/bulk")
    @Operation(summary = "Générer des bulletins en lot", 
               description = "Génère plusieurs bulletins de paie simultanément")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<Long, PayslipDocument>> generateBulkPayslips(
            @Valid @RequestBody Map<Long, PayrollResult> payrollResults) {
        
        log.info("Génération en lot de {} bulletins", payrollResults.size());
        
        try {
            Map<Long, PayslipDocument> documents = payslipGenerationService.generateBulkPayslips(payrollResults);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            log.error("Erreur lors de la génération en lot: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Valide la signature numérique d'un bulletin
     */
    @PostMapping("/validate-signature")
    @Operation(summary = "Valider une signature numérique", 
               description = "Valide la signature numérique d'un bulletin de paie")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<Boolean> validateSignature(
            @Valid @RequestBody PayslipDocument document) {
        
        log.info("Validation de signature pour le document {}", document.getDocumentId());
        
        try {
            boolean isValid = payslipGenerationService.validatePayslipSignature(document);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            log.error("Erreur lors de la validation de signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Télécharge le PDF d'un bulletin existant
     */
    @GetMapping("/{documentId}/pdf")
    @Operation(summary = "Télécharger le PDF d'un bulletin", 
               description = "Télécharge le fichier PDF d'un bulletin de paie existant")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<byte[]> downloadPayslipPdf(
            @Parameter(description = "ID du document") 
            @PathVariable String documentId) {
        
        log.info("Téléchargement PDF pour le document {}", documentId);
        
        // Note: Dans une implémentation complète, on récupérerait le document depuis la base
        // Pour cette version, on retourne une erreur NOT_IMPLEMENTED
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}