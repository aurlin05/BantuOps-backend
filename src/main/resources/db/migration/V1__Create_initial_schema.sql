-- BantuOps Database Schema
-- Initial schema creation with security and audit features

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create audit log table first (referenced by triggers)
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'VIEW')),
    user_id BIGINT,
    user_email VARCHAR(255),
    user_role VARCHAR(50),
    old_values TEXT,
    new_values TEXT,
    ip_address INET,
    user_agent TEXT,
    session_id VARCHAR(255),
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes for performance
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_user (user_id),
    INDEX idx_audit_logs_timestamp (timestamp),
    INDEX idx_audit_logs_action (action)
);

-- Create permissions table
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    resource VARCHAR(100),
    action VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create user roles table
CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create role permissions junction table
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES user_roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Create users table with encrypted sensitive fields
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name TEXT, -- Encrypted field
    last_name TEXT,  -- Encrypted field
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT users_email_check CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- Create user roles junction table
CREATE TABLE user_user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES user_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Create employees table with encrypted personal information
CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    employee_number VARCHAR(50) UNIQUE NOT NULL,
    
    -- Personal Information (encrypted)
    first_name TEXT, -- Encrypted
    last_name TEXT,  -- Encrypted
    email TEXT,      -- Encrypted
    phone_number TEXT, -- Encrypted
    national_id TEXT,  -- Encrypted
    date_of_birth DATE,
    
    -- Employment Information
    position VARCHAR(100),
    department VARCHAR(100),
    hire_date DATE NOT NULL,
    contract_type VARCHAR(20) CHECK (contract_type IN ('CDI', 'CDD', 'STAGE', 'FREELANCE')),
    base_salary TEXT, -- Encrypted BigDecimal
    
    -- Work Schedule
    work_hours_per_week INTEGER DEFAULT 40,
    start_time TIME DEFAULT '08:00:00',
    end_time TIME DEFAULT '17:00:00',
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    termination_date DATE,
    termination_reason TEXT,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    
    -- Constraints
    CONSTRAINT employees_hire_date_check CHECK (hire_date <= CURRENT_DATE),
    CONSTRAINT employees_termination_check CHECK (termination_date IS NULL OR termination_date >= hire_date),
    CONSTRAINT employees_work_hours_check CHECK (work_hours_per_week > 0 AND work_hours_per_week <= 60)
);

-- Create attendance records table
CREATE TABLE attendance_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    scheduled_start_time TIME NOT NULL,
    actual_start_time TIME,
    scheduled_end_time TIME NOT NULL,
    actual_end_time TIME,
    attendance_type VARCHAR(20) DEFAULT 'PRESENT' CHECK (attendance_type IN ('PRESENT', 'LATE', 'ABSENT', 'HALF_DAY', 'SICK_LEAVE', 'VACATION')),
    delay_minutes INTEGER DEFAULT 0,
    justification TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    approved_by BIGINT REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE (employee_id, work_date),
    CONSTRAINT attendance_delay_check CHECK (delay_minutes >= 0),
    CONSTRAINT attendance_times_check CHECK (
        (actual_start_time IS NULL AND actual_end_time IS NULL) OR
        (actual_start_time IS NOT NULL AND actual_end_time IS NOT NULL AND actual_end_time > actual_start_time)
    )
);

-- Create invoices table with encrypted financial data
CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    client_email VARCHAR(255),
    client_address TEXT,
    
    -- Financial data (encrypted)
    subtotal_amount TEXT, -- Encrypted BigDecimal
    vat_rate DECIMAL(5,4) DEFAULT 0.18, -- 18% VAT for Senegal
    vat_amount TEXT,      -- Encrypted BigDecimal
    total_amount TEXT,    -- Encrypted BigDecimal
    
    -- Status and dates
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED')),
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    paid_date DATE,
    
    -- Additional information
    description TEXT,
    notes TEXT,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    
    -- Constraints
    CONSTRAINT invoices_due_date_check CHECK (due_date >= issue_date),
    CONSTRAINT invoices_paid_date_check CHECK (paid_date IS NULL OR paid_date >= issue_date),
    CONSTRAINT invoices_vat_rate_check CHECK (vat_rate >= 0 AND vat_rate <= 1)
);

-- Create payroll records table with encrypted salary data
CREATE TABLE payroll_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    
    -- Salary components (encrypted)
    base_salary TEXT,           -- Encrypted BigDecimal
    overtime_amount TEXT,       -- Encrypted BigDecimal
    allowances_total TEXT,      -- Encrypted BigDecimal
    deductions_total TEXT,      -- Encrypted BigDecimal
    gross_salary TEXT,          -- Encrypted BigDecimal
    
    -- Tax calculations (encrypted)
    income_tax TEXT,            -- Encrypted BigDecimal
    social_contributions TEXT,  -- Encrypted BigDecimal
    net_salary TEXT,           -- Encrypted BigDecimal
    
    -- Status and processing
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'PAID')),
    calculation_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approval_date TIMESTAMP WITH TIME ZONE,
    payment_date TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    approved_by BIGINT REFERENCES users(id),
    
    -- Constraints
    UNIQUE (employee_id, period_year, period_month),
    CONSTRAINT payroll_period_year_check CHECK (period_year >= 2020 AND period_year <= 2100)
);

-- Create financial transactions table
CREATE TABLE financial_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'PAYROLL', 'TAX', 'INVOICE_PAYMENT')),
    reference_id BIGINT, -- Reference to related entity (invoice, payroll, etc.)
    reference_type VARCHAR(50), -- Type of referenced entity
    
    -- Transaction details
    description TEXT NOT NULL,
    amount TEXT NOT NULL, -- Encrypted BigDecimal
    currency VARCHAR(3) DEFAULT 'XOF', -- West African CFA franc
    
    -- Dates
    transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,
    value_date DATE,
    
    -- Bank information
    bank_account VARCHAR(100),
    bank_reference VARCHAR(100),
    
    -- Status
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id)
);

-- Create indexes for performance optimization
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_employees_number ON employees(employee_number);
CREATE INDEX idx_employees_active ON employees(is_active);
CREATE INDEX idx_employees_department ON employees(department);
CREATE INDEX idx_attendance_employee_date ON attendance_records(employee_id, work_date);
CREATE INDEX idx_attendance_date ON attendance_records(work_date);
CREATE INDEX idx_attendance_status ON attendance_records(status);
CREATE INDEX idx_invoices_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_dates ON invoices(issue_date, due_date);
CREATE INDEX idx_payroll_employee_period ON payroll_records(employee_id, period_year, period_month);
CREATE INDEX idx_payroll_period ON payroll_records(period_year, period_month);
CREATE INDEX idx_payroll_status ON payroll_records(status);
CREATE INDEX idx_transactions_type ON financial_transactions(transaction_type);
CREATE INDEX idx_transactions_date ON financial_transactions(transaction_date);
CREATE INDEX idx_transactions_reference ON financial_transactions(reference_type, reference_id);

-- Insert default permissions
INSERT INTO permissions (name, description, resource, action) VALUES
('USER_READ', 'Read user information', 'USER', 'READ'),
('USER_WRITE', 'Create and update users', 'USER', 'WRITE'),
('USER_DELETE', 'Delete users', 'USER', 'DELETE'),
('EMPLOYEE_READ', 'Read employee information', 'EMPLOYEE', 'READ'),
('EMPLOYEE_WRITE', 'Create and update employees', 'EMPLOYEE', 'WRITE'),
('EMPLOYEE_DELETE', 'Delete employees', 'EMPLOYEE', 'DELETE'),
('PAYROLL_READ', 'Read payroll information', 'PAYROLL', 'READ'),
('PAYROLL_WRITE', 'Create and update payroll', 'PAYROLL', 'WRITE'),
('PAYROLL_CALCULATE', 'Calculate payroll', 'PAYROLL', 'CALCULATE'),
('FINANCIAL_READ', 'Read financial information', 'FINANCIAL', 'READ'),
('FINANCIAL_WRITE', 'Create and update financial records', 'FINANCIAL', 'WRITE'),
('FINANCIAL_EXPORT', 'Export financial data', 'FINANCIAL', 'EXPORT'),
('ATTENDANCE_READ', 'Read attendance records', 'ATTENDANCE', 'READ'),
('ATTENDANCE_WRITE', 'Create and update attendance', 'ATTENDANCE', 'WRITE'),
('ATTENDANCE_APPROVE', 'Approve attendance records', 'ATTENDANCE', 'APPROVE'),
('AUDIT_READ', 'Read audit logs', 'AUDIT', 'READ'),
('SYSTEM_ADMIN', 'Full system administration', 'SYSTEM', 'ADMIN');

-- Insert default roles
INSERT INTO user_roles (name, description) VALUES
('ADMIN', 'System administrator with full access'),
('HR', 'Human resources manager with employee and payroll access'),
('USER', 'Regular user with limited access');

-- Assign permissions to roles
-- ADMIN role gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM user_roles r, permissions p WHERE r.name = 'ADMIN';

-- HR role gets employee, payroll, attendance, and financial permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM user_roles r, permissions p 
WHERE r.name = 'HR' AND p.name IN (
    'EMPLOYEE_READ', 'EMPLOYEE_WRITE', 'PAYROLL_READ', 'PAYROLL_WRITE', 'PAYROLL_CALCULATE',
    'FINANCIAL_READ', 'FINANCIAL_WRITE', 'ATTENDANCE_READ', 'ATTENDANCE_WRITE', 'ATTENDANCE_APPROVE'
);

-- USER role gets basic read permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM user_roles r, permissions p 
WHERE r.name = 'USER' AND p.name IN ('USER_READ', 'EMPLOYEE_READ', 'ATTENDANCE_READ');