# 🏢 Guide d'Intégration API BantuOps

## 📋 Table des Matières

1. [Vue d'ensemble](#vue-densemble)
2. [Authentification](#authentification)
3. [Endpoints Principaux](#endpoints-principaux)
4. [Exemples d'Utilisation](#exemples-dutilisation)
5. [Gestion des Erreurs](#gestion-des-erreurs)
6. [Conformité Sénégalaise](#conformité-sénégalaise)
7. [SDKs et Outils](#sdks-et-outils)
8. [Support](#support)

## 🌟 Vue d'ensemble

L'API BantuOps est une solution complète pour la gestion des PME sénégalaises, offrant :

- **Gestion de Paie** conforme à la législation sénégalaise
- **Gestion Financière** avec TVA et chiffrement
- **Gestion RH** et suivi d'assiduité
- **Sécurité** avancée avec JWT et audit

### 🔗 URLs de Base

| Environnement | URL | Description |
|---------------|-----|-------------|
| **Production** | `https://api.bantuops.com` | 🚀 Environnement de production |
| **Staging** | `https://staging-api.bantuops.com` | 🧪 Environnement de test |
| **Développement** | `http://localhost:8080` | 💻 Développement local |

### 📊 Limites et Quotas

| Limite | Valeur | Description |
|--------|--------|-------------|
| **Requêtes/minute** | 1,000 | Limite par utilisateur |
| **Requêtes/heure** | 10,000 | Limite par utilisateur |
| **Requêtes/jour** | 100,000 | Limite par utilisateur |
| **Taille fichier** | 10 MB | Taille maximale des uploads |
| **Employés/entreprise** | 1,000 | Limite par compte |

## 🔐 Authentification

### 1. Connexion

```bash
curl -X POST "https://api.bantuops.com/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@bantuops.com",
    "password": "SecurePassword123!"
  }'
```

**Réponse :**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "username": "admin@bantuops.com",
    "roles": ["ADMIN"],
    "permissions": ["READ", "WRITE", "DELETE"]
  }
}
```

### 2. Utilisation du Token

Incluez le token dans toutes les requêtes :

```bash
curl -X GET "https://api.bantuops.com/api/employees" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"
```

### 3. Renouvellement du Token

```bash
curl -X POST "https://api.bantuops.com/api/auth/refresh" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

## 🎯 Endpoints Principaux

### 👥 Gestion des Employés

#### Créer un Employé
```bash
POST /api/employees
```

**Exemple :**
```json
{
  "employeeNumber": "EMP-001",
  "firstName": "Amadou",
  "lastName": "Diallo",
  "email": "amadou.diallo@bantuops.com",
  "phoneNumber": "+221701234567",
  "nationalId": "1234567890123",
  "dateOfBirth": "1990-05-15",
  "position": "Développeur Senior",
  "department": "Informatique",
  "hireDate": "2024-01-01",
  "contractType": "CDI",
  "baseSalary": 500000,
  "workStartTime": "08:00",
  "workEndTime": "17:00",
  "workDays": "LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI"
}
```

#### Lister les Employés
```bash
GET /api/employees?page=0&size=20&isActive=true
```

### 💰 Gestion de Paie

#### Calculer une Paie
```bash
POST /api/payroll/calculate
```

**Exemple :**
```json
{
  "employeeId": 1,
  "payrollPeriod": "2024-01",
  "baseSalary": 500000,
  "regularHours": 173.33,
  "overtimeHours": 10.5,
  "performanceBonus": 50000,
  "transportAllowance": 25000,
  "mealAllowance": 15000
}
```

**Réponse :**
```json
{
  "employeeId": 1,
  "period": "2024-01",
  "baseSalary": 500000,
  "grossSalary": 615000,
  "netSalary": 487500,
  "incomeTax": 75000,
  "ipresContribution": 30750,
  "cssContribution": 21525,
  "totalAllowances": 90000,
  "totalDeductions": 127275,
  "calculatedAt": "2024-01-15T10:30:00Z"
}
```

#### Générer un Bulletin de Paie
```bash
POST /api/payslips/generate
```

### 📊 Gestion Financière

#### Créer une Facture
```bash
POST /api/financial/invoices
```

**Exemple :**
```json
{
  "invoiceNumber": "FACT-2024-001",
  "invoiceDate": "2024-01-15",
  "dueDate": "2024-02-15",
  "clientName": "Entreprise ABC SARL",
  "clientAddress": "Avenue Cheikh Anta Diop, Dakar, Sénégal",
  "clientTaxNumber": "1234567890123",
  "subtotalAmount": 1000000,
  "vatRate": 0.18,
  "currency": "XOF",
  "description": "Prestation de services informatiques"
}
```

#### Calculer la TVA
```bash
POST /api/financial/vat/calculate
```

**Exemple :**
```json
{
  "amount": 1000000,
  "vatRate": 0.18,
  "exemptionType": null
}
```

### 👔 Gestion RH

#### Enregistrer une Présence
```bash
POST /api/hr/attendance/record
```

**Exemple :**
```json
{
  "employeeId": 1,
  "workDate": "2024-01-15",
  "scheduledStartTime": "08:00",
  "actualStartTime": "08:15",
  "scheduledEndTime": "17:00",
  "actualEndTime": "17:00",
  "type": "LATE",
  "justification": "Embouteillage sur la VDN - Dakar"
}
```

#### Générer un Rapport d'Assiduité
```bash
POST /api/hr/reports/attendance?startDate=2024-01-01&endDate=2024-01-31
```

## 💡 Exemples d'Utilisation

### Scénario Complet : Gestion d'un Employé

```javascript
// 1. Authentification
const authResponse = await fetch('https://api.bantuops.com/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'admin@bantuops.com',
    password: 'password123'
  })
});

const { token } = await authResponse.json();

// 2. Créer un employé
const employeeResponse = await fetch('https://api.bantuops.com/api/employees', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    employeeNumber: 'EMP-001',
    firstName: 'Amadou',
    lastName: 'Diallo',
    email: 'amadou.diallo@bantuops.com',
    phoneNumber: '+221701234567',
    position: 'Développeur',
    department: 'IT',
    baseSalary: 500000,
    contractType: 'CDI'
  })
});

const employee = await employeeResponse.json();

// 3. Calculer la paie
const payrollResponse = await fetch('https://api.bantuops.com/api/payroll/calculate', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    employeeId: employee.id,
    payrollPeriod: '2024-01',
    baseSalary: 500000,
    regularHours: 173.33
  })
});

const payrollResult = await payrollResponse.json();

// 4. Générer le bulletin
const payslipResponse = await fetch('https://api.bantuops.com/api/payslips/generate', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(payrollResult)
});

const payslip = await payslipResponse.json();
console.log('Bulletin généré:', payslip.documentId);
```

### Exemple Python

```python
import requests
import json

class BantuOpsAPI:
    def __init__(self, base_url, username, password):
        self.base_url = base_url
        self.token = None
        self.authenticate(username, password)
    
    def authenticate(self, username, password):
        response = requests.post(f"{self.base_url}/api/auth/login", 
                               json={"username": username, "password": password})
        if response.status_code == 200:
            self.token = response.json()["token"]
        else:
            raise Exception("Authentication failed")
    
    def get_headers(self):
        return {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json"
        }
    
    def create_employee(self, employee_data):
        response = requests.post(f"{self.base_url}/api/employees",
                               json=employee_data, headers=self.get_headers())
        return response.json()
    
    def calculate_payroll(self, payroll_data):
        response = requests.post(f"{self.base_url}/api/payroll/calculate",
                               json=payroll_data, headers=self.get_headers())
        return response.json()

# Utilisation
api = BantuOpsAPI("https://api.bantuops.com", "admin@bantuops.com", "password123")

employee = api.create_employee({
    "employeeNumber": "EMP-001",
    "firstName": "Amadou",
    "lastName": "Diallo",
    "email": "amadou.diallo@bantuops.com",
    "phoneNumber": "+221701234567",
    "position": "Développeur",
    "baseSalary": 500000
})

payroll = api.calculate_payroll({
    "employeeId": employee["id"],
    "payrollPeriod": "2024-01",
    "baseSalary": 500000
})

print(f"Salaire net: {payroll['netSalary']} FCFA")
```

## ❌ Gestion des Erreurs

### Codes d'Erreur Courants

| Code | Type | Description | Solution |
|------|------|-------------|----------|
| `400` | `VALIDATION_ERROR` | Données invalides | Vérifiez le format des données |
| `401` | `AUTHENTICATION_FAILED` | Token invalide | Reconnectez-vous |
| `403` | `ACCESS_DENIED` | Permissions insuffisantes | Contactez l'administrateur |
| `404` | `RESOURCE_NOT_FOUND` | Ressource inexistante | Vérifiez l'ID |
| `409` | `RESOURCE_CONFLICT` | Ressource déjà existante | Utilisez un autre identifiant |
| `422` | `BUSINESS_RULE_VIOLATION` | Règle métier violée | Respectez les règles métier |
| `429` | `TOO_MANY_REQUESTS` | Trop de requêtes | Attendez avant de réessayer |
| `500` | `INTERNAL_SERVER_ERROR` | Erreur serveur | Contactez le support |

### Exemple de Gestion d'Erreur

```javascript
try {
  const response = await fetch('https://api.bantuops.com/api/employees', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(employeeData)
  });

  if (!response.ok) {
    const error = await response.json();
    
    switch (error.code) {
      case 'VALIDATION_ERROR':
        console.error('Erreurs de validation:', error.details);
        break;
      case 'BUSINESS_RULE_VIOLATION':
        console.error('Règle métier violée:', error.message);
        break;
      default:
        console.error('Erreur:', error.message);
    }
    
    throw new Error(error.message);
  }

  const employee = await response.json();
  console.log('Employé créé:', employee);
  
} catch (error) {
  console.error('Erreur lors de la création:', error.message);
}
```

## 🇸🇳 Conformité Sénégalaise

### Validation des Données

#### Numéro de Téléphone Sénégalais
```regex
^\\+221[0-9]{9}$
```
**Exemples valides :** `+221701234567`, `+221771234567`

#### Numéro Fiscal Sénégalais
```regex
\\d{13}
```
**Exemple valide :** `1234567890123`

#### Numéro de Compte Bancaire (IBAN)
```regex
^[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{16}$
```
**Exemple valide :** `SN12K00100152000025690007542`

### Taux de Taxes et Cotisations

| Type | Taux | Description |
|------|------|-------------|
| **TVA** | 18% | Taxe sur la Valeur Ajoutée |
| **IPRES** | 6% | Institution de Prévoyance Retraite |
| **CSS** | 3.5% | Caisse de Sécurité Sociale |
| **IRPP** | Variable | Impôt sur le Revenu des Personnes Physiques |

### Barème IRPP 2024

| Tranche | Taux | Montant Fixe |
|---------|------|--------------|
| 0 - 630,000 | 0% | 0 |
| 630,001 - 1,500,000 | 20% | 0 |
| 1,500,001 - 4,000,000 | 30% | 174,000 |
| 4,000,001 - 8,300,000 | 35% | 924,000 |
| 8,300,001+ | 40% | 2,429,000 |

## 🛠️ SDKs et Outils

### JavaScript/TypeScript
```bash
npm install @bantuops/api-client
```

```javascript
import { BantuOpsClient } from '@bantuops/api-client';

const client = new BantuOpsClient({
  baseURL: 'https://api.bantuops.com',
  apiKey: 'your-api-key'
});

const employee = await client.employees.create({
  firstName: 'Amadou',
  lastName: 'Diallo',
  // ...
});
```

### Python
```bash
pip install bantuops-api
```

```python
from bantuops import BantuOpsClient

client = BantuOpsClient(
    base_url='https://api.bantuops.com',
    api_key='your-api-key'
)

employee = client.employees.create({
    'firstName': 'Amadou',
    'lastName': 'Diallo',
    # ...
})
```

### PHP
```bash
composer require bantuops/api-client
```

```php
use BantuOps\ApiClient;

$client = new ApiClient([
    'base_url' => 'https://api.bantuops.com',
    'api_key' => 'your-api-key'
]);

$employee = $client->employees->create([
    'firstName' => 'Amadou',
    'lastName' => 'Diallo',
    // ...
]);
```

### Collection Postman

Téléchargez notre collection Postman complète :
```bash
curl -o bantuops-api.postman_collection.json \
  https://api.bantuops.com/docs/postman-collection
```

## 📞 Support

### Canaux de Support

- **Email :** support@bantuops.com
- **Documentation :** https://docs.bantuops.com
- **Status :** https://status.bantuops.com
- **GitHub :** https://github.com/bantuops/api
- **Discord :** https://discord.gg/bantuops

### Heures de Support

- **Lundi - Vendredi :** 8h00 - 18h00 (GMT)
- **Samedi :** 9h00 - 13h00 (GMT)
- **Urgences :** 24h/7j via email

### SLA

| Type | Temps de Réponse | Résolution |
|------|------------------|------------|
| **Critique** | 1 heure | 4 heures |
| **Élevé** | 4 heures | 24 heures |
| **Moyen** | 24 heures | 72 heures |
| **Faible** | 72 heures | 1 semaine |

---

> 💡 **Conseil :** Consultez la [documentation interactive Swagger UI](https://api.bantuops.com/swagger-ui/index.html) pour tester les endpoints en temps réel !