# BantuOps Database Schema Configuration Summary

## Task 1.3: Configurer la base de données PostgreSQL - COMPLETED

This document summarizes the PostgreSQL database configuration implemented for the BantuOps backend migration.

## ✅ Sub-task: Créer les schémas de tables avec champs chiffrés

### Core Business Tables Created:

1. **employees** - Employee records with encrypted personal information
   - Encrypted fields: first_name, last_name, email, phone_number, national_id, base_salary
   - Includes employment information, work schedule, and audit fields

2. **payroll_records** - Payroll calculations with encrypted salary data
   - Encrypted fields: base_salary, overtime_amount, allowances_total, deductions_total, gross_salary, income_tax, social_contributions, net_salary
   - Includes period tracking and approval workflow

3. **financial_transactions** - Financial transactions with encrypted amounts
   - Encrypted fields: amount
   - Includes transaction types, status tracking, and bank information

4. **invoices** - Client invoices with encrypted financial data
   - Encrypted fields: subtotal_amount, vat_amount, total_amount
   - Includes client information and payment tracking

5. **attendance_records** - Employee attendance tracking
   - Includes delay calculations, justifications, and approval workflow

### Supporting Business Tables:

6. **tax_brackets** - Senegalese tax calculation brackets
   - Encrypted fields: min_income, max_income, fixed_amount
   - Pre-populated with 2024 Senegalese tax rates

7. **social_contribution_rates** - Social contribution rates (IPRES, CSS, FNR, CFCE)
   - Pre-populated with Senegalese social security rates

8. **overtime_rules** - Overtime calculation rules and multipliers
   - Configurable rules according to Senegalese Labor Code

9. **attendance_rules** - Attendance policy rules and penalties
   - Configurable grace periods and penalty rates

10. **payroll_allowances** - Individual payroll allowances
    - Encrypted fields: amount
    - Linked to payroll records

11. **payroll_deductions** - Individual payroll deductions
    - Encrypted fields: amount
    - Linked to payroll records

12. **invoice_line_items** - Invoice line item details
    - Encrypted fields: unit_price, line_total

13. **company_settings** - Company-wide configuration settings
    - Supports encrypted settings for sensitive configuration

14. **payroll_calculation_logs** - Audit trail for payroll calculations
    - Tracks calculation steps and parameters

15. **security_violations** - Security incident tracking
    - Monitors unauthorized access attempts and security breaches

## ✅ Sub-task: Configurer les index pour les performances

### Performance Indexes Created:

#### Primary Performance Indexes:
- `idx_employees_number` - Employee number lookup
- `idx_employees_active` - Active employee filtering
- `idx_employees_department` - Department-based queries
- `idx_attendance_employee_date` - Attendance by employee and date
- `idx_payroll_employee_period` - Payroll by employee and period
- `idx_invoices_status_date` - Invoice status and date queries
- `idx_transactions_type_date_status` - Transaction filtering

#### Composite Indexes for Complex Queries:
- `idx_payroll_employee_status_period` - Multi-column payroll queries
- `idx_attendance_employee_type_date` - Attendance analysis
- `idx_transactions_reference_date` - Transaction reference tracking
- `idx_invoices_client_status_date` - Client invoice management

#### Partial Indexes for Specific Use Cases:
- `idx_employees_active_recent` - Recently hired active employees
- `idx_payroll_pending_approval` - Pending payroll approvals
- `idx_attendance_pending_approval` - Pending attendance approvals
- `idx_invoices_overdue` - Overdue invoice tracking

#### Expression Indexes:
- `idx_employees_full_name_search` - Full name search capability
- `idx_attendance_work_month` - Monthly attendance aggregation
- `idx_payroll_period_key` - Period-based payroll lookup

### Materialized Views for Performance:
1. **mv_employee_statistics** - Department-wise employee statistics
2. **mv_monthly_payroll_summary** - Monthly payroll processing summary
3. **mv_attendance_statistics** - Monthly attendance trends

## ✅ Sub-task: Implémenter les contraintes de sécurité et intégrité

### Security Constraints:

#### Data Validation Constraints:
- Email format validation for employees
- Senegalese phone number validation
- Date range validations (hire dates, work dates)
- Positive amount validations for financial fields
- Tax rate validations (0-100%)

#### Business Rule Constraints:
- Payroll period validation (2020-2100, months 1-12)
- Attendance date validation (not future dates)
- Employee termination date logic
- Working hours validation (0-60 hours per week)

#### Referential Integrity:
- Foreign key constraints with proper cascading
- Unique constraints on business keys
- Check constraints for enumerated values

### Security Functions:
1. **validate_senegal_phone_number_enhanced()** - Enhanced phone validation
2. **validate_senegal_ninea()** - Tax number validation
3. **validate_senegal_bank_account_enhanced()** - Bank account validation
4. **validate_payroll_calculation()** - Payroll integrity checks
5. **security_check_trigger()** - Unauthorized access detection

## ✅ Sub-task: Ajouter les triggers d'audit automatiques

### Audit Triggers Implemented:

#### Automatic Audit Triggers:
- All sensitive tables have INSERT/UPDATE/DELETE audit triggers
- Captures old and new values in JSON format
- Records user information, IP address, and session details
- Timestamps all operations

#### Tables with Audit Triggers:
- users, employees, payroll_records, financial_transactions
- invoices, attendance_records, tax_brackets, social_contribution_rates
- overtime_rules, attendance_rules, payroll_allowances, payroll_deductions
- invoice_line_items, company_settings, security_violations

#### Audit Context Management:
- `set_audit_context()` - Sets user context for audit trails
- `clear_audit_context()` - Clears audit context
- Session-based audit information tracking

#### Specialized Audit Functions:
- `payroll_integrity_check_trigger()` - Validates payroll calculations
- `performance_monitoring_trigger()` - Tracks slow operations
- `update_updated_at_column()` - Automatic timestamp updates

### Additional Security Features:

#### Business Rule Validation:
- `is_business_hours()` - Validates business hour operations
- `is_senegal_public_holiday()` - Holiday validation
- `calculate_working_days()` - Working day calculations

#### Maintenance and Monitoring:
- `cleanup_old_audit_logs()` - 7-year retention policy
- `database_health_check()` - Comprehensive health monitoring
- `monitor_database_connections()` - Connection pool monitoring
- `refresh_all_materialized_views()` - Performance view updates

## Database Migration Files Created:

1. **V3__Create_business_entities_schema.sql** - Core business tables and indexes
2. **V4__Create_business_entities_audit_triggers.sql** - Audit triggers and security functions
3. **V5__Create_performance_optimizations.sql** - Performance optimizations and monitoring

## Pre-populated Data:

### Senegalese Tax Brackets (2024):
- 0% for income up to 630,000 XOF
- 20% for income 630,001 to 1,500,000 XOF
- 30% for income 1,500,001 to 4,000,000 XOF
- 35% for income 4,000,001 to 8,000,000 XOF
- 40% for income above 8,000,000 XOF

### Social Contribution Rates:
- **IPRES**: 6% employee, 8% employer (ceiling: 1,800,000 XOF)
- **CSS**: 1% employee, 7% employer
- **FNR**: 0% employee, 3% employer
- **CFCE**: 0% employee, 2% employer

### Default Company Settings:
- Currency: XOF (West African CFA franc)
- Working days: 5 per week
- Working hours: 8 per day
- Grace period: 15 minutes for late arrivals

## Security Features Implemented:

1. **Data Encryption**: All sensitive fields use encryption converters
2. **Audit Trail**: Complete audit logging for all operations
3. **Access Control**: Role-based permissions and security checks
4. **Data Validation**: Senegalese business rule validation
5. **Performance Monitoring**: Slow query detection and logging
6. **Security Violation Tracking**: Unauthorized access monitoring

## Compliance Features:

1. **Senegalese Labor Law**: Overtime rules and working hour regulations
2. **Tax Compliance**: Official tax brackets and social contribution rates
3. **Data Retention**: 7-year audit log retention policy
4. **Financial Regulations**: VAT calculations and invoice requirements

## Performance Optimizations:

1. **Indexing Strategy**: Comprehensive indexing for all query patterns
2. **Materialized Views**: Pre-computed aggregations for reporting
3. **Partitioning**: Audit log partitioning by month for scalability
4. **Connection Monitoring**: Database connection pool optimization
5. **Query Performance**: Slow query detection and optimization

## Task Completion Status: ✅ COMPLETED

All sub-tasks have been successfully implemented:
- ✅ Database schemas created with encrypted fields
- ✅ Performance indexes configured
- ✅ Security and integrity constraints implemented
- ✅ Automatic audit triggers added

The PostgreSQL database is now fully configured with:
- Secure encrypted storage for sensitive data
- Comprehensive audit trails
- Performance-optimized queries
- Senegalese business rule compliance
- Robust security monitoring

## Next Steps:

The database schema is ready for the backend application to use. The next tasks in the implementation plan can now proceed with:
- Entity model implementation (Task 2.1)
- Repository development (Task 2.2)
- Service layer implementation (Task 3.1+)

All database foundations are in place to support the complete BantuOps backend migration.