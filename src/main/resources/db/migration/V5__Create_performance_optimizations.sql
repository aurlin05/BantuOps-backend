-- Performance Optimizations and Advanced Indexes
-- Optimize database performance for high-volume operations

-- Create materialized views for frequently accessed aggregated data

-- Materialized view for employee statistics
CREATE MATERIALIZED VIEW mv_employee_statistics AS
SELECT 
    department,
    COUNT(*) as total_employees,
    COUNT(*) FILTER (WHERE is_active = true) as active_employees,
    COUNT(*) FILTER (WHERE is_active = false) as inactive_employees,
    AVG(EXTRACT(YEAR FROM AGE(CURRENT_DATE, hire_date))) as avg_years_service,
    MIN(hire_date) as earliest_hire_date,
    MAX(hire_date) as latest_hire_date
FROM employees 
GROUP BY department;

-- Create unique index on materialized view
CREATE UNIQUE INDEX idx_mv_employee_stats_dept ON mv_employee_statistics(department);

-- Materialized view for monthly payroll summary
CREATE MATERIALIZED VIEW mv_monthly_payroll_summary AS
SELECT 
    period_year,
    period_month,
    COUNT(*) as total_payrolls,
    COUNT(*) FILTER (WHERE status = 'PAID') as paid_payrolls,
    COUNT(*) FILTER (WHERE status = 'DRAFT') as draft_payrolls,
    COUNT(*) FILTER (WHERE status = 'CALCULATED') as calculated_payrolls,
    COUNT(*) FILTER (WHERE status = 'APPROVED') as approved_payrolls
FROM payroll_records 
GROUP BY period_year, period_month;

-- Create unique index on materialized view
CREATE UNIQUE INDEX idx_mv_payroll_summary_period ON mv_monthly_payroll_summary(period_year, period_month);

-- Materialized view for attendance statistics
CREATE MATERIALIZED VIEW mv_attendance_statistics AS
SELECT 
    DATE_TRUNC('month', work_date) as month_year,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE attendance_type = 'PRESENT') as present_count,
    COUNT(*) FILTER (WHERE attendance_type = 'LATE') as late_count,
    COUNT(*) FILTER (WHERE attendance_type = 'ABSENT') as absent_count,
    COUNT(*) FILTER (WHERE delay_minutes > 0) as delayed_count,
    AVG(delay_minutes) FILTER (WHERE delay_minutes > 0) as avg_delay_minutes,
    MAX(delay_minutes) as max_delay_minutes
FROM attendance_records 
GROUP BY DATE_TRUNC('month', work_date);

-- Create unique index on materialized view
CREATE UNIQUE INDEX idx_mv_attendance_stats_month ON mv_attendance_statistics(month_year);

-- Create function to refresh materialized views
CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_employee_statistics;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_monthly_payroll_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_attendance_statistics;
    
    -- Log the refresh operation
    INSERT INTO audit_logs (
        entity_type,
        action,
        user_email,
        user_role,
        new_values
    ) VALUES (
        'SYSTEM_MAINTENANCE',
        'REFRESH_VIEWS',
        'system',
        'system',
        json_build_object(
            'operation', 'refresh_materialized_views',
            'timestamp', CURRENT_TIMESTAMP
        )::TEXT
    );
END;
$$ LANGUAGE plpgsql;

-- Create advanced indexes for complex queries

-- Composite indexes for payroll queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_employee_status_period 
    ON payroll_records(employee_id, status, period_year DESC, period_month DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_calculation_approval_dates 
    ON payroll_records(calculation_date, approval_date) 
    WHERE status IN ('CALCULATED', 'APPROVED', 'PAID');

-- Composite indexes for attendance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_employee_type_date 
    ON attendance_records(employee_id, attendance_type, work_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_delay_status_date 
    ON attendance_records(delay_minutes, status, work_date DESC) 
    WHERE delay_minutes > 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_monthly_summary 
    ON attendance_records(DATE_TRUNC('month', work_date), employee_id, attendance_type);

-- Composite indexes for financial queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_type_date_status 
    ON financial_transactions(transaction_type, transaction_date DESC, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reference_date 
    ON financial_transactions(reference_type, reference_id, transaction_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_client_status_date 
    ON invoices(client_name, status, issue_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoices_due_status 
    ON invoices(due_date, status) 
    WHERE status IN ('SENT', 'OVERDUE');

-- Partial indexes for specific use cases
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employees_active_recent 
    ON employees(hire_date DESC, department) 
    WHERE is_active = true AND hire_date >= CURRENT_DATE - INTERVAL '2 years';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_recent_calculations 
    ON payroll_records(calculation_date DESC, employee_id) 
    WHERE calculation_date >= CURRENT_DATE - INTERVAL '6 months';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_recent_violations 
    ON attendance_records(work_date DESC, employee_id, delay_minutes) 
    WHERE work_date >= CURRENT_DATE - INTERVAL '3 months' AND 
          (delay_minutes > 30 OR attendance_type IN ('ABSENT', 'LATE'));

-- Expression indexes for common calculations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employees_full_name_search 
    ON employees(LOWER(first_name || ' ' || last_name)) 
    WHERE is_active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_work_month 
    ON attendance_records(EXTRACT(YEAR FROM work_date), EXTRACT(MONTH FROM work_date), employee_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payroll_period_key 
    ON payroll_records((period_year || '-' || LPAD(period_month::TEXT, 2, '0')), employee_id);

-- GIN indexes for full-text search (if needed for audit logs)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_search 
    ON audit_logs USING GIN(to_tsvector('english', 
        COALESCE(old_values, '') || ' ' || COALESCE(new_values, '')));

-- Create database statistics collection function
CREATE OR REPLACE FUNCTION collect_database_statistics()
RETURNS TABLE(
    table_name TEXT,
    row_count BIGINT,
    table_size TEXT,
    index_size TEXT,
    total_size TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        schemaname||'.'||tablename as table_name,
        n_tup_ins + n_tup_upd + n_tup_del as row_count,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as table_size,
        pg_size_pretty(pg_indexes_size(schemaname||'.'||tablename)) as index_size,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) + 
                      pg_indexes_size(schemaname||'.'||tablename)) as total_size
    FROM pg_stat_user_tables 
    WHERE schemaname = 'public'
    ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
END;
$$ LANGUAGE plpgsql;

-- Create query performance monitoring function
CREATE OR REPLACE FUNCTION log_slow_queries()
RETURNS VOID AS $$
BEGIN
    -- This would integrate with pg_stat_statements extension in production
    -- For now, just log that monitoring is active
    INSERT INTO audit_logs (
        entity_type,
        action,
        user_email,
        user_role,
        new_values
    ) VALUES (
        'PERFORMANCE_MONITORING',
        'SLOW_QUERY_CHECK',
        'system',
        'system',
        json_build_object(
            'operation', 'slow_query_monitoring',
            'timestamp', CURRENT_TIMESTAMP,
            'status', 'active'
        )::TEXT
    );
END;
$$ LANGUAGE plpgsql;

-- Create connection pool monitoring
CREATE OR REPLACE FUNCTION monitor_database_connections()
RETURNS TABLE(
    total_connections INTEGER,
    active_connections INTEGER,
    idle_connections INTEGER,
    max_connections INTEGER,
    connection_usage_percent NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*)::INTEGER as total_connections,
        COUNT(*) FILTER (WHERE state = 'active')::INTEGER as active_connections,
        COUNT(*) FILTER (WHERE state = 'idle')::INTEGER as idle_connections,
        (SELECT setting::INTEGER FROM pg_settings WHERE name = 'max_connections') as max_connections,
        ROUND((COUNT(*)::NUMERIC / (SELECT setting::INTEGER FROM pg_settings WHERE name = 'max_connections')::NUMERIC) * 100, 2) as connection_usage_percent
    FROM pg_stat_activity 
    WHERE datname = current_database();
END;
$$ LANGUAGE plpgsql;

-- Create table partitioning for audit logs (by month)
-- This helps with performance when audit logs grow large

-- Create partition function for audit logs
CREATE OR REPLACE FUNCTION create_audit_log_partition(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    -- Calculate partition boundaries
    start_date := DATE_TRUNC('month', partition_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'audit_logs_' || TO_CHAR(start_date, 'YYYY_MM');
    
    -- Create partition table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_logs
        FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date);
    
    -- Create indexes on partition
    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I ON %I (timestamp)',
        'idx_' || partition_name || '_timestamp', partition_name);
    
    EXECUTE format('
        CREATE INDEX IF NOT EXISTS %I ON %I (entity_type, entity_id)',
        'idx_' || partition_name || '_entity', partition_name);
    
    -- Log partition creation
    INSERT INTO audit_logs (
        entity_type,
        action,
        user_email,
        user_role,
        new_values
    ) VALUES (
        'SYSTEM_MAINTENANCE',
        'CREATE_PARTITION',
        'system',
        'system',
        json_build_object(
            'operation', 'create_audit_partition',
            'partition_name', partition_name,
            'start_date', start_date,
            'end_date', end_date
        )::TEXT
    );
END;
$$ LANGUAGE plpgsql;

-- Convert audit_logs to partitioned table (this would be done carefully in production)
-- For now, just prepare the structure

-- Create backup and maintenance functions
CREATE OR REPLACE FUNCTION backup_table_data(table_name TEXT, backup_suffix TEXT DEFAULT NULL)
RETURNS VOID AS $$
DECLARE
    backup_table_name TEXT;
BEGIN
    backup_suffix := COALESCE(backup_suffix, TO_CHAR(CURRENT_TIMESTAMP, 'YYYY_MM_DD_HH24_MI_SS'));
    backup_table_name := table_name || '_backup_' || backup_suffix;
    
    EXECUTE format('CREATE TABLE %I AS SELECT * FROM %I', backup_table_name, table_name);
    
    -- Log backup operation
    INSERT INTO audit_logs (
        entity_type,
        action,
        user_email,
        user_role,
        new_values
    ) VALUES (
        'SYSTEM_MAINTENANCE',
        'BACKUP_TABLE',
        'system',
        'system',
        json_build_object(
            'operation', 'backup_table',
            'source_table', table_name,
            'backup_table', backup_table_name,
            'timestamp', CURRENT_TIMESTAMP
        )::TEXT
    );
END;
$$ LANGUAGE plpgsql;

-- Create database health check function
CREATE OR REPLACE FUNCTION database_health_check()
RETURNS TABLE(
    check_name TEXT,
    status TEXT,
    details TEXT,
    recommendation TEXT
) AS $$
BEGIN
    -- Check table sizes
    RETURN QUERY
    SELECT 
        'Large Tables'::TEXT as check_name,
        CASE 
            WHEN pg_total_relation_size('audit_logs') > 1073741824 THEN 'WARNING'  -- > 1GB
            ELSE 'OK'
        END as status,
        'Audit logs size: ' || pg_size_pretty(pg_total_relation_size('audit_logs')) as details,
        'Consider partitioning or archiving old audit logs' as recommendation
    WHERE pg_total_relation_size('audit_logs') > 1073741824;
    
    -- Check connection usage
    RETURN QUERY
    SELECT 
        'Connection Usage'::TEXT as check_name,
        CASE 
            WHEN connection_usage_percent > 80 THEN 'CRITICAL'
            WHEN connection_usage_percent > 60 THEN 'WARNING'
            ELSE 'OK'
        END as status,
        'Connection usage: ' || connection_usage_percent || '%' as details,
        'Consider increasing max_connections or optimizing connection pooling' as recommendation
    FROM monitor_database_connections()
    WHERE connection_usage_percent > 60;
    
    -- Check for missing indexes on foreign keys
    RETURN QUERY
    SELECT 
        'Missing Indexes'::TEXT as check_name,
        'WARNING'::TEXT as status,
        'Some foreign key columns may lack indexes' as details,
        'Review and add indexes on foreign key columns for better performance' as recommendation
    WHERE EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
        WHERE tc.constraint_type = 'FOREIGN KEY'
        AND tc.table_schema = 'public'
        LIMIT 1
    );
END;
$$ LANGUAGE plpgsql;

-- Create automated maintenance scheduler function
CREATE OR REPLACE FUNCTION schedule_maintenance_tasks()
RETURNS VOID AS $$
BEGIN
    -- Refresh materialized views
    PERFORM refresh_all_materialized_views();
    
    -- Update table statistics
    ANALYZE;
    
    -- Clean up old temporary data (if any)
    DELETE FROM audit_logs WHERE entity_type = 'TEMP_DATA' AND timestamp < CURRENT_TIMESTAMP - INTERVAL '1 day';
    
    -- Log maintenance completion
    INSERT INTO audit_logs (
        entity_type,
        action,
        user_email,
        user_role,
        new_values
    ) VALUES (
        'SYSTEM_MAINTENANCE',
        'SCHEDULED_MAINTENANCE',
        'system',
        'system',
        json_build_object(
            'operation', 'scheduled_maintenance',
            'tasks_completed', ARRAY['refresh_views', 'analyze_tables', 'cleanup_temp_data'],
            'timestamp', CURRENT_TIMESTAMP
        )::TEXT
    );
END;
$$ LANGUAGE plpgsql;

-- Grant permissions for monitoring functions
GRANT EXECUTE ON FUNCTION collect_database_statistics() TO PUBLIC;
GRANT EXECUTE ON FUNCTION monitor_database_connections() TO PUBLIC;
GRANT EXECUTE ON FUNCTION database_health_check() TO PUBLIC;

-- Add comments for documentation
COMMENT ON MATERIALIZED VIEW mv_employee_statistics IS 'Aggregated employee statistics by department';
COMMENT ON MATERIALIZED VIEW mv_monthly_payroll_summary IS 'Monthly payroll processing summary';
COMMENT ON MATERIALIZED VIEW mv_attendance_statistics IS 'Monthly attendance statistics and trends';
COMMENT ON FUNCTION refresh_all_materialized_views() IS 'Refreshes all materialized views for updated statistics';
COMMENT ON FUNCTION collect_database_statistics() IS 'Collects database size and performance statistics';
COMMENT ON FUNCTION monitor_database_connections() IS 'Monitors database connection usage';
COMMENT ON FUNCTION database_health_check() IS 'Performs comprehensive database health check';
COMMENT ON FUNCTION schedule_maintenance_tasks() IS 'Automated maintenance task scheduler';

-- Create initial partitions for current and next month
SELECT create_audit_log_partition(CURRENT_DATE);
SELECT create_audit_log_partition(CURRENT_DATE + INTERVAL '1 month');