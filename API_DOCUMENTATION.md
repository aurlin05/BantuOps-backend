# BantuOps Backend API Documentation

## 📋 Vue d'ensemble

L'API BantuOps est une API REST sécurisée conçue spécifiquement pour la gestion des PME sénégalaises. Elle respecte la législation locale et offre des fonctionnalités complètes de gestion de paie, financière et RH.

## 🚀 Accès à la Documentation

### Documentation Interactive (Swagger UI)
- **URL de développement** : `http://localhost:8080/swagger-ui.html`
- **URL de production** : `https://api.bantuops.com/swagger-ui.html`

### Documentation JSON/YAML
- **OpenAPI JSON** : `http://localhost:8080/api-docs`
- **OpenAPI YAML** : `http://localhost:8080/api-docs.yaml`

## 🔐 Authentification

### JWT Bearer Token
Toutes les APIs (sauf `/api/auth/login`) nécessitent un token JWT dans l'en-tête :

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Obtenir un Token
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin@bantuops.com",
  "password": "SecurePassword123!"
}
```

**Réponse :**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer"
}
```

## 📚 Endpoints Principaux

### 🔑 Authentification (`/api/auth`)
- `POST /login` - Connexion utilisateur
- `POST /refresh` - Renouveler le token
- `POST /logout` - Déconnexion
- `POST /validate` - Valider un token
- `GET /me` - Profil utilisateur connecté

### 💰 Gestion de Paie (`/api/payroll`)
- `POST /calculate` - Calculer la paie d'un employé
- `POST /calculate-salary` - Calculer le salaire de base
- `POST /calculate-overtime` - Calculer les heures supplémentaires
- `POST /bulk-calculate` - Calcul en lot
- `GET /employee/{id}/history` - Historique des paies
- `GET /tax-rates` - Taux de taxes sénégalais
- `POST /validate` - Valider les données de paie

### 📊 Gestion Financière (`/api/financial`)
- `POST /invoices` - Créer une facture
- `POST /invoices/calculate` - Calculer les montants
- `GET /invoices` - Lister les factures
- `GET /invoices/{id}` - Détails d'une facture
- `POST /vat/calculate` - Calculer la TVA
- `POST /reports/generate` - Générer un rapport financier
- `POST /transactions` - Enregistrer une transaction
- `GET /transactions` - Historique des transactions
- `GET /vat/report` - Rapport de TVA pour la DGI
- `POST /export` - Exporter les données (ADMIN uniquement)

### 👥 Gestion RH (`/api/hr`)
- `POST /attendance/record` - Enregistrer la présence
- `POST /attendance/calculate-delay` - Calculer les retards
- `POST /absence/record` - Enregistrer une absence
- `GET /attendance/check-rules/{id}` - Vérifier les règles d'assiduité
- `POST /reports/attendance` - Rapport d'assiduité
- `POST /payroll/calculate-adjustments` - Ajustements de paie
- `GET /attendance/employee/{id}/history` - Historique d'assiduité
- `POST /attendance/rules/configure` - Configurer les règles (ADMIN)
- `POST /absence/validate-justification` - Valider une justification

### 🧑‍💼 Gestion des Employés (`/api/employees`)
- `POST /` - Créer un employé
- `PUT /{id}` - Mettre à jour un employé
- `GET /{id}` - Récupérer un employé
- `GET /` - Lister les employés (avec pagination)
- `PATCH /{id}/deactivate` - Désactiver un employé
- `PATCH /{id}/activate` - Réactiver un employé
- `POST /validate` - Valider les données d'employé
- `POST /search` - Recherche avancée
- `GET /statistics` - Statistiques des employés

### 📖 Documentation (`/api/docs`)
- `GET /info` - Informations sur l'API
- `GET /health` - État de santé
- `GET /limits` - Limites et quotas
- `GET /error-codes` - Codes d'erreur
- `GET /examples` - Exemples d'utilisation
- `GET /changelog` - Historique des versions
- `GET /validation-schemas` - Schémas de validation sénégalais
- `GET /integration-guide` - Guide d'intégration
- `GET /performance-metrics` - Métriques de performance

## 🌍 Spécificités Sénégalaises

### Validation des Données
L'API valide automatiquement les données selon les normes sénégalaises :

#### Numéro de Téléphone
- **Format** : `+221XXXXXXXXX`
- **Exemple** : `+221701234567`

#### Numéro Fiscal
- **Format** : 13 chiffres
- **Exemple** : `1234567890123`

#### Compte Bancaire
- **Format** : IBAN sénégalais
- **Exemple** : `SN12K00100152000025690007542`

### Calculs de Paie Conformes

#### Taux de Taxes (2024)
- **IRPP** : Barème progressif (0% à 40%)
- **IPRES** : 6% (plafonné)
- **CSS** : 3.5% (plafonné)
- **TVA** : 18%

#### Tranches IRPP
| Tranche | Taux | Montant fixe |
|---------|------|--------------|
| 0 - 630 000 | 0% | 0 |
| 630 001 - 1 500 000 | 20% | 0 |
| 1 500 001 - 4 000 000 | 30% | 174 000 |
| 4 000 001 - 8 300 000 | 35% | 924 000 |
| > 8 300 000 | 40% | 2 429 000 |

## 🔒 Sécurité

### Chiffrement des Données
- **Algorithme** : AES-256
- **Données chiffrées** : Informations personnelles, salaires, montants financiers
- **Clés** : Gestion sécurisée via variables d'environnement

### Audit et Traçabilité
- Toutes les actions sont auditées
- Logs sécurisés avec horodatage
- Traçabilité complète des modifications

### Permissions par Rôle
- **ADMIN** : Accès complet
- **HR** : Gestion RH et paie
- **FINANCE** : Gestion financière
- **USER** : Consultation limitée

## 📊 Codes de Réponse HTTP

| Code | Signification | Description |
|------|---------------|-------------|
| 200 | OK | Requête réussie |
| 201 | Created | Ressource créée |
| 400 | Bad Request | Données invalides |
| 401 | Unauthorized | Authentification requise |
| 403 | Forbidden | Permissions insuffisantes |
| 404 | Not Found | Ressource non trouvée |
| 409 | Conflict | Conflit de ressource |
| 422 | Unprocessable Entity | Règle métier violée |
| 429 | Too Many Requests | Limite de taux atteinte |
| 500 | Internal Server Error | Erreur serveur |

## 🚦 Limites et Quotas

### Limites de Taux
- **Par minute** : 1 000 requêtes
- **Par heure** : 10 000 requêtes
- **Par jour** : 100 000 requêtes

### Limites de Données
- **Employés par entreprise** : 1 000
- **Factures par mois** : 5 000
- **Enregistrements de paie par mois** : 1 000

### Limites de Fichiers
- **Taille maximale** : 10 MB
- **Formats autorisés** : PDF, CSV, XLSX

## 🛠️ Exemples d'Intégration

### JavaScript/Node.js
```javascript
const axios = require('axios');

// Authentification
const loginResponse = await axios.post('http://localhost:8080/api/auth/login', {
  username: 'admin@bantuops.com',
  password: 'password123'
});

const token = loginResponse.data.token;

// Calcul de paie
const payrollResponse = await axios.post(
  'http://localhost:8080/api/payroll/calculate',
  {
    employeeId: 1,
    payrollPeriod: '2024-01',
    baseSalary: 500000
  },
  {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  }
);
```

### Python
```python
import requests

# Authentification
login_response = requests.post(
    'http://localhost:8080/api/auth/login',
    json={
        'username': 'admin@bantuops.com',
        'password': 'password123'
    }
)

token = login_response.json()['token']

# Calcul de paie
payroll_response = requests.post(
    'http://localhost:8080/api/payroll/calculate',
    json={
        'employeeId': 1,
        'payrollPeriod': '2024-01',
        'baseSalary': 500000
    },
    headers={
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json'
    }
)
```

### cURL
```bash
# Authentification
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@bantuops.com","password":"password123"}' \
  | jq -r '.token')

# Calcul de paie
curl -X POST http://localhost:8080/api/payroll/calculate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "employeeId": 1,
    "payrollPeriod": "2024-01",
    "baseSalary": 500000
  }'
```

## 🐛 Gestion des Erreurs

### Format de Réponse d'Erreur
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Données invalides",
  "details": {
    "baseSalary": "Le salaire de base doit être positif",
    "phoneNumber": "Le numéro doit être au format sénégalais"
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/employees",
  "suggestion": "Veuillez corriger les champs invalides et réessayer"
}
```

### Codes d'Erreur Métier
- `PAYROLL_CALCULATION_ERROR` : Erreur de calcul de paie
- `INVALID_SENEGAL_TAX_NUMBER` : Numéro fiscal sénégalais invalide
- `INVALID_SENEGAL_PHONE` : Numéro de téléphone sénégalais invalide
- `BUSINESS_RULE_VIOLATION` : Violation de règle métier
- `INSUFFICIENT_PERMISSION` : Permissions insuffisantes

## 📞 Support

- **Email** : support@bantuops.com
- **Documentation** : [Swagger UI](http://localhost:8080/swagger-ui.html)
- **Statut** : https://status.bantuops.com

## 📝 Changelog

### v1.0.0 (2024-01-15)
- Version initiale de l'API
- Gestion de paie conforme à la législation sénégalaise
- Gestion financière avec TVA
- Gestion RH et assiduité
- Authentification JWT
- Chiffrement des données sensibles
- Audit complet des actions
- Documentation OpenAPI complète

---

**Note** : Cette documentation est générée automatiquement et mise à jour en continu. Pour la version la plus récente, consultez la documentation interactive Swagger UI.