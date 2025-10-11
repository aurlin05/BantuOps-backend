package com.bantuops.backend.service;

import com.bantuops.backend.dto.PayrollResult;
import com.bantuops.backend.dto.PayslipDocument;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.exception.PayrollCalculationException;
import com.bantuops.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

/**
 * Service de génération de bulletins de paie conformes au format officiel sénégalais
 * Conforme aux exigences 1.3, 2.3, 2.4 pour la génération de bulletins
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayslipGenerationService {

    private final EmployeeRepository employeeRepository;
    private final DigitalSignatureService digitalSignatureService;
    private final PdfGenerationService pdfGenerationService;
    private final AuditService auditService;

    // Configuration depuis application.properties
    @Value("${bantuops.company.name:BantuOps}")
    private String companyName;
    
    @Value("${bantuops.company.address:Dakar, Sénégal}")
    private String companyAddress;
    
    @Value("${bantuops.company.ninea:}")
    private String companyNinea;
    
    @Value("${bantuops.company.rccm:}")
    private String companyRccm;

    // Constantes pour le format sénégalais
    private static final String PAYSLIP_TEMPLATE = "senegal_payslip_template";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    
    // Taux officiels sénégalais (peuvent être configurés)
    private static final BigDecimal IPRES_RATE = new BigDecimal("0.06"); // 6%
    private static final BigDecimal CSS_RATE = new BigDecimal("0.07"); // 7%
    private static final BigDecimal FAMILY_ALLOWANCE_RATE = new BigDecimal("0.07"); // 7%

    /**
     * Génère un bulletin de paie avec format officiel sénégalais
     * Exigences: 1.3, 2.3, 2.4
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayslipDocument generatePayslip(PayrollResult payrollResult) {
        log.info("Génération du bulletin de paie pour l'employé {} période {}", 
                payrollResult.getEmployeeId(), payrollResult.getPeriod());

        try {
            // Validation des données
            validatePayrollResult(payrollResult);

            // Récupération des informations de l'employé
            Employee employee = employeeRepository.findById(payrollResult.getEmployeeId())
                .orElseThrow(() -> new PayrollCalculationException("Employé non trouvé"));

            // Génération du document
            PayslipDocument document = createPayslipDocument(payrollResult, employee);

            // Génération du contenu HTML
            String htmlContent = generateHtmlContent(payrollResult, employee);
            document.setHtmlContent(htmlContent);

            // Génération du PDF
            byte[] pdfContent = pdfGenerationService.generatePdf(htmlContent);
            document.setPdfContent(pdfContent);

            // Signature numérique
            String digitalSignature = digitalSignatureService.signDocument(pdfContent);
            document.setDigitalSignature(digitalSignature);

            // Finalisation du document
            finalizeDocument(document);

            // Audit de la génération
            auditService.logPayslipGeneration(payrollResult.getEmployeeId(), payrollResult.getPeriod());

            log.info("Bulletin de paie généré avec succès - ID: {}", document.getDocumentId());
            return document;

        } catch (Exception e) {
            log.error("Erreur lors de la génération du bulletin de paie: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors de la génération du bulletin", e);
        }
    }

    /**
     * Génère un bulletin de paie au format PDF sécurisé
     * Exigences: 1.3, 2.3, 2.4
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public byte[] generateSecurePdf(PayrollResult payrollResult) {
        log.info("Génération du PDF sécurisé pour l'employé {}", payrollResult.getEmployeeId());

        try {
            // Validation des données
            validatePayrollResult(payrollResult);

            // Récupération des informations de l'employé
            Employee employee = employeeRepository.findById(payrollResult.getEmployeeId())
                .orElseThrow(() -> new PayrollCalculationException("Employé non trouvé"));

            // Génération du contenu HTML
            String htmlContent = generateHtmlContent(payrollResult, employee);
            
            // Génération directe du PDF sécurisé
            String password = generatePdfPassword(payrollResult);
            byte[] securedPdf = pdfGenerationService.generateSecurePdfFromHtml(htmlContent, password);

            // Audit de la génération
            auditService.logSecurePdfGeneration(payrollResult.getEmployeeId(), payrollResult.getPeriod());

            log.info("PDF sécurisé généré avec succès - Taille: {} bytes", securedPdf.length);
            return securedPdf;

        } catch (Exception e) {
            log.error("Erreur lors de la génération du PDF sécurisé: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors de la génération du PDF sécurisé", e);
        }
    }

    /**
     * Génère un bulletin de paie avec template personnalisé
     * Exigences: 1.3, 2.3, 2.4
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayslipDocument generatePayslipWithTemplate(PayrollResult payrollResult, String templateName) {
        log.info("Génération du bulletin avec template {} pour l'employé {}", 
                templateName, payrollResult.getEmployeeId());

        try {
            // Validation des données
            validatePayrollResult(payrollResult);

            // Récupération des informations de l'employé
            Employee employee = employeeRepository.findById(payrollResult.getEmployeeId())
                .orElseThrow(() -> new PayrollCalculationException("Employé non trouvé"));

            // Génération du document
            PayslipDocument document = createPayslipDocument(payrollResult, employee);

            // Génération du contenu HTML avec template spécifique
            String htmlContent = generateHtmlContentWithTemplate(payrollResult, employee, templateName);
            document.setHtmlContent(htmlContent);

            // Génération du PDF
            byte[] pdfContent = pdfGenerationService.generatePdf(htmlContent);
            document.setPdfContent(pdfContent);

            // Signature numérique
            String digitalSignature = digitalSignatureService.signDocument(pdfContent);
            document.setDigitalSignature(digitalSignature);

            // Calcul du checksum pour vérification d'intégrité
            String checksum = digitalSignatureService.generateChecksum(pdfContent);
            document.setChecksum(checksum);

            // Finalisation du document
            finalizeDocument(document);

            // Audit de la génération
            auditService.logPayslipGenerationWithTemplate(
                payrollResult.getEmployeeId(), 
                payrollResult.getPeriod(), 
                templateName
            );

            log.info("Bulletin avec template généré avec succès - ID: {}", document.getDocumentId());
            return document;

        } catch (Exception e) {
            log.error("Erreur lors de la génération avec template: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors de la génération avec template", e);
        }
    }

    /**
     * Valide la signature numérique d'un bulletin
     * Exigences: 2.3, 2.4
     */
    public boolean validatePayslipSignature(PayslipDocument document) {
        log.info("Validation de la signature du bulletin ID: {}", document.getDocumentId());

        try {
            boolean isValid = digitalSignatureService.validateSignature(
                document.getPdfContent(), 
                document.getDigitalSignature()
            );

            // Audit de la validation
            auditService.logSignatureValidation(document.getDocumentId(), isValid);

            log.info("Validation de signature terminée - Résultat: {}", isValid);
            return isValid;

        } catch (Exception e) {
            log.error("Erreur lors de la validation de signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Génère un bulletin de paie en lot pour plusieurs employés
     * Exigences: 1.3, 2.3, 2.4
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Map<Long, PayslipDocument> generateBulkPayslips(Map<Long, PayrollResult> payrollResults) {
        log.info("Génération en lot de {} bulletins de paie", payrollResults.size());

        Map<Long, PayslipDocument> documents = new HashMap<>();

        try {
            for (Map.Entry<Long, PayrollResult> entry : payrollResults.entrySet()) {
                try {
                    PayslipDocument document = generatePayslip(entry.getValue());
                    documents.put(entry.getKey(), document);
                } catch (Exception e) {
                    log.error("Erreur lors de la génération du bulletin pour l'employé {}: {}", 
                             entry.getKey(), e.getMessage());
                    // Continue avec les autres employés
                }
            }

            log.info("Génération en lot terminée - {} bulletins générés sur {} demandés", 
                    documents.size(), payrollResults.size());
            return documents;

        } catch (Exception e) {
            log.error("Erreur lors de la génération en lot: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors de la génération en lot", e);
        }
    }

    // Méthodes privées

    private void validatePayrollResult(PayrollResult payrollResult) {
        if (payrollResult == null) {
            throw new IllegalArgumentException("Le résultat de paie ne peut pas être null");
        }
        if (payrollResult.getEmployeeId() == null) {
            throw new IllegalArgumentException("L'ID de l'employé est obligatoire");
        }
        if (payrollResult.getPeriod() == null) {
            throw new IllegalArgumentException("La période est obligatoire");
        }
        if (payrollResult.getNetSalary() == null || payrollResult.getNetSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le salaire net doit être positif");
        }
    }

    private PayslipDocument createPayslipDocument(PayrollResult payrollResult, Employee employee) {
        return PayslipDocument.builder()
            .documentId(UUID.randomUUID().toString())
            .employeeId(payrollResult.getEmployeeId())
            .employeeName(employee.getFullName())
            .employeeNumber(employee.getEmployeeNumber())
            .period(payrollResult.getPeriod())
            .generatedDate(LocalDateTime.now())
            .status(PayslipDocument.PayslipStatus.GENERATED)
            .build();
    }

    private String generateHtmlContent(PayrollResult payrollResult, Employee employee) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>");
        html.append("<html lang='fr'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Bulletin de Paie - ").append(employee.getFullName()).append("</title>");
        html.append("<style>");
        html.append(getSenegalesePayslipCss());
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        
        // En-tête conforme au format sénégalais
        html.append("<div class='header'>");
        html.append("<div class='company-section'>");
        html.append("<h1>").append(companyName).append("</h1>");
        html.append("<p>").append(companyAddress).append("</p>");
        if (companyNinea != null && !companyNinea.isEmpty()) {
            html.append("<p>NINEA: ").append(companyNinea).append("</p>");
        }
        if (companyRccm != null && !companyRccm.isEmpty()) {
            html.append("<p>RCCM: ").append(companyRccm).append("</p>");
        }
        html.append("</div>");
        html.append("<div class='document-title'>");
        html.append("<h2>BULLETIN DE PAIE</h2>");
        html.append("<p class='period'>Période: ").append(payrollResult.getPeriod().format(MONTH_FORMATTER)).append("</p>");
        html.append("</div>");
        html.append("</div>");

        // Informations de l'employé (format sénégalais)
        html.append("<div class='employee-section'>");
        html.append("<h3>INFORMATIONS DE L'EMPLOYÉ</h3>");
        html.append("<div class='employee-grid'>");
        html.append("<div class='employee-left'>");
        html.append("<p><strong>Nom et Prénoms:</strong> ").append(employee.getFullName()).append("</p>");
        html.append("<p><strong>Matricule:</strong> ").append(employee.getEmployeeNumber()).append("</p>");
        html.append("<p><strong>Fonction:</strong> ").append(employee.getPosition()).append("</p>");
        html.append("</div>");
        html.append("<div class='employee-right'>");
        html.append("<p><strong>Service:</strong> ").append(employee.getDepartment()).append("</p>");
        html.append("<p><strong>Date d'embauche:</strong> ").append(employee.getHireDate() != null ? employee.getHireDate().format(DATE_FORMATTER) : "N/A").append("</p>");
        html.append("<p><strong>Catégorie:</strong> ").append(employee.getContractType() != null ? employee.getContractType().toString() : "N/A").append("</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // Détails de la paie (format officiel sénégalais)
        html.append("<div class='payroll-section'>");
        html.append("<table class='payroll-table'>");
        html.append("<thead>");
        html.append("<tr><th>DÉSIGNATION</th><th>BASE</th><th>TAUX</th><th>MONTANT</th></tr>");
        html.append("</thead>");
        html.append("<tbody>");
        
        // Section GAINS
        html.append("<tr class='section-header'><td colspan='4'>GAINS</td></tr>");
        html.append("<tr><td>Salaire de base</td><td>").append(formatHours(payrollResult.getRegularHours())).append("</td><td>-</td><td>").append(formatAmount(payrollResult.getBaseSalary())).append("</td></tr>");
        
        if (payrollResult.getOvertimeAmount().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Heures supplémentaires</td><td>").append(formatHours(payrollResult.getOvertimeHours())).append("</td><td>+25%</td><td>").append(formatAmount(payrollResult.getOvertimeAmount())).append("</td></tr>");
        }
        
        if (payrollResult.getTransportAllowance().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Indemnité de transport</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getTransportAllowance())).append("</td></tr>");
        }
        
        if (payrollResult.getMealAllowance().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Indemnité de repas</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getMealAllowance())).append("</td></tr>");
        }
        
        if (payrollResult.getHousingAllowance().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Indemnité de logement</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getHousingAllowance())).append("</td></tr>");
        }
        
        if (payrollResult.getPerformanceBonus().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Prime de performance</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getPerformanceBonus())).append("</td></tr>");
        }
        
        html.append("<tr class='subtotal'><td colspan='3'><strong>TOTAL GAINS</strong></td><td><strong>").append(formatAmount(payrollResult.getGrossSalary())).append("</strong></td></tr>");

        // Section COTISATIONS SOCIALES
        html.append("<tr class='section-header'><td colspan='4'>COTISATIONS SOCIALES</td></tr>");
        html.append("<tr><td>Cotisation IPRES (Retraite)</td><td>").append(formatAmount(payrollResult.getGrossSalary())).append("</td><td>6%</td><td>").append(formatAmount(payrollResult.getIpresContribution())).append("</td></tr>");
        html.append("<tr><td>Cotisation CSS (Sécurité Sociale)</td><td>").append(formatAmount(payrollResult.getGrossSalary())).append("</td><td>7%</td><td>").append(formatAmount(payrollResult.getCssContribution())).append("</td></tr>");
        
        if (payrollResult.getFamilyAllowanceContribution().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Prestations familiales</td><td>").append(formatAmount(payrollResult.getGrossSalary())).append("</td><td>7%</td><td>").append(formatAmount(payrollResult.getFamilyAllowanceContribution())).append("</td></tr>");
        }

        // Section IMPÔTS
        html.append("<tr class='section-header'><td colspan='4'>IMPÔTS</td></tr>");
        html.append("<tr><td>Impôt sur le revenu (IRPP)</td><td>").append(formatAmount(calculateTaxableIncome(payrollResult))).append("</td><td>Variable</td><td>").append(formatAmount(payrollResult.getIncomeTax())).append("</td></tr>");

        // Section AUTRES DÉDUCTIONS
        if (hasOtherDeductions(payrollResult)) {
            html.append("<tr class='section-header'><td colspan='4'>AUTRES DÉDUCTIONS</td></tr>");
            
            if (payrollResult.getAdvanceDeduction().compareTo(BigDecimal.ZERO) > 0) {
                html.append("<tr><td>Avance sur salaire</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getAdvanceDeduction())).append("</td></tr>");
            }
            
            if (payrollResult.getLoanDeduction().compareTo(BigDecimal.ZERO) > 0) {
                html.append("<tr><td>Remboursement prêt</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getLoanDeduction())).append("</td></tr>");
            }
            
            if (payrollResult.getAbsenceDeduction().compareTo(BigDecimal.ZERO) > 0) {
                html.append("<tr><td>Retenue absence</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getAbsenceDeduction())).append("</td></tr>");
            }
            
            if (payrollResult.getDelayPenalty().compareTo(BigDecimal.ZERO) > 0) {
                html.append("<tr><td>Pénalité retard</td><td>-</td><td>-</td><td>").append(formatAmount(payrollResult.getDelayPenalty())).append("</td></tr>");
            }
        }

        BigDecimal totalDeductions = payrollResult.getTotalDeductions()
            .add(payrollResult.getTotalSocialContributions())
            .add(payrollResult.getIncomeTax());
            
        html.append("<tr class='subtotal'><td colspan='3'><strong>TOTAL DÉDUCTIONS</strong></td><td><strong>").append(formatAmount(totalDeductions)).append("</strong></td></tr>");

        // Salaire net
        html.append("<tr class='net-salary'><td colspan='3'><strong>SALAIRE NET À PAYER</strong></td><td><strong>").append(formatAmount(payrollResult.getNetSalary())).append("</strong></td></tr>");

        html.append("</tbody>");
        html.append("</table>");
        html.append("</div>");

        // Récapitulatif en lettres (obligatoire au Sénégal)
        html.append("<div class='amount-in-words'>");
        html.append("<p><strong>Montant en lettres:</strong> ").append(convertAmountToWords(payrollResult.getNetSalary())).append("</p>");
        html.append("</div>");

        // Signatures (format officiel)
        html.append("<div class='signatures'>");
        html.append("<div class='signature-left'>");
        html.append("<p><strong>L'Employé</strong></p>");
        html.append("<p>Signature:</p>");
        html.append("<div class='signature-line'></div>");
        html.append("</div>");
        html.append("<div class='signature-right'>");
        html.append("<p><strong>L'Employeur</strong></p>");
        html.append("<p>Signature et cachet:</p>");
        html.append("<div class='signature-line'></div>");
        html.append("</div>");
        html.append("</div>");

        // Pied de page conforme
        html.append("<div class='footer'>");
        html.append("<p>Document généré le ").append(LocalDateTime.now().format(DATE_FORMATTER)).append("</p>");
        html.append("<p>Bulletin de paie conforme au Code du Travail sénégalais</p>");
        html.append("<p>Article L.105 - Conservation obligatoire pendant 5 ans</p>");
        html.append("</div>");

        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    private String getSenegalesePayslipCss() {
        return """
            @page { size: A4; margin: 15mm; }
            body { 
                font-family: 'Times New Roman', serif; 
                font-size: 12px; 
                line-height: 1.4; 
                margin: 0; 
                padding: 0; 
                color: #000; 
            }
            
            .header { 
                display: flex; 
                justify-content: space-between; 
                align-items: flex-start; 
                border-bottom: 3px solid #000; 
                padding-bottom: 15px; 
                margin-bottom: 20px; 
            }
            
            .company-section h1 { 
                font-size: 18px; 
                font-weight: bold; 
                margin: 0 0 5px 0; 
                text-transform: uppercase; 
            }
            
            .company-section p { 
                margin: 2px 0; 
                font-size: 11px; 
            }
            
            .document-title { 
                text-align: right; 
            }
            
            .document-title h2 { 
                font-size: 16px; 
                font-weight: bold; 
                margin: 0; 
                text-decoration: underline; 
            }
            
            .period { 
                font-size: 12px; 
                font-weight: bold; 
                margin: 5px 0 0 0; 
            }
            
            .employee-section { 
                margin: 20px 0; 
                border: 1px solid #000; 
                padding: 10px; 
            }
            
            .employee-section h3 { 
                font-size: 14px; 
                font-weight: bold; 
                margin: 0 0 10px 0; 
                text-align: center; 
                text-decoration: underline; 
            }
            
            .employee-grid { 
                display: flex; 
                justify-content: space-between; 
            }
            
            .employee-left, .employee-right { 
                width: 48%; 
            }
            
            .employee-left p, .employee-right p { 
                margin: 5px 0; 
                font-size: 11px; 
            }
            
            .payroll-section { 
                margin: 20px 0; 
            }
            
            .payroll-table { 
                width: 100%; 
                border-collapse: collapse; 
                border: 2px solid #000; 
                font-size: 11px; 
            }
            
            .payroll-table th, .payroll-table td { 
                border: 1px solid #000; 
                padding: 6px 8px; 
                text-align: left; 
            }
            
            .payroll-table th { 
                background-color: #f0f0f0; 
                font-weight: bold; 
                text-align: center; 
            }
            
            .payroll-table td:nth-child(2), 
            .payroll-table td:nth-child(3), 
            .payroll-table td:nth-child(4) { 
                text-align: right; 
            }
            
            .section-header { 
                background-color: #e0e0e0; 
                font-weight: bold; 
                text-align: center; 
            }
            
            .subtotal { 
                background-color: #f5f5f5; 
                font-weight: bold; 
            }
            
            .net-salary { 
                background-color: #d4edda; 
                font-weight: bold; 
                font-size: 13px; 
            }
            
            .amount-in-words { 
                margin: 15px 0; 
                padding: 10px; 
                border: 1px solid #000; 
                font-size: 11px; 
            }
            
            .signatures { 
                display: flex; 
                justify-content: space-between; 
                margin: 30px 0 20px 0; 
            }
            
            .signature-left, .signature-right { 
                width: 45%; 
                text-align: center; 
            }
            
            .signature-line { 
                border-bottom: 1px solid #000; 
                height: 40px; 
                margin-top: 20px; 
            }
            
            .footer { 
                margin-top: 20px; 
                text-align: center; 
                font-size: 10px; 
                color: #666; 
                border-top: 1px solid #ccc; 
                padding-top: 10px; 
            }
            
            .footer p { 
                margin: 2px 0; 
            }
            
            @media print {
                body { font-size: 11px; }
                .header { page-break-after: avoid; }
                .payroll-table { page-break-inside: avoid; }
                .signatures { page-break-before: avoid; }
            }
            """;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0 FCFA";
        }
        return String.format("%,.0f FCFA", amount);
    }

    private void finalizeDocument(PayslipDocument document) {
        document.setStatus(PayslipDocument.PayslipStatus.FINALIZED);
        document.setFinalizedDate(LocalDateTime.now());
    }

    private String generatePdfPassword(PayrollResult payrollResult) {
        // Génère un mot de passe basé sur l'employé et la période
        return "PAY_" + payrollResult.getEmployeeId() + "_" + payrollResult.getPeriod().toString().replace("-", "");
    }

    /**
     * Formate les heures travaillées
     */
    private String formatHours(BigDecimal hours) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }
        return String.format("%.1f h", hours);
    }

    /**
     * Calcule le revenu imposable selon la législation sénégalaise
     */
    private BigDecimal calculateTaxableIncome(PayrollResult payrollResult) {
        // Au Sénégal, le revenu imposable = salaire brut - cotisations sociales
        return payrollResult.getGrossSalary()
            .subtract(payrollResult.getIpresContribution())
            .subtract(payrollResult.getCssContribution())
            .subtract(payrollResult.getFamilyAllowanceContribution());
    }

    /**
     * Vérifie s'il y a d'autres déductions
     */
    private boolean hasOtherDeductions(PayrollResult payrollResult) {
        return payrollResult.getAdvanceDeduction().compareTo(BigDecimal.ZERO) > 0 ||
               payrollResult.getLoanDeduction().compareTo(BigDecimal.ZERO) > 0 ||
               payrollResult.getAbsenceDeduction().compareTo(BigDecimal.ZERO) > 0 ||
               payrollResult.getDelayPenalty().compareTo(BigDecimal.ZERO) > 0 ||
               payrollResult.getOtherDeductions().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Convertit un montant en lettres (français sénégalais)
     */
    private String convertAmountToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "Zéro franc CFA";
        }

        // Implémentation simplifiée - dans un vrai système, utiliser une bibliothèque dédiée
        long integerPart = amount.longValue();
        
        if (integerPart < 1000) {
            return convertHundreds(integerPart) + " francs CFA";
        } else if (integerPart < 1000000) {
            long thousands = integerPart / 1000;
            long remainder = integerPart % 1000;
            String result = convertHundreds(thousands) + " mille";
            if (remainder > 0) {
                result += " " + convertHundreds(remainder);
            }
            return result + " francs CFA";
        } else {
            long millions = integerPart / 1000000;
            long remainder = integerPart % 1000000;
            String result = convertHundreds(millions) + " million";
            if (millions > 1) result += "s";
            if (remainder > 0) {
                if (remainder >= 1000) {
                    long thousands = remainder / 1000;
                    long hundreds = remainder % 1000;
                    result += " " + convertHundreds(thousands) + " mille";
                    if (hundreds > 0) {
                        result += " " + convertHundreds(hundreds);
                    }
                } else {
                    result += " " + convertHundreds(remainder);
                }
            }
            return result + " francs CFA";
        }
    }

    /**
     * Convertit les centaines en lettres
     */
    private String convertHundreds(long number) {
        if (number == 0) return "";
        
        String[] units = {"", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf"};
        String[] teens = {"dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"};
        String[] tens = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante-dix", "quatre-vingt", "quatre-vingt-dix"};
        
        String result = "";
        
        // Centaines
        if (number >= 100) {
            long hundreds = number / 100;
            if (hundreds == 1) {
                result = "cent";
            } else {
                result = units[(int)hundreds] + " cent";
            }
            if (hundreds > 1 && number % 100 == 0) {
                result += "s";
            }
            number %= 100;
            if (number > 0) result += " ";
        }
        
        // Dizaines et unités
        if (number >= 20) {
            long tensDigit = number / 10;
            long unitsDigit = number % 10;
            
            if (tensDigit == 7 || tensDigit == 9) {
                result += tens[(int)tensDigit - 1];
                if (unitsDigit == 1) {
                    result += " et onze";
                } else if (unitsDigit > 1) {
                    result += "-" + teens[(int)unitsDigit];
                } else {
                    result += "-dix";
                }
            } else if (tensDigit == 8) {
                result += tens[(int)tensDigit];
                if (unitsDigit == 0) {
                    result += "s";
                } else {
                    result += "-" + units[(int)unitsDigit];
                }
            } else {
                result += tens[(int)tensDigit];
                if (unitsDigit == 1) {
                    result += " et un";
                } else if (unitsDigit > 1) {
                    result += "-" + units[(int)unitsDigit];
                }
            }
        } else if (number >= 10) {
            result += teens[(int)number - 10];
        } else if (number > 0) {
            result += units[(int)number];
        }
        
        return result;
    }

    /**
     * Génère le contenu HTML avec un template spécifique
     */
    private String generateHtmlContentWithTemplate(PayrollResult payrollResult, Employee employee, String templateName) {
        switch (templateName.toLowerCase()) {
            case "senegal_official":
                return generateSenegalOfficialTemplate(payrollResult, employee);
            case "senegal_simplified":
                return generateSenegalSimplifiedTemplate(payrollResult, employee);
            case "senegal_detailed":
                return generateSenegalDetailedTemplate(payrollResult, employee);
            default:
                log.warn("Template {} non reconnu, utilisation du template par défaut", templateName);
                return generateHtmlContent(payrollResult, employee);
        }
    }

    /**
     * Template officiel sénégalais complet
     */
    private String generateSenegalOfficialTemplate(PayrollResult payrollResult, Employee employee) {
        // Utilise le template par défaut qui est déjà conforme
        return generateHtmlContent(payrollResult, employee);
    }

    /**
     * Template sénégalais simplifié
     */
    private String generateSenegalSimplifiedTemplate(PayrollResult payrollResult, Employee employee) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>");
        html.append("<html lang='fr'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Bulletin de Paie Simplifié</title>");
        html.append("<style>");
        html.append(getSimplifiedPayslipCss());
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        
        // En-tête simplifié
        html.append("<div class='header'>");
        html.append("<h1>").append(companyName).append("</h1>");
        html.append("<h2>BULLETIN DE PAIE</h2>");
        html.append("<p>").append(payrollResult.getPeriod().format(MONTH_FORMATTER)).append("</p>");
        html.append("</div>");

        // Informations employé
        html.append("<div class='employee'>");
        html.append("<p><strong>").append(employee.getFullName()).append("</strong> - ").append(employee.getEmployeeNumber()).append("</p>");
        html.append("<p>").append(employee.getPosition()).append(" - ").append(employee.getDepartment()).append("</p>");
        html.append("</div>");

        // Résumé de paie
        html.append("<table class='summary'>");
        html.append("<tr><td>Salaire brut</td><td>").append(formatAmount(payrollResult.getGrossSalary())).append("</td></tr>");
        html.append("<tr><td>Cotisations sociales</td><td>").append(formatAmount(payrollResult.getTotalSocialContributions())).append("</td></tr>");
        html.append("<tr><td>Impôt sur le revenu</td><td>").append(formatAmount(payrollResult.getIncomeTax())).append("</td></tr>");
        html.append("<tr><td>Autres déductions</td><td>").append(formatAmount(payrollResult.getTotalDeductions())).append("</td></tr>");
        html.append("<tr class='net'><td><strong>Salaire net</strong></td><td><strong>").append(formatAmount(payrollResult.getNetSalary())).append("</strong></td></tr>");
        html.append("</table>");

        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    /**
     * Template sénégalais détaillé avec calculs
     */
    private String generateSenegalDetailedTemplate(PayrollResult payrollResult, Employee employee) {
        StringBuilder html = new StringBuilder();
        
        // Commence par le template standard
        html.append(generateHtmlContent(payrollResult, employee));
        
        // Ajoute une section détaillée des calculs
        String detailedCalculations = generateDetailedCalculationsSection(payrollResult);
        
        // Insère avant la fermeture du body
        String content = html.toString();
        content = content.replace("</body>", detailedCalculations + "</body>");
        
        return content;
    }

    /**
     * Génère la section des calculs détaillés
     */
    private String generateDetailedCalculationsSection(PayrollResult payrollResult) {
        StringBuilder html = new StringBuilder();
        
        html.append("<div class='detailed-calculations'>");
        html.append("<h3>DÉTAIL DES CALCULS</h3>");
        html.append("<table class='calculations-table'>");
        
        // Calculs des heures
        if (payrollResult.getRegularHours().compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Heures normales</td><td>").append(formatHours(payrollResult.getRegularHours())).append("</td><td>").append(formatAmount(payrollResult.getHourlyRate())).append("</td><td>").append(formatAmount(payrollResult.getRegularSalary())).append("</td></tr>");
        }
        
        if (payrollResult.getOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overtimeRate = payrollResult.getHourlyRate().multiply(new BigDecimal("1.25"));
            html.append("<tr><td>Heures supplémentaires</td><td>").append(formatHours(payrollResult.getOvertimeHours())).append("</td><td>").append(formatAmount(overtimeRate)).append("</td><td>").append(formatAmount(payrollResult.getOvertimeAmount())).append("</td></tr>");
        }
        
        // Calculs des cotisations
        html.append("<tr class='section'><td colspan='4'>COTISATIONS SOCIALES</td></tr>");
        html.append("<tr><td>IPRES (6%)</td><td>").append(formatAmount(payrollResult.getGrossSalary())).append("</td><td>6%</td><td>").append(formatAmount(payrollResult.getIpresContribution())).append("</td></tr>");
        html.append("<tr><td>CSS (7%)</td><td>").append(formatAmount(payrollResult.getGrossSalary())).append("</td><td>7%</td><td>").append(formatAmount(payrollResult.getCssContribution())).append("</td></tr>");
        
        // Calcul de l'impôt
        BigDecimal taxableIncome = calculateTaxableIncome(payrollResult);
        html.append("<tr class='section'><td colspan='4'>CALCUL IMPÔT</td></tr>");
        html.append("<tr><td>Revenu imposable</td><td colspan='2'>Brut - Cotisations</td><td>").append(formatAmount(taxableIncome)).append("</td></tr>");
        html.append("<tr><td>Impôt calculé</td><td colspan='2'>Selon barème</td><td>").append(formatAmount(payrollResult.getIncomeTax())).append("</td></tr>");
        
        html.append("</table>");
        html.append("</div>");
        
        return html.toString();
    }

    /**
     * CSS pour le template simplifié
     */
    private String getSimplifiedPayslipCss() {
        return """
            body { font-family: Arial, sans-serif; margin: 20px; font-size: 14px; }
            .header { text-align: center; margin-bottom: 30px; }
            .header h1 { margin: 0; font-size: 20px; }
            .header h2 { margin: 5px 0; font-size: 16px; text-decoration: underline; }
            .employee { margin: 20px 0; padding: 10px; border: 1px solid #ccc; }
            .summary { width: 100%; border-collapse: collapse; margin: 20px 0; }
            .summary td { padding: 10px; border: 1px solid #ccc; }
            .summary td:last-child { text-align: right; font-weight: bold; }
            .net { background-color: #d4edda; font-size: 16px; }
            """;
    }
}