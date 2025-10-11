package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO pour les calculs de facture
 * Conforme aux exigences 2.1, 2.2, 2.3, 3.1, 3.2 pour les calculs financiers sécurisés
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceCalculation {

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountedSubtotal;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String currency;
    private List<ItemCalculation> itemCalculations;
    private VatBreakdown vatBreakdown;
    private DiscountBreakdown discountBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemCalculation {
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private BigDecimal discountAmount;
        private BigDecimal discountedAmount;
        private BigDecimal vatAmount;
        private BigDecimal totalAmount;
        private Integer itemOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VatBreakdown {
        private BigDecimal vatableAmount;
        private BigDecimal vatRate;
        private BigDecimal vatAmount;
        private BigDecimal totalIncludingVat;
        private Boolean isVatExempt;
        private String exemptionReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountBreakdown {
        private BigDecimal originalAmount;
        private BigDecimal discountAmount;
        private BigDecimal discountPercentage;
        private BigDecimal finalAmount;
        private String discountType;
        private String discountReason;
    }
}