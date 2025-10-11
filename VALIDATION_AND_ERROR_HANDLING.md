# Validation et Gestion d'Erreurs - BantuOps Backend

## Vue d'ensemble

Ce document décrit l'implémentation complète de la validation et de la gestion d'erreurs pour le backend BantuOps, conforme aux exigences 4.2, 4.3, 4.4, 3.1, 3.2, 3.3 de la spécification de migration.

## Architecture de Validation

### 1. Couches de Validation

```
┌─────────────────────────────────────┐
│        Contrôleur REST              │
│  - @Valid sur les paramètres        │
│  - Annotations de sécurité          │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│     ValidationService               │
│  - Validation centralisée           │
│  - Combinaison Bean + Métier        │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│   Bean Validation (JSR-303)        │
│  - Annotations standard             │
│  - Annotations personnalisées       │
└─────────────────┬───────────────────┘
                  │
┌─────────────────▼───────────────────┐
│  BusinessRuleValidator              │
│  - Règles métier sénégalaises       │
│  - Validation contextuelle          │
└─────────────────────────────────────┘
```

### 2. Types de Validation

#### Bean Validation (JSR-303)
- Validation des contraintes de base (@NotNull, @Size, @Pattern, etc.)
- Annotations personnalisées pour le contexte sénégalais
- Validation cross-field avec @AssertTrue

#### Validation Métier Sénégalaise
- Respect du SMIG (Salaire Minimum Interprofessionnel Garanti)
- Validation des numéros fiscaux sénégalais
- Validation des numéros de téléphone locaux
- Respect du Code du Travail sénégalais
- Validation des comptes bancaires locaux

## Annotations de Validation Personnalisées

### @SenegaleseBusinessRule
Valide les règles métier spécifiques au Sénégal selon le type d'objet.

```java
@SenegaleseBusinessRule(SenegaleseBusinessRule.RuleType.PAYROLL)
public class PayrollRequest {
    // ...
}
```

### @SenegalesePhoneNumber
Valide les numéros de téléphone sénégalais (fixes et mobiles).

```java
@SenegalesePhoneNumber
private String phoneNumber; // +221701234567, 771234567, etc.
```

### @SenegaleseTaxNumber
Valide les numéros fiscaux sénégalais (13 chiffres avec clé de contrôle).

```java
@SenegaleseTaxNumber(required = false)
private String taxNumber; // 1234567890123
```

### @SenegaleseBankAccount
Valide les comptes bancaires sénégalais selon le format RIB local.

```java
@SenegaleseBankAccount
private String accountNumber; // Format banque sénégalaise
```

## Gestion d'Erreurs

### 1. GlobalExceptionHandler

Gestionnaire centralisé pour toutes les exceptions avec messages localisés.

#### Exceptions Gérées
- `MethodArgumentNotValidException` - Erreurs de validation Bean
- `ConstraintViolationException` - Violations de contraintes
- `BusinessRuleException` - Violations de règles métier
- `AuthenticationException` - Erreurs d'authentification
- `AccessDeniedException` - Erreurs d'autorisation
- `ResourceNotFoundException` - Ressources non trouvées
- `Exception` - Erreurs génériques

#### Format de Réponse d'Erreur
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Erreur de validation des données",
  "details": {
    "baseSalary": "Le salaire de base doit être positif",
    "employeeId": "L'ID de l'employé est obligatoire"
  },
  "timestamp": "2024-01-15T10:30:00",
  "path": "uri=/api/payroll/calculate",
  "suggestion": "Veuillez corriger les champs invalides et réessayer"
}
```

### 2. SecurityExceptionHandler

Gestionnaire spécialisé pour les violations de sécurité avec audit automatique.

#### Fonctionnalités
- Audit automatique des violations de sécurité
- Détection d'activités suspectes
- Blocage temporaire d'adresses IP
- Logging détaillé des tentatives d'accès

## Localisation

### 1. Support Multi-langue

Le système supporte trois langues :
- **Français (fr)** - Langue officielle du Sénégal (par défaut)
- **Anglais (en)** - Pour les utilisateurs internationaux
- **Wolof (wo)** - Langue nationale du Sénégal

### 2. Fichiers de Messages

#### messages.properties (Français)
```properties
validation.failed=Erreur de validation des données
senegal.smig.violation=Le salaire ne peut pas être inférieur au SMIG sénégalais
senegal.phone.number.invalid=Numéro de téléphone sénégalais invalide
```

#### messages_en.properties (Anglais)
```properties
validation.failed=Data validation error
senegal.smig.violation=Salary cannot be below Senegalese SMIG
senegal.phone.number.invalid=Invalid Senegalese phone number
```

#### messages_wo.properties (Wolof)
```properties
validation.failed=Njuup ci validation bi
senegal.smig.violation=Salaire bi mënul doy SMIG bu Sénégal
senegal.phone.number.invalid=Numéro téléphone bu Sénégal baax ul
```

### 3. Changement de Langue

La langue peut être changée via :
- En-tête HTTP `Accept-Language`
- Paramètre de requête `?lang=fr|en|wo`

## Règles Métier Sénégalaises

### 1. Règles de Paie

#### SMIG (Salaire Minimum)
- Montant : 60 000 XOF/mois minimum
- Validation automatique sur tous les salaires de base

#### Heures Supplémentaires
- Maximum : 4 heures/jour selon le Code du Travail
- Majoration obligatoire au-delà de 8h/jour

#### Déductions
- Maximum : 1/3 du salaire brut selon la loi
- Validation automatique du total des déductions

### 2. Règles d'Employé

#### Âge de Travail
- Minimum : 16 ans (âge légal minimum)
- Retraite : 60 ans (avertissement au-delà)

#### Durée de Travail
- Maximum : 8 heures/jour (durée légale)
- Repos hebdomadaire : Dimanche obligatoire

### 3. Règles de Facturation

#### TVA Sénégalaise
- Taux standard : 18%
- Validation automatique du taux appliqué

#### Numéro Fiscal
- Obligatoire pour factures > 1 000 000 XOF
- Format : 13 chiffres avec clé de contrôle

### 4. Règles de Transaction

#### Seuils de Déclaration
- Déclaration spéciale : > 10 000 000 XOF
- IBAN obligatoire : > 5 000 000 XOF

## Utilisation

### 1. Dans les Contrôleurs

```java
@PostMapping("/calculate")
public ResponseEntity<PayrollResponse> calculatePayroll(
        @Valid @RequestBody PayrollRequest request) {
    // La validation est automatique grâce à @Valid
    // Les erreurs sont gérées par GlobalExceptionHandler
    return ResponseEntity.ok(payrollService.calculatePayroll(request));
}
```

### 2. Validation Programmatique

```java
@Service
public class PayrollService {
    
    @Autowired
    private ValidationService validationService;
    
    public PayrollResult calculatePayroll(PayrollRequest request) {
        // Validation explicite si nécessaire
        validationService.validateAndThrow(request);
        
        // Logique métier...
        return result;
    }
}
```

### 3. Validation Personnalisée

```java
// Validation spécifique aux règles sénégalaises
ValidationResult result = validationService.validateSenegaleseBusinessRules(request);
if (!result.isValid()) {
    // Traitement des erreurs
    throw new BusinessRuleException("SENEGAL_RULES_VIOLATION", 
                                   String.join("; ", result.getErrors()));
}
```

## Tests

### 1. Tests de Validation

```java
@Test
void shouldRejectPayrollRequestBelowSMIG() {
    PayrollRequest request = PayrollRequest.builder()
        .baseSalary(new BigDecimal("50000")) // Sous le SMIG
        .build();
    
    ValidationResult result = validationService.validatePayrollRequest(request);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).contains("SMIG");
}
```

### 2. Tests d'Intégration

```java
@Test
@WithMockUser(roles = "HR")
void shouldHandleValidationErrorsWithLocalizedMessages() throws Exception {
    mockMvc.perform(post("/api/payroll/calculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.suggestion").exists());
}
```

## Configuration

### 1. Application Properties

```properties
# Localisation
spring.messages.basename=messages
spring.messages.encoding=UTF-8
spring.messages.cache-duration=3600

# Validation
spring.validation.enabled=true
```

### 2. Configuration Bean

```java
@Configuration
public class LocalizationConfig {
    
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = 
            new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(Locale.FRENCH);
        return messageSource;
    }
}
```

## Bonnes Pratiques

### 1. Validation
- Utiliser `@Valid` sur tous les paramètres d'entrée
- Combiner Bean Validation et règles métier
- Fournir des messages d'erreur clairs et localisés
- Inclure des suggestions de correction

### 2. Gestion d'Erreurs
- Centraliser la gestion dans GlobalExceptionHandler
- Logger les erreurs avec le niveau approprié
- Auditer les violations de sécurité
- Ne jamais exposer d'informations sensibles

### 3. Localisation
- Utiliser des clés de message descriptives
- Fournir des traductions complètes
- Tester avec différentes langues
- Respecter les spécificités culturelles

### 4. Sécurité
- Valider côté serveur même si validé côté client
- Auditer toutes les violations de sécurité
- Implémenter des mécanismes anti-brute force
- Chiffrer les données sensibles dans les logs

## Maintenance

### 1. Ajout de Nouvelles Règles
1. Créer l'annotation de validation
2. Implémenter le validateur
3. Ajouter les messages localisés
4. Écrire les tests
5. Mettre à jour la documentation

### 2. Modification des Messages
1. Modifier les fichiers .properties
2. Tester avec toutes les langues
3. Vérifier l'affichage dans l'interface
4. Valider avec les utilisateurs locaux

### 3. Monitoring
- Surveiller les logs d'erreur
- Analyser les violations fréquentes
- Optimiser les performances de validation
- Mettre à jour selon l'évolution réglementaire

Cette implémentation fournit une validation robuste et une gestion d'erreurs complète, adaptée au contexte sénégalais et conforme aux exigences de sécurité et de localisation du projet BantuOps.