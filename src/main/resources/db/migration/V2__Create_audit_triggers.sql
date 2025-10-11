-- Audit Triggers for BantuOps Database
-- Automatically track changes to sensitive tables

-- Create audit trigger function
CREATE OR REPLACE FUNCTION audit_trigger_function()
RETURNS TRIGGER AS $$
DECLARE
    old_data TEXT;
    new_data TEXT;
    current_user_id BIGINT;
    current_user_email VARCHAR(255);
    current_user_role VARCHAR(50);
BEGIN
    -- Get current user information from session variables if available
    current_user_id := COALESCE(current_setting('app.current_user_id', true)::BIGINT, NULL);
    current_user_email := COALESCE(current_setting('app.current_user_email', true), 'system');
    current_user_role := COALESCE(current_setting('app.current_user_role', true), 'system');

    -- Convert old and new data to JSON
    IF TG_OP = 'DELETE' THEN
        old_data := row_to_json(OLD)::TEXT;
        new_data := NULL;
    ELSIF TG_OP = 'INSERT' THEN
        old_data := NULL;
        new_data := row_to_json(NEW)::TEXT;
    ELSIF TG_OP = 'UPDATE' THEN
        old_data := row_to_json(OLD)::TEXT;
        new_data := row_to_json(NEW)::TEXT;
    END IF;

    -- Insert audit record
    INSERT INTO audit_logs (
        entity_type,
        entity_id,
        action,
        user_id,
        user_email,
        user_role,
        old_values,
        new_values,
        ip_address,
        user_agent,
        session_id
    ) VALUES (
        TG_TABLE_NAME,
        CASE 
            WHEN TG_OP = 'DELETE' THEN (OLD.id)::BIGINT
            ELSE (NEW.id)::BIGINT
        END,
        TG_OP,
        current_user_id,
        current_user_email,
        current_user_role,
        old_data,
        new_data,
        COALESCE(current_setting('app.client_ip', true)::INET, NULL),
        COALESCE(current_setting('app.user_agent', true), NULL),
        COALESCE(current_setting('app.session_id', true), NULL)
    );

    -- Return appropriate record
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create audit triggers for sensitive tables

-- Users table audit
CREATE TRIGGER users_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Employees table audit
CREATE TRIGGER employees_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON employees
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Payroll records audit
CREATE TRIGGER payroll_records_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON payroll_records
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Financial transactions audit
CREATE TRIGGER financial_transactions_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Invoices audit
CREATE TRIGGER invoices_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Attendance records audit
CREATE TRIGGER attendance_records_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON attendance_records
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Create function to set session variables for audit context
CREATE OR REPLACE FUNCTION set_audit_context(
    p_user_id BIGINT,
    p_user_email VARCHAR(255),
    p_user_role VARCHAR(50),
    p_client_ip INET DEFAULT NULL,
    p_user_agent TEXT DEFAULT NULL,
    p_session_id VARCHAR(255) DEFAULT NULL
)
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_user_id', p_user_id::TEXT, true);
    PERFORM set_config('app.current_user_email', p_user_email, true);
    PERFORM set_config('app.current_user_role', p_user_role, true);
    
    IF p_client_ip IS NOT NULL THEN
        PERFORM set_config('app.client_ip', p_client_ip::TEXT, true);
    END IF;
    
    IF p_user_agent IS NOT NULL THEN
        PERFORM set_config('app.user_agent', p_user_agent, true);
    END IF;
    
    IF p_session_id IS NOT NULL THEN
        PERFORM set_config('app.session_id', p_session_id, true);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create function to clear audit context
CREATE OR REPLACE FUNCTION clear_audit_context()
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_user_id', '', true);
    PERFORM set_config('app.current_user_email', '', true);
    PERFORM set_config('app.current_user_role', '', true);
    PERFORM set_config('app.client_ip', '', true);
    PERFORM set_config('app.user_agent', '', true);
    PERFORM set_config('app.session_id', '', true);
END;
$$ LANGUAGE plpgsql;

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create updated_at triggers for tables that have updated_at column
CREATE TRIGGER users_updated_at_trigger
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER employees_updated_at_trigger
    BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER attendance_records_updated_at_trigger
    BEFORE UPDATE ON attendance_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER invoices_updated_at_trigger
    BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER payroll_records_updated_at_trigger
    BEFORE UPDATE ON payroll_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER financial_transactions_updated_at_trigger
    BEFORE UPDATE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create function for data integrity checks
CREATE OR REPLACE FUNCTION validate_senegal_phone_number(phone_number TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    -- Senegalese phone numbers: +221 XX XXX XX XX or 77/78/70/76/75 XXX XX XX
    RETURN phone_number ~ '^(\+221|00221)?[7][0-8][0-9]{7}$';
END;
$$ LANGUAGE plpgsql;

-- Create function for Senegalese tax number validation
CREATE OR REPLACE FUNCTION validate_senegal_tax_number(tax_number TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    -- Basic validation for Senegalese tax numbers (NINEA format)
    RETURN tax_number ~ '^[0-9]{13}$';
END;
$$ LANGUAGE plpgsql;

-- Create function for bank account validation
CREATE OR REPLACE FUNCTION validate_senegal_bank_account(account_number TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    -- Basic validation for Senegalese bank account numbers
    RETURN LENGTH(account_number) >= 10 AND LENGTH(account_number) <= 20 AND account_number ~ '^[0-9]+$';
END;
$$ LANGUAGE plpgsql;

-- Add comments to tables for documentation
COMMENT ON TABLE users IS 'System users with authentication and authorization';
COMMENT ON TABLE employees IS 'Employee records with encrypted personal information';
COMMENT ON TABLE payroll_records IS 'Payroll calculations with encrypted salary data';
COMMENT ON TABLE financial_transactions IS 'Financial transactions with encrypted amounts';
COMMENT ON TABLE invoices IS 'Client invoices with encrypted financial data';
COMMENT ON TABLE attendance_records IS 'Employee attendance tracking';
COMMENT ON TABLE audit_logs IS 'Audit trail for all sensitive operations';

-- Add column comments for encrypted fields
COMMENT ON COLUMN employees.first_name IS 'Encrypted personal information';
COMMENT ON COLUMN employees.last_name IS 'Encrypted personal information';
COMMENT ON COLUMN employees.email IS 'Encrypted personal information';
COMMENT ON COLUMN employees.phone_number IS 'Encrypted personal information';
COMMENT ON COLUMN employees.national_id IS 'Encrypted personal information';
COMMENT ON COLUMN employees.base_salary IS 'Encrypted salary information';
COMMENT ON COLUMN payroll_records.base_salary IS 'Encrypted salary component';
COMMENT ON COLUMN payroll_records.gross_salary IS 'Encrypted salary component';
COMMENT ON COLUMN payroll_records.net_salary IS 'Encrypted salary component';
COMMENT ON COLUMN invoices.total_amount IS 'Encrypted financial amount';
COMMENT ON COLUMN financial_transactions.amount IS 'Encrypted transaction amount';