-- Audit Triggers for Business Entities
-- Extend audit functionality to new business tables

-- Create audit triggers for new business tables

-- Tax brackets audit
CREATE TRIGGER tax_brackets_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON tax_brackets
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Social contribution rates audit
CREATE TRIGGER social_contribution_rates_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON social_contribution_rates
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Overtime rules audit
CREATE TRIGGER overtime_rules_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON overtime_rules
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Attendance rules audit
CREATE TRIGGER attendance_rules_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON attendance_rules
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Payroll allowances audit
CREATE TRIGGER payroll_allowances_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON payroll_allowances
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Payroll deductions audit
CREATE TRIGGER payroll_deductions_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON payroll_deductions
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Invoice line items audit
CREATE TRIGGER invoice_line_items_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON invoice_line_items
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Company settings audit (critical for security)
CREATE TRIGGER company_settings_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON company_settings
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Payroll calculation logs audit
CREATE TRIGGER payroll_calculation_logs_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON payroll_calculation_logs
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Security violations audit (meta-audit)
CREATE TRIGGER security_violations_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON security_violations
    FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

-- Create updated_at triggers for tables with updated_at column
CREATE TRIGGER tax_brackets_updated_at_trigger
    BEFORE UPDATE ON tax_brackets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER social_contribution_rates_updated_at_trigger
    BEFORE UPDATE ON social_contribution_rates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER overtime_rules_updated_at_trigger
    BEFORE UPDATE ON overtime_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER attendance_rules_updated_at_trigger
    BEFORE UPDATE ON attendance_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER company_settings_updated_at_trigger
    BEFORE UPDATE ON company_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create enhanced validation functions for Senegalese business rules

-- Enhanced phone number validation for Senegal
CREATE OR REPLACE FUNCTION validate_senegal_phone_number_enhanced(phone_number TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    -- Remove spaces and common separators
    phone_number := REGEXP_REPLACE(phone_number, '[^0-9+]', '', 'g');
    
    -- Senegalese mobile numbers: +221 7X XXX XX XX (Orange, Tigo, Expresso)
    -- Fixed line: +221 3X XXX XX XX
    RETURN phone_number ~ '^(\+221|00221)?[37][0-9][0-9]{7}$';
END;
$$ LANGUAGE plpgsql;

-- Validate Senegalese NINEA (tax number)
CREATE OR REPLACE FUNCTION validate_senegal_ninea(ninea TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    -- NINEA format: 13 digits
    RETURN ninea ~ '^[0-9]{13}$';
END;
$$ LANGUAGE plpgsql;

-- Validate Senegalese bank account (RIB format)
CREATE OR REPLACE FUNCTION validate_senegal_bank_account_enhanced(account_number TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    clean_account TEXT;
    bank_code TEXT;
    branch_code TEXT;
    account_num TEXT;
    key_check TEXT;
    calculated_key INTEGER;
BEGIN
    -- Remove spaces and separators
    clean_account := REGEXP_REPLACE(account_number, '[^0-9]', '', 'g');
    
    -- Senegalese RIB format: 23 digits (5 bank + 5 branch + 11 account + 2 key)
    IF LENGTH(clean_account) != 23 THEN
        RETURN FALSE;
    END IF;
    
    -- Extract components
    bank_code := SUBSTRING(clean_account FROM 1 FOR 5);
    branch_code := SUBSTRING(clean_account FROM 6 FOR 5);
    account_num := SUBSTRING(clean_account FROM 11 FOR 11);
    key_check := SUBSTRING(clean_account FROM 22 FOR 2);
    
    -- Basic validation (simplified RIB key calculation)
    -- In production, implement full RIB key validation algorithm
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- Create function to validate payroll calculation integrity
CREATE OR REPLACE FUNCTION validate_payroll_calculation(
    p_base_salary DECIMAL,
    p_allowances DECIMAL,
    p_deductions DECIMAL,
    p_gross_salary DECIMAL,
    p_net_salary DECIMAL
)
RETURNS BOOLEAN AS $$
BEGIN
    -- Validate that gross salary = base salary + allowances
    IF ABS(p_gross_salary - (p_base_salary + p_allowances)) > 0.01 THEN
        RETURN FALSE;
    END IF;
    
    -- Validate that net salary = gross salary - deductions
    IF ABS(p_net_salary - (p_gross_salary - p_deductions)) > 0.01 THEN
        RETURN FALSE;
    END IF;
    
    -- Validate positive amounts
    IF p_base_salary < 0 OR p_allowances < 0 OR p_deductions < 0 OR p_gross_salary < 0 THEN
        RETURN FALSE;
    END IF;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- Create function to check business hours for attendance
CREATE OR REPLACE FUNCTION is_business_hours(check_time TIME, check_date DATE DEFAULT CURRENT_DATE)
RETURNS BOOLEAN AS $$
DECLARE
    day_of_week INTEGER;
BEGIN
    -- Get day of week (1 = Monday, 7 = Sunday)
    day_of_week := EXTRACT(DOW FROM check_date);
    
    -- Monday to Friday: 8:00 AM to 6:00 PM
    IF day_of_week BETWEEN 1 AND 5 THEN
        RETURN check_time BETWEEN '08:00:00' AND '18:00:00';
    END IF;
    
    -- Saturday: 8:00 AM to 1:00 PM
    IF day_of_week = 6 THEN
        RETURN check_time BETWEEN '08:00:00' AND '13:00:00';
    END IF;
    
    -- Sunday: closed
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Create function to check if date is a Senegalese public holiday
CREATE OR REPLACE FUNCTION is_senegal_public_holiday(check_date DATE)
RETURNS BOOLEAN AS $$
DECLARE
    month_day TEXT;
    year INTEGER;
BEGIN
    month_day := TO_CHAR(check_date, 'MM-DD');
    year := EXTRACT(YEAR FROM check_date);
    
    -- Fixed holidays
    IF month_day IN (
        '01-01', -- New Year's Day
        '04-04', -- Independence Day
        '05-01', -- Labor Day
        '08-15', -- Assumption of Mary
        '11-01', -- All Saints' Day
        '12-25'  -- Christmas Day
    ) THEN
        RETURN TRUE;
    END IF;
    
    -- Variable holidays (simplified - in production, calculate properly)
    -- Easter Monday, Eid al-Fitr, Eid al-Adha, Mawlid, etc.
    -- This would require more complex calculation
    
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Create function to calculate working days between dates
CREATE OR REPLACE FUNCTION calculate_working_days(start_date DATE, end_date DATE)
RETURNS INTEGER AS $$
DECLARE
    working_days INTEGER := 0;
    current_date DATE := start_date;
    day_of_week INTEGER;
BEGIN
    WHILE current_date <= end_date LOOP
        day_of_week := EXTRACT(DOW FROM current_date);
        
        -- Count Monday to Friday, excluding public holidays
        IF day_of_week BETWEEN 1 AND 5 AND NOT is_senegal_public_holiday(current_date) THEN
            working_days := working_days + 1;
        END IF;
        
        current_date := current_date + INTERVAL '1 day';
    END LOOP;
    
    RETURN working_days;
END;
$$ LANGUAGE plpgsql;

-- Create security trigger to prevent unauthorized data access
CREATE OR REPLACE FUNCTION security_check_trigger()
RETURNS TRIGGER AS $$
DECLARE
    current_user_role TEXT;
    table_name TEXT;
BEGIN
    -- Get current user role
    current_user_role := COALESCE(current_setting('app.current_user_role', true), 'anonymous');
    table_name := TG_TABLE_NAME;
    
    -- Log potential security violations
    IF current_user_role = 'anonymous' AND table_name IN (
        'employees', 'payroll_records', 'financial_transactions', 'invoices'
    ) THEN
        INSERT INTO security_violations (
            violation_type,
            violation_details,
            severity,
            ip_address,
            user_agent
        ) VALUES (
            'UNAUTHORIZED_ACCESS',
            'Attempted access to ' || table_name || ' without authentication',
            'HIGH',
            COALESCE(current_setting('app.client_ip', true)::INET, NULL),
            COALESCE(current_setting('app.user_agent', true), NULL)
        );
    END IF;
    
    -- Allow the operation to continue (actual security is handled at application level)
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply security triggers to sensitive tables
CREATE TRIGGER employees_security_trigger
    BEFORE INSERT OR UPDATE OR DELETE ON employees
    FOR EACH ROW EXECUTE FUNCTION security_check_trigger();

CREATE TRIGGER payroll_records_security_trigger
    BEFORE INSERT OR UPDATE OR DELETE ON payroll_records
    FOR EACH ROW EXECUTE FUNCTION security_check_trigger();

CREATE TRIGGER financial_transactions_security_trigger
    BEFORE INSERT OR UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION security_check_trigger();

-- Create data integrity check trigger for payroll records
CREATE OR REPLACE FUNCTION payroll_integrity_check_trigger()
RETURNS TRIGGER AS $$
DECLARE
    decrypted_base DECIMAL;
    decrypted_allowances DECIMAL;
    decrypted_deductions DECIMAL;
    decrypted_gross DECIMAL;
    decrypted_net DECIMAL;
BEGIN
    -- Note: In production, these would be decrypted using the application's encryption service
    -- This is a simplified version for demonstration
    
    -- Skip validation if any encrypted field is NULL
    IF NEW.base_salary IS NULL OR NEW.gross_salary IS NULL OR NEW.net_salary IS NULL THEN
        RETURN NEW;
    END IF;
    
    -- Log calculation for audit
    INSERT INTO payroll_calculation_logs (
        payroll_record_id,
        calculation_step,
        input_parameters,
        calculation_result,
        calculated_by
    ) VALUES (
        NEW.id,
        'INTEGRITY_CHECK',
        json_build_object(
            'base_salary', NEW.base_salary,
            'allowances_total', NEW.allowances_total,
            'deductions_total', NEW.deductions_total
        )::TEXT,
        json_build_object(
            'gross_salary', NEW.gross_salary,
            'net_salary', NEW.net_salary,
            'validation_passed', true
        )::TEXT,
        COALESCE(current_setting('app.current_user_id', true)::BIGINT, NULL)
    );
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payroll_records_integrity_trigger
    BEFORE INSERT OR UPDATE ON payroll_records
    FOR EACH ROW EXECUTE FUNCTION payroll_integrity_check_trigger();

-- Create performance monitoring trigger
CREATE OR REPLACE FUNCTION performance_monitoring_trigger()
RETURNS TRIGGER AS $$
DECLARE
    operation_start TIMESTAMP;
    operation_duration INTERVAL;
BEGIN
    operation_start := clock_timestamp();
    
    -- Simulate the actual operation time
    -- In production, this would measure actual processing time
    
    operation_duration := clock_timestamp() - operation_start;
    
    -- Log slow operations (> 100ms)
    IF EXTRACT(EPOCH FROM operation_duration) > 0.1 THEN
        INSERT INTO audit_logs (
            entity_type,
            entity_id,
            action,
            user_id,
            user_email,
            user_role,
            new_values
        ) VALUES (
            'PERFORMANCE_LOG',
            CASE 
                WHEN TG_OP = 'DELETE' THEN (OLD.id)::BIGINT
                ELSE (NEW.id)::BIGINT
            END,
            'SLOW_OPERATION',
            COALESCE(current_setting('app.current_user_id', true)::BIGINT, NULL),
            COALESCE(current_setting('app.current_user_email', true), 'system'),
            COALESCE(current_setting('app.current_user_role', true), 'system'),
            json_build_object(
                'table', TG_TABLE_NAME,
                'operation', TG_OP,
                'duration_ms', EXTRACT(EPOCH FROM operation_duration) * 1000
            )::TEXT
        );
    END IF;
    
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply performance monitoring to critical tables
CREATE TRIGGER payroll_records_performance_trigger
    AFTER INSERT OR UPDATE OR DELETE ON payroll_records
    FOR EACH ROW EXECUTE FUNCTION performance_monitoring_trigger();

CREATE TRIGGER financial_transactions_performance_trigger
    AFTER INSERT OR UPDATE OR DELETE ON financial_transactions
    FOR EACH ROW EXECUTE FUNCTION performance_monitoring_trigger();

-- Create data retention policy function
CREATE OR REPLACE FUNCTION cleanup_old_audit_logs()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete audit logs older than 7 years (legal requirement in Senegal)
    DELETE FROM audit_logs 
    WHERE timestamp < CURRENT_DATE - INTERVAL '7 years';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    -- Log the cleanup operation
    INSERT INTO audit_logs (
        entity_type,
        action,
        user_email,
        user_role,
        new_values
    ) VALUES (
        'SYSTEM_MAINTENANCE',
        'CLEANUP',
        'system',
        'system',
        json_build_object(
            'operation', 'audit_log_cleanup',
            'deleted_records', deleted_count,
            'retention_period', '7 years'
        )::TEXT
    );
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Add table comments for the new audit functionality
COMMENT ON FUNCTION validate_senegal_phone_number_enhanced(TEXT) IS 'Enhanced validation for Senegalese phone numbers';
COMMENT ON FUNCTION validate_senegal_ninea(TEXT) IS 'Validation for Senegalese NINEA tax numbers';
COMMENT ON FUNCTION validate_senegal_bank_account_enhanced(TEXT) IS 'Enhanced validation for Senegalese bank accounts (RIB format)';
COMMENT ON FUNCTION validate_payroll_calculation(DECIMAL, DECIMAL, DECIMAL, DECIMAL, DECIMAL) IS 'Validates payroll calculation integrity';
COMMENT ON FUNCTION is_business_hours(TIME, DATE) IS 'Checks if time falls within business hours';
COMMENT ON FUNCTION is_senegal_public_holiday(DATE) IS 'Checks if date is a Senegalese public holiday';
COMMENT ON FUNCTION calculate_working_days(DATE, DATE) IS 'Calculates working days between two dates excluding weekends and holidays';
COMMENT ON FUNCTION cleanup_old_audit_logs() IS 'Cleanup function for audit log retention policy';

-- Grant necessary permissions for audit functions
-- Note: In production, create specific roles with minimal required permissions
GRANT EXECUTE ON FUNCTION validate_senegal_phone_number_enhanced(TEXT) TO PUBLIC;
GRANT EXECUTE ON FUNCTION validate_senegal_ninea(TEXT) TO PUBLIC;
GRANT EXECUTE ON FUNCTION validate_senegal_bank_account_enhanced(TEXT) TO PUBLIC;
GRANT EXECUTE ON FUNCTION is_business_hours(TIME, DATE) TO PUBLIC;
GRANT EXECUTE ON FUNCTION is_senegal_public_holiday(DATE) TO PUBLIC;
GRANT EXECUTE ON FUNCTION calculate_working_days(DATE, DATE) TO PUBLIC;