# Guide du Système de Chiffrement BantuOps

## Vue d'ensemble

Le système de chiffrement BantuOps utilise AES-256-GCM pour protéger automatiquement les données sensibles dans la base de données. Ce guide explique comment configurer et utiliser le système de chiffrement.

## Fonctionnalités

- **Chiffrement AES-256-GCM** : Algorithme de chiffrement robuste avec authentification
- **Chiffrement automatique JPA** : Les données sont chiffrées/déchiffrées automatiquement
- **Gestion sécurisée des clés** : Validation et génération de clés cryptographiquement sécurisées
- **Support multi-types** : String, BigDecimal, Long
- **Validation au démarrage** : Vérification de la configuration de sécurité

## Configuration

### 1. Génération de la clé de chiffrement

```java
// Générer une nouvelle clé AES-256
String encryptionKey = KeyManagementUtil.generateEncryptionKey();
System.out.println("ENCRYPTION_KEY=" + encryptionKey);
```

### 2. Variables d'environnement

```bash
# Clé de chiffrement (OBLIGATOIRE en production)
ENCRYPTION_KEY=your-base64-encoded-256-bit-key

# Validation au démarrage (optionnel, défaut: true)
ENCRYPTION_VALIDATE_ON_STARTUP=true

# Force minimale de la clé (optionnel, défaut: 70)
ENCRYPTION_MIN_KEY_STRENGTH=70
```

### 3. Configuration application.properties

```properties
# Configuration du chiffrement
bantuops.encryption.key=${ENCRYPTION_KEY:}
bantuops.encryption.validate-on-startup=${ENCRYPTION_VALIDATE_ON_STARTUP:true}
bantuops.encryption.min-key-strength=${ENCRYPTION_MIN_KEY_STRENGTH:70}
```

## Utilisation dans les Entités JPA

### Chiffrement des chaînes de caractères

```java
@Entity
public class Employee {
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_name", columnDefinition = "TEXT")
    private String fullName;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_email", columnDefinition = "TEXT")
    private String email;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_phone", columnDefinition = "TEXT")
    private String phoneNumber;
}
```

### Chiffrement des montants financiers

```java
@Entity
public class PayrollRecord {
    
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_gross_salary", columnDefinition = "TEXT")
    private BigDecimal grossSalary;
    
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_net_salary", columnDefinition = "TEXT")
    private BigDecimal netSalary;
    
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_tax_amount", columnDefinition = "TEXT")
    private BigDecimal taxAmount;
}
```

### Chiffrement des identifiants sensibles

```java
@Entity
public class BankAccount {
    
    @Convert(converter = EncryptedLongConverter.class)
    @Column(name = "encrypted_account_number", columnDefinition = "TEXT")
    private Long accountNumber;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_iban", columnDefinition = "TEXT")
    private String iban;
}
```

## Services Disponibles

### DataEncryptionService

```java
@Autowired
private DataEncryptionService encryptionService;

// Chiffrement manuel
String encrypted = encryptionService.encrypt("données sensibles");
String decrypted = encryptionService.decrypt(encrypted);

// Validation de la configuration
boolean isValid = encryptionService.validateEncryptionConfiguration();

// Génération de nouvelle clé
String newKey = encryptionService.generateNewEncryptionKey();
```

### KeyManagementUtil

```java
// Génération de clé sécurisée
String key = KeyManagementUtil.generateEncryptionKey();

// Validation de clé
boolean isValid = KeyManagementUtil.isValidEncryptionKey(key);

// Analyse de la force de la clé
int strength = KeyManagementUtil.validateKeyStrength(key);

// Génération de clé avec force minimale
String strongKey = KeyManagementUtil.generateStrongEncryptionKey(80);
```

## Sécurité et Bonnes Pratiques

### 1. Gestion des clés

- **JAMAIS** stocker la clé dans le code source
- Utiliser des variables d'environnement sécurisées
- Générer des clés avec une entropie élevée
- Effectuer une rotation régulière des clés

### 2. Configuration de production

```bash
# Générer une clé forte pour la production
ENCRYPTION_KEY=$(java -cp app.jar com.bantuops.backend.util.KeyManagementUtil)

# Activer la validation stricte
ENCRYPTION_VALIDATE_ON_STARTUP=true
ENCRYPTION_MIN_KEY_STRENGTH=80
```

### 3. Colonnes de base de données

- Utiliser `columnDefinition = "TEXT"` pour les champs chiffrés
- Les données chiffrées sont plus longues que les données originales
- Prévoir suffisamment d'espace de stockage

### 4. Performance

- Le chiffrement ajoute une latence aux opérations de base de données
- Utiliser le cache Redis pour les données fréquemment accédées
- Éviter de chiffrer les champs utilisés dans les index de recherche

## Exemples d'utilisation

### Entité Employee complète

```java
@Entity
@Table(name = "employees")
public class Employee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Données personnelles chiffrées
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_first_name", columnDefinition = "TEXT")
    private String firstName;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_last_name", columnDefinition = "TEXT")
    private String lastName;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_national_id", columnDefinition = "TEXT")
    private String nationalId;
    
    // Données financières chiffrées
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_base_salary", columnDefinition = "TEXT")
    private BigDecimal baseSalary;
    
    // Données non sensibles (non chiffrées)
    @Column(name = "employee_code")
    private String employeeCode;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "hire_date")
    private LocalDate hireDate;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
}
```

### Service de gestion des employés

```java
@Service
@Transactional
public class EmployeeService {
    
    @Autowired
    private EmployeeRepository employeeRepository;
    
    public Employee createEmployee(EmployeeRequest request) {
        Employee employee = new Employee();
        
        // Les données sensibles seront automatiquement chiffrées
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setNationalId(request.getNationalId());
        employee.setBaseSalary(request.getBaseSalary());
        
        // Les données non sensibles restent en clair
        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setDepartment(request.getDepartment());
        employee.setHireDate(request.getHireDate());
        
        return employeeRepository.save(employee);
    }
    
    public Employee getEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        
        // Les données sont automatiquement déchiffrées lors de la lecture
        return employee;
    }
}
```

## Tests

### Test du service de chiffrement

```java
@SpringBootTest
class DataEncryptionServiceTest {
    
    @Autowired
    private DataEncryptionService encryptionService;
    
    @Test
    void testEncryptDecrypt() {
        String original = "Données sensibles";
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);
        
        assertNotEquals(original, encrypted);
        assertEquals(original, decrypted);
    }
}
```

### Test des convertisseurs

```java
@DataJpaTest
class EncryptionConverterIntegrationTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    void testEncryptedEntityPersistence() {
        // Créer une entité avec des données sensibles
        Employee employee = new Employee();
        employee.setFirstName("Jean");
        employee.setBaseSalary(new BigDecimal("500000"));
        
        // Sauvegarder (chiffrement automatique)
        Employee saved = entityManager.persistAndFlush(employee);
        entityManager.clear();
        
        // Recharger (déchiffrement automatique)
        Employee loaded = entityManager.find(Employee.class, saved.getId());
        
        assertEquals("Jean", loaded.getFirstName());
        assertEquals(new BigDecimal("500000"), loaded.getBaseSalary());
    }
}
```

## Dépannage

### Erreurs courantes

1. **"No encryption key configured"**
   - Vérifier que la variable `ENCRYPTION_KEY` est définie
   - Générer une nouvelle clé si nécessaire

2. **"Invalid encryption key format"**
   - Vérifier que la clé est encodée en Base64
   - Vérifier que la clé fait exactement 32 bytes (256 bits)

3. **"Encryption key strength too low"**
   - Générer une nouvelle clé avec `generateStrongEncryptionKey()`
   - Éviter les clés avec des patterns répétitifs

### Logs de débogage

```properties
# Activer les logs de débogage pour le chiffrement
logging.level.com.bantuops.backend.service.DataEncryptionService=DEBUG
logging.level.com.bantuops.backend.converter=DEBUG
logging.level.com.bantuops.backend.config.EncryptionConfig=DEBUG
```

## Migration des données existantes

Si vous avez des données existantes non chiffrées, créez un script de migration :

```java
@Component
public class EncryptionMigrationService {
    
    @Autowired
    private DataEncryptionService encryptionService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Transactional
    public void migrateExistingData() {
        // Migrer les données existantes vers le format chiffré
        List<Employee> employees = employeeRepository.findAll();
        
        for (Employee employee : employees) {
            // Forcer la sauvegarde pour déclencher le chiffrement
            employeeRepository.save(employee);
        }
    }
}
```

## Support

Pour toute question sur le système de chiffrement :

1. Consulter les logs de l'application
2. Vérifier la configuration des variables d'environnement
3. Tester avec `DataEncryptionService.validateEncryptionConfiguration()`
4. Contacter l'équipe de développement BantuOps