package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Résultat de validation avec erreurs et avertissements
 * Conforme aux exigences 3.1, 3.2, 3.3 pour la validation métier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /**
     * Indique si la validation a réussi
     */
    private boolean valid;

    /**
     * Liste des erreurs de validation
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Liste des avertissements de validation
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Détails supplémentaires de validation par champ
     */
    private Map<String, List<String>> fieldErrors;

    /**
     * Suggestions pour corriger les erreurs
     */
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    /**
     * Constructeur de convenance
     */
    public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    /**
     * Ajoute une erreur de validation
     */
    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.valid = false;
    }

    /**
     * Ajoute un avertissement de validation
     */
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }

    /**
     * Ajoute une suggestion
     */
    public void addSuggestion(String suggestion) {
        if (this.suggestions == null) {
            this.suggestions = new ArrayList<>();
        }
        this.suggestions.add(suggestion);
    }

    /**
     * Vérifie si la validation a des erreurs
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Vérifie si la validation a des avertissements
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Crée un résultat de validation réussi
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
            .valid(true)
            .build();
    }

    /**
     * Crée un résultat de validation avec erreur
     */
    public static ValidationResult error(String error) {
        return ValidationResult.builder()
            .valid(false)
            .errors(List.of(error))
            .build();
    }

    /**
     * Crée un résultat de validation avec erreurs multiples
     */
    public static ValidationResult errors(List<String> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errors(errors)
            .build();
    }

    /**
     * Crée un résultat de validation avec avertissement
     */
    public static ValidationResult warning(String warning) {
        return ValidationResult.builder()
            .valid(true)
            .warnings(List.of(warning))
            .build();
    }

    /**
     * Combine deux résultats de validation
     */
    public ValidationResult combine(ValidationResult other) {
        if (other == null) {
            return this;
        }

        ValidationResult combined = ValidationResult.builder()
            .valid(this.valid && other.valid)
            .errors(new ArrayList<>())
            .warnings(new ArrayList<>())
            .suggestions(new ArrayList<>())
            .build();

        if (this.errors != null) {
            combined.errors.addAll(this.errors);
        }
        if (other.errors != null) {
            combined.errors.addAll(other.errors);
        }

        if (this.warnings != null) {
            combined.warnings.addAll(this.warnings);
        }
        if (other.warnings != null) {
            combined.warnings.addAll(other.warnings);
        }

        if (this.suggestions != null) {
            combined.suggestions.addAll(this.suggestions);
        }
        if (other.suggestions != null) {
            combined.suggestions.addAll(other.suggestions);
        }

        return combined;
    }
}