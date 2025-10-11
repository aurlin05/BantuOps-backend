package com.bantuops.backend.dto;

import com.bantuops.backend.validation.SenegaleseBusinessRule;
import com.bantuops.backend.validation.SenegalesePhoneNumber;
import com.bantuops.backend.validation.SenegaleseTaxNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO InvoiceRequest avec validation fiscale sénégalaise
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation métier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SenegaleseBusinessRule(SenegaleseBusinessRule.RuleType.INVOICE)
public class InvoiceRequest {

    @NotBlank(message = "Le numéro de facture est obligatoire")
    @Size(max = 50, message = "Le numéro de facture ne peut pas dépasser 50 caractères")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Le numéro de facture ne peut contenir que des lettres majuscules, chiffres et tirets")
    private String invoiceNumber;

    @NotNull(message = "La date de facture est obligatoire")
    @PastOrPresent(message = "La date de facture ne peut pas être dans le futur")
    private LocalDate invoiceDate;

    @NotNull(message = "La date d'échéance est obligatoire")
    @Future(message = "La date d'échéance doit être dans le futur")
    private LocalDate dueDate;

    @NotBlank(message = "Le nom du client est obligatoire")
    @Size(max = 200, message = "Le nom du client ne peut pas dépasser 200 caractères")
    private String clientName;

    @Size(max = 500, message = "L'adresse du client ne peut pas dépasser 500 caractères")
    private String clientAddress;

    @SenegaleseTaxNumber(required = false)
    private String clientTaxNumber;

    @Email(message = "L'email du client doit être valide")
    @Size(max = 100, message = "L'email ne peut pas dépasser 100 caractères")
    private String clientEmail;

    @SenegalesePhoneNumber(required = false)
    private String clientPhone;

    @NotNull(message = "Le montant hors taxes est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant hors taxes doit être positif")
    @Digits(integer = 12, fraction = 2, message = "Le montant hors taxes doit avoir au maximum 12 chiffres entiers et 2 décimales")
    private BigDecimal subtotalAmount;

    @DecimalMin(value = "0.0", message = "Le montant de remise ne peut pas être négatif")
    @Digits(integer = 10, fraction = 2, message = "Le montant de remise doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal discountAmount;

    @NotNull(message = "Le taux de TVA est obligatoire")
    @DecimalMin(value = "0.0", message = "Le taux de TVA ne peut pas être négatif")
    @DecimalMax(value = "1.0", message = "Le taux de TVA ne peut pas dépasser 100%")
    @Digits(integer = 1, fraction = 4, message = "Le taux de TVA doit avoir au maximum 1 chiffre entier et 4 décimales")
    private BigDecimal vatRate;

    @NotBlank(message = "La devise est obligatoire")
    @Pattern(regexp = "^(XOF|EUR|USD)$", message = "La devise doit être XOF (Franc CFA), EUR ou USD")
    private String currency;

    @Size(max = 1000, message = "La description ne peut pas dépasser 1000 caractères")
    private String description;

    @Size(max = 2000, message = "Les conditions générales ne peuvent pas dépasser 2000 caractères")
    private String termsAndConditions;

    @NotEmpty(message = "La facture doit contenir au moins un élément")
    @Valid
    private List<@Valid InvoiceItemRequest> items;

    @AssertTrue(message = "La date d'échéance doit être postérieure à la date de facture")
    public boolean isValidDueDate() {
        if (invoiceDate == null || dueDate == null) {
            return true;
        }
        return dueDate.isAfter(invoiceDate);
    }

    @AssertTrue(message = "Le montant de remise ne peut pas dépasser le montant hors taxes")
    public boolean isValidDiscountAmount() {
        if (subtotalAmount == null || discountAmount == null) {
            return true;
        }
        return discountAmount.compareTo(subtotalAmount) <= 0;
    }

    @AssertTrue(message = "Pour les montants supérieurs à 1 000 000 XOF, le numéro fiscal du client est obligatoire")
    public boolean isValidTaxNumberRequirement() {
        if (subtotalAmount == null || !"XOF".equals(currency)) {
            return true;
        }
        
        // Seuil de 1 000 000 XOF pour l'obligation du numéro fiscal
        BigDecimal threshold = new BigDecimal("1000000");
        if (subtotalAmount.compareTo(threshold) > 0) {
            return clientTaxNumber != null && !clientTaxNumber.trim().isEmpty();
        }
        return true;
    }

    /**
     * DTO pour les éléments de facture
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceItemRequest {

        @NotBlank(message = "La description de l'élément est obligatoire")
        @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
        private String description;

        @NotNull(message = "La quantité est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "La quantité doit être positive")
        @Digits(integer = 7, fraction = 3, message = "La quantité doit avoir au maximum 7 chiffres entiers et 3 décimales")
        private BigDecimal quantity;

        @NotNull(message = "Le prix unitaire est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le prix unitaire doit être positif")
        @Digits(integer = 10, fraction = 2, message = "Le prix unitaire doit avoir au maximum 10 chiffres entiers et 2 décimales")
        private BigDecimal unitPrice;

        @Size(max = 20, message = "L'unité ne peut pas dépasser 20 caractères")
        @Pattern(regexp = "^[a-zA-Z0-9\\s]*$", message = "L'unité ne peut contenir que des lettres, chiffres et espaces")
        private String unit;

        @NotNull(message = "L'ordre de l'élément est obligatoire")
        @Min(value = 1, message = "L'ordre de l'élément doit être au minimum 1")
        private Integer itemOrder;
    }
}