package com.bantuops.backend.dto;

import com.bantuops.backend.entity.Transaction;
import com.bantuops.backend.validation.SenegaleseBusinessRule;
import com.bantuops.backend.validation.SenegaleseBankAccount;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pour les demandes de transaction
 * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5 pour la gestion sécurisée des transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SenegaleseBusinessRule(SenegaleseBusinessRule.RuleType.TRANSACTION)
public class TransactionRequest {

    @NotBlank(message = "La référence de transaction est obligatoire")
    @Size(max = 100, message = "La référence ne peut pas dépasser 100 caractères")
    private String transactionReference;

    @NotNull(message = "La date de transaction est obligatoire")
    @PastOrPresent(message = "La date de transaction ne peut pas être dans le futur")
    private LocalDate transactionDate;

    private LocalDate valueDate;

    @NotNull(message = "Le type de transaction est obligatoire")
    private Transaction.TransactionType transactionType;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant doit être positif")
    @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au maximum 15 chiffres entiers et 2 décimales")
    private BigDecimal amount;

    @DecimalMin(value = "0.0", message = "Les frais ne peuvent pas être négatifs")
    @Digits(integer = 10, fraction = 2, message = "Les frais doivent avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal fees;

    @NotBlank(message = "La devise est obligatoire")
    @Pattern(regexp = "^(XOF|EUR|USD)$", message = "La devise doit être XOF (Franc CFA), EUR ou USD")
    private String currency;

    @SenegaleseBankAccount(required = false)
    private String accountNumber;

    @Pattern(regexp = "^[0-9]{3,5}$", message = "Le code banque doit contenir entre 3 et 5 chiffres")
    private String bankCode;

    @Pattern(regexp = "^[0-9]{3,5}$", message = "Le code agence doit contenir entre 3 et 5 chiffres")
    private String branchCode;

    @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}([A-Z0-9]?){0,16}$", 
             message = "L'IBAN doit respecter le format international")
    private String iban;

    @Pattern(regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$", 
             message = "Le code BIC/SWIFT doit respecter le format international")
    private String bicSwift;

    @Size(max = 200, message = "Le nom de la contrepartie ne peut pas dépasser 200 caractères")
    private String counterpartyName;

    @SenegaleseBankAccount(required = false)
    private String counterpartyAccount;

    @Size(max = 100, message = "La banque de la contrepartie ne peut pas dépasser 100 caractères")
    private String counterpartyBank;

    @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
    private String description;

    @Size(max = 100, message = "La référence interne ne peut pas dépasser 100 caractères")
    private String internalReference;

    @Size(max = 100, message = "La référence externe ne peut pas dépasser 100 caractères")
    private String externalReference;

    @Pattern(regexp = "^(BANK_TRANSFER|MOBILE_MONEY|CASH|CHECK|CARD|WIRE_TRANSFER)$", 
             message = "Méthode de paiement invalide")
    private String paymentMethod;

    @Pattern(regexp = "^(ONLINE|BRANCH|ATM|MOBILE|PHONE)$", 
             message = "Canal de paiement invalide")
    private String paymentChannel;

    private Long invoiceId;

    @AssertTrue(message = "La date de valeur ne peut pas être antérieure à la date de transaction")
    public boolean isValidValueDate() {
        if (transactionDate == null || valueDate == null) {
            return true;
        }
        return !valueDate.isBefore(transactionDate);
    }

    @AssertTrue(message = "Les informations bancaires sont obligatoires pour les virements")
    public boolean isValidBankingInfo() {
        if (transactionType == null) {
            return true;
        }
        
        if (transactionType == Transaction.TransactionType.BANK_TRANSFER || 
            transactionType == Transaction.TransactionType.WIRE_TRANSFER) {
            return accountNumber != null && !accountNumber.trim().isEmpty() &&
                   bankCode != null && !bankCode.trim().isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "Le nom de la contrepartie est obligatoire pour les paiements")
    public boolean isValidCounterpartyInfo() {
        if (transactionType == null) {
            return true;
        }
        
        if (transactionType == Transaction.TransactionType.PAYMENT_RECEIVED || 
            transactionType == Transaction.TransactionType.PAYMENT_SENT) {
            return counterpartyName != null && !counterpartyName.trim().isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "Pour les montants supérieurs à 5 000 000 XOF, l'IBAN est obligatoire")
    public boolean isValidIbanRequirement() {
        if (amount == null || !"XOF".equals(currency)) {
            return true;
        }
        
        BigDecimal threshold = new BigDecimal("5000000");
        if (amount.compareTo(threshold) > 0) {
            return iban != null && !iban.trim().isEmpty();
        }
        return true;
    }
}