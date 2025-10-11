-- BantuOps Business Entities Schema
-- Additional tables for payroll calculation, HR management, and financial operations

-- Create tax brackets table for Senegalese tax calculation
CREATE TABLE tax_brackets (
    id BIGSERIAL PRIMARY KEY,
    min_income TEXT NOT NULL, -- Encrypted BigDecimal
    max_income TEXT,          -- Encrypted BigDecimal (NULL for highest bracket)
    rate DECIMAL(5,4) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    fixed_amount TEXT,        -- Encrypted BigDecimal
    year INTEGER NOT NULL CHECK (year >= 2020 AND year <= 2100),
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE (year, min_income, is_active),
    CONSTRAINT tax_brackets_income_check CHECK (
        (max_income IS NULL) OR 
        (min_income::DECIMAL < max_income::DECIMAL)
    )
);

-- Create social contribution rates table
CREATE TABLE social_contribution_rates (
    id BIGSERIAL PRIMARY KEY,
    contribution_type VARCHAR(50) NOT NULL CHECK (contribution_type IN ('IPRES', 'CSS', 'FNR', 'CFCE')),
    employee_rate DECIMAL(5,4) NOT NULL CHECK (employee_rate >= 0 AND employee_rate <= 1),
    employer_rate DECIMAL(5,4) NOT NULL CHECK (employer_rate >= 0 AND employer_rate <= 1),
    ceiling_amount TEXT,      -- Encrypted BigDecimal (NULL for no ceiling)
    year INTEGER NOT NULL CHECK (year >= 2020 AND year <= 2100),
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE (contribution_type, year, is_active)
);

-- Create overtime rules table
CREATE TABLE overtime_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- Time thresholds
    daily_threshold_hours INTEGER DEFAULT 8,
    weekly_threshold_hours INTEGER DEFAULT 40,
    monthly_threshold_hours INTEGER DEFAULT 173,
    
    -- Multipliers
    daily_overtime_multiplier DECIMAL(3,2) DEFAULT 1.25,
    weekly_overtime_multiplier DECIMAL(3,2) DEFAULT 1.50,
    night_shift_multiplier DECIMAL(3,2) DEFAULT 1.75,
    weekend_multiplier DECIMAL(3,2) DEFAULT 2.00,
    holiday_multiplier DECIMAL(3,2) DEFAULT 2.50,
    
    -- Validity
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT overtime_rules_dates_check CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT overtime_rules_multipliers_check CHECK (
        daily_overtime_multiplier >= 1.0 AND
        weekly_overtime_multiplier >= 1.0 AND
        night_shift_multiplier >= 1.0 AND
        weekend_multiplier >= 1.0 AND
        holiday_multiplier >= 1.0
    )
);

-- Create attendance rules table
CREATE TABLE attendance_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- Delay rules
    grace_period_minutes INTEGER DEFAULT 15,
    max_delay_minutes INTEGER DEFAULT 120,
    delay_penalty_rate DECIMAL(5,4) DEFAULT 0.0,
    
    -- Absence rules
    max_unexcused_absences_per_month INTEGER DEFAULT 3,
    absence_penalty_rate DECIMAL(5,4) DEFAULT 0.0,
    
    -- Justification requirements
    requires_justification_after_minutes INTEGER DEFAULT 30,
    auto_approve_justified_delays BOOLEAN DEFAULT FALSE,
    
    -- Validity
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT attendance_rules_dates_check CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT attendance_rules_penalties_check CHECK (
        delay_penalty_rate >= 0 AND delay_penalty_rate <= 1 AND
        absence_penalty_rate >= 0 AND absence_penalty_rate <= 1
    )
);

-- Create payroll allowances table
CREATE TABLE payroll_allowances (
    id BIGSERIAL PRIMARY KEY,
    payroll_record_id BIGINT NOT NULL REFERENCES payroll_records(id) ON DELETE CASCADE,
    allowance_type VARCHAR(50) NOT NULL CHECK (allowance_type IN (
        'TRANSPORT', 'MEAL', 'HOUSING', 'FAMILY', 'PERFORMANCE', 'OVERTIME', 'BONUS', 'OTHER'
    )),
    description TEXT,
    amount TEXT NOT NULL, -- Encrypted BigDecimal
    is_taxable BOOLEAN DEFAULT TRUE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT payroll_allowances_unique CHECK (
        (payroll_record_id, allowance_type, description) IS NOT NULL
    )
);

-- Create payroll deductions table
CREATE TABLE payroll_deductions (
    id BIGSERIAL PRIMARY KEY,
    payroll_record_id BIGINT NOT NULL REFERENCES payroll_records(id) ON DELETE CASCADE,
    deduction_type VARCHAR(50) NOT NULL CHECK (deduction_type IN (
        'INCOME_TAX', 'SOCIAL_CONTRIBUTION', 'ADVANCE', 'LOAN', 'INSURANCE', 'PENALTY', 'OTHER'
    )),
    description TEXT,
    amount TEXT NOT NULL, -- Encrypted BigDecimal
    is_mandatory BOOLEAN DEFAULT FALSE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT payroll_deductions_unique CHECK (
        (payroll_record_id, deduction_type, description) IS NOT NULL
    )
);

-- Create invoice line items table
CREATE TABLE invoice_line_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    description TEXT NOT NULL,
    quantity DECIMAL(10,3) NOT NULL DEFAULT 1.0,
    unit_price TEXT NOT NULL, -- Encrypted BigDecimal
    line_total TEXT NOT NULL, -- Encrypted BigDecimal
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE (invoice_id, line_number),
    CONSTRAINT invoice_line_items_quantity_check CHECK (quantity > 0)
);

-- Create company settings table for business rules configuration
CREATE TABLE company_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT,
    setting_type VARCHAR(20) DEFAULT 'STRING' CHECK (setting_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    description TEXT,
    is_encrypted BOOLEAN DEFAULT FALSE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(id)
);

-- Create payroll calculation logs for audit trail
CREATE TABLE payroll_calculation_logs (
    id BIGSERIAL PRIMARY KEY,
    payroll_record_id BIGINT NOT NULL REFERENCES payroll_records(id) ON DELETE CASCADE,
    calculation_step VARCHAR(100) NOT NULL,
    input_parameters TEXT, -- JSON format
    calculation_result TEXT, -- JSON format with encrypted amounts
    calculation_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    calculated_by BIGINT REFERENCES users(id),
    
    -- Indexes
    INDEX idx_payroll_calc_logs_record (payroll_record_id),
    INDEX idx_payroll_calc_logs_timestamp (calculation_timestamp)
);

-- Create security violations table for monitoring
CREATE TABLE security_violations (
    id BIGSERIAL PRIMARY KEY,
    violation_type VARCHAR(50) NOT NULL CHECK (violation_type IN (
        'UNAUTHORIZED_ACCESS', 'INVALID_TOKEN', 'PERMISSION_DENIED', 'SUSPICIOUS_ACTIVITY', 
        'BRUTE_FORCE', 'DATA_BREACH_ATTEMPT', 'ENCRYPTION_FAILURE'
    )),
    user_id BIGINT REFERENCES users(id),
    ip_address INET,
    user_agent TEXT,
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    violation_details TEXT,
    severity VARCHAR(20) DEFAULT 'MEDIUM' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_by BIGINT REFERENCES users(id),
    resolved_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_security_violations_type (violation_type),
    INDEX idx_security_violations_severity (severity),
    INDEX idx_security_violations_timestamp (created_at),
    INDEX idx_security_violations_user (user_id),
    INDEX idx_security_violations_ip (ip_address)
);

-- Create performance indexes for existing tables
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employees_hire_date ON employees(hire_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employees_department_active ON employees(department, is_active);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employees_created_at ON employees(created_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_employee_month ON attendance_records(employee_id, DATE_TRUNC('month', work_date));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_type_status ON attendance_records(attendance_type, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_delay ON attendance_records(delay_minutes) WHERE delay_minutes > 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_employee_year ON payroll_records(employee_id, period_year);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_calculation_date ON payroll_records(calculation_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_status_period ON payroll_records(status, period_year, period_month);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_client ON invoices(client_name);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_status_date ON invoices(status, issue_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_due_date ON invoices(due_date) WHERE status IN ('SENT', 'OVERDUE');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount_date ON financial_transactions(transaction_date, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reference_complete ON financial_transactions(reference_type, reference_id, status);

-- Create composite indexes for complex queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_entity_action_date ON audit_logs(entity_type, action, timestamp);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_user_date ON audit_logs(user_id, timestamp) WHERE user_id IS NOT NULL;

-- Add performance indexes for new tables
CREATE INDEX idx_tax_brackets_year_active ON tax_brackets(year, is_active);
CREATE INDEX idx_social_contrib_year_active ON social_contribution_rates(year, is_active);
CREATE INDEX idx_overtime_rules_active_dates ON overtime_rules(is_active, effective_from, effective_to);
CREATE INDEX idx_attendance_rules_active_dates ON attendance_rules(is_active, effective_from, effective_to);
CREATE INDEX idx_payroll_allowances_record_type ON payroll_allowances(payroll_record_id, allowance_type);
CREATE INDEX idx_payroll_deductions_record_type ON payroll_deductions(payroll_record_id, deduction_type);
CREATE INDEX idx_invoice_line_items_invoice ON invoice_line_items(invoice_id, line_number);
CREATE INDEX idx_company_settings_key ON company_settings(setting_key);

-- Insert default tax brackets for Senegal (2024 rates)
INSERT INTO tax_brackets (min_income, max_income, rate, fixed_amount, year) VALUES
('0', '630000', 0.0, '0', 2024),           -- 0% for income up to 630,000 XOF
('630001', '1500000', 0.20, '0', 2024),    -- 20% for income 630,001 to 1,500,000 XOF
('1500001', '4000000', 0.30, '174000', 2024), -- 30% for income 1,500,001 to 4,000,000 XOF
('4000001', '8000000', 0.35, '924000', 2024),  -- 35% for income 4,000,001 to 8,000,000 XOF
('8000001', NULL, 0.40, '2324000', 2024);      -- 40% for income above 8,000,000 XOF

-- Insert default social contribution rates for Senegal
INSERT INTO social_contribution_rates (contribution_type, employee_rate, employer_rate, ceiling_amount, year) VALUES
('IPRES', 0.06, 0.08, '1800000', 2024),  -- IPRES: 6% employee, 8% employer, ceiling 1,800,000 XOF
('CSS', 0.01, 0.07, NULL, 2024),         -- CSS: 1% employee, 7% employer, no ceiling
('FNR', 0.00, 0.03, NULL, 2024),         -- FNR: 0% employee, 3% employer
('CFCE', 0.00, 0.02, NULL, 2024);        -- CFCE: 0% employee, 2% employer

-- Insert default overtime rules
INSERT INTO overtime_rules (rule_name, description) VALUES
('Standard Senegal Labor Code', 'Default overtime rules according to Senegalese Labor Code');

-- Insert default attendance rules
INSERT INTO attendance_rules (rule_name, description) VALUES
('Standard Company Policy', 'Default attendance rules for company policy');

-- Insert default company settings
INSERT INTO company_settings (setting_key, setting_value, setting_type, description) VALUES
('COMPANY_NAME', 'BantuOps Company', 'STRING', 'Company name for documents'),
('COMPANY_ADDRESS', '', 'STRING', 'Company address'),
('COMPANY_PHONE', '', 'STRING', 'Company phone number'),
('COMPANY_EMAIL', '', 'STRING', 'Company email address'),
('COMPANY_TAX_NUMBER', '', 'STRING', 'Company tax identification number'),
('DEFAULT_CURRENCY', 'XOF', 'STRING', 'Default currency code'),
('PAYROLL_CALCULATION_METHOD', 'MONTHLY', 'STRING', 'Default payroll calculation method'),
('WORKING_DAYS_PER_WEEK', '5', 'NUMBER', 'Standard working days per week'),
('WORKING_HOURS_PER_DAY', '8', 'NUMBER', 'Standard working hours per day'),
('GRACE_PERIOD_MINUTES', '15', 'NUMBER', 'Grace period for late arrivals'),
('AUTO_CALCULATE_OVERTIME', 'true', 'BOOLEAN', 'Automatically calculate overtime'),
('REQUIRE_ATTENDANCE_APPROVAL', 'true', 'BOOLEAN', 'Require approval for attendance records');

-- Add security constraints
ALTER TABLE employees ADD CONSTRAINT employees_email_format_check 
    CHECK (email IS NULL OR email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

ALTER TABLE employees ADD CONSTRAINT employees_phone_senegal_check 
    CHECK (phone_number IS NULL OR validate_senegal_phone_number(phone_number));

-- Add data integrity constraints
ALTER TABLE payroll_records ADD CONSTRAINT payroll_records_period_check 
    CHECK (period_year >= 2020 AND period_month BETWEEN 1 AND 12);

ALTER TABLE attendance_records ADD CONSTRAINT attendance_records_work_date_check 
    CHECK (work_date <= CURRENT_DATE AND work_date >= '2020-01-01');

ALTER TABLE invoices ADD CONSTRAINT invoices_amounts_positive_check 
    CHECK (vat_rate >= 0 AND vat_rate <= 1);

-- Add foreign key constraints with proper cascading
ALTER TABLE payroll_allowances ADD CONSTRAINT fk_payroll_allowances_payroll 
    FOREIGN KEY (payroll_record_id) REFERENCES payroll_records(id) ON DELETE CASCADE;

ALTER TABLE payroll_deductions ADD CONSTRAINT fk_payroll_deductions_payroll 
    FOREIGN KEY (payroll_record_id) REFERENCES payroll_records(id) ON DELETE CASCADE;

ALTER TABLE invoice_line_items ADD CONSTRAINT fk_invoice_line_items_invoice 
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE;

-- Add check constraints for business rules
ALTER TABLE tax_brackets ADD CONSTRAINT tax_brackets_rate_valid 
    CHECK (rate >= 0 AND rate <= 1);

ALTER TABLE social_contribution_rates ADD CONSTRAINT social_contrib_rates_valid 
    CHECK (employee_rate >= 0 AND employee_rate <= 1 AND employer_rate >= 0 AND employer_rate <= 1);

-- Create partial indexes for better performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employees_active_department 
    ON employees(department) WHERE is_active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_pending_approval 
    ON payroll_records(employee_id, period_year, period_month) 
    WHERE status IN ('DRAFT', 'CALCULATED');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_pending_approval 
    ON attendance_records(employee_id, work_date) 
    WHERE status = 'PENDING';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_overdue 
    ON invoices(due_date, client_name) 
    WHERE status IN ('SENT', 'OVERDUE') AND due_date < CURRENT_DATE;

-- Add comments for documentation
COMMENT ON TABLE tax_brackets IS 'Tax brackets for Senegalese income tax calculation';
COMMENT ON TABLE social_contribution_rates IS 'Social contribution rates for IPRES, CSS, FNR, CFCE';
COMMENT ON TABLE overtime_rules IS 'Overtime calculation rules and multipliers';
COMMENT ON TABLE attendance_rules IS 'Attendance policy rules and penalties';
COMMENT ON TABLE payroll_allowances IS 'Payroll allowances and benefits';
COMMENT ON TABLE payroll_deductions IS 'Payroll deductions and taxes';
COMMENT ON TABLE invoice_line_items IS 'Individual line items for invoices';
COMMENT ON TABLE company_settings IS 'Company-wide configuration settings';
COMMENT ON TABLE payroll_calculation_logs IS 'Audit trail for payroll calculations';
COMMENT ON TABLE security_violations IS 'Security incident tracking and monitoring';