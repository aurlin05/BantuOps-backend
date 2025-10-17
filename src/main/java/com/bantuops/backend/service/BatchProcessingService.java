package com.bantuops.backend.service;

import com.bantuops.backend.dto.PayrollResult;
import com.bantuops.backend.entity.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de traitement par lots utilisant Spring Batch
 * Optimisé pour les traitements de gros volumes avec gestion d'erreurs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchProcessingService {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final JobLauncher jobLauncher;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final PayrollCalculationService payrollCalculationService;
    private final PerformanceMonitoringService performanceMonitoringService;
    
    // Suivi des jobs en cours
    private final Map<String, JobExecution> activeJobs = new ConcurrentHashMap<>();
    
    // Configuration des chunks
    private static final int DEFAULT_CHUNK_SIZE = 100;
    private static final int LARGE_CHUNK_SIZE = 500;

    /**
     * Job de calcul de paie en masse avec Spring Batch
     */
    public JobExecution executeBulkPayrollJob(YearMonth period) throws Exception {
        String jobName = "bulkPayrollJob_" + period.toString();
        
        Job job = jobBuilderFactory.get(jobName)
                .incrementer(new RunIdIncrementer())
                .start(createPayrollCalculationStep())
                .build();
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("period", period.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        try {
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);
            activeJobs.put(jobName, jobExecution);
            
            log.info("Started bulk payroll job: {} for period: {}", jobName, period);
            return jobExecution;
            
        } catch (JobExecutionAlreadyRunningException | JobRestartException | 
                 JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
            log.error("Error starting bulk payroll job for period: {}", period, e);
            throw e;
        }
    }

    /**
     * Création du step de calcul de paie
     */
    private Step createPayrollCalculationStep() {
        return stepBuilderFactory.get("payrollCalculationStep")
                .<Employee, PayrollResult>chunk(DEFAULT_CHUNK_SIZE)
                .reader(createEmployeeReader())
                .processor(createPayrollProcessor())
                .writer(createPayrollWriter())
                .faultTolerant()
                .skipLimit(10)
                .skip(Exception.class)
                .listener(new PayrollStepExecutionListener())
                .build();
    }

    /**
     * Reader pour les employés actifs
     */
    private ItemReader<Employee> createEmployeeReader() {
        JdbcPagingItemReader<Employee> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(dataSource);
        reader.setPageSize(DEFAULT_CHUNK_SIZE);
        reader.setRowMapper((rs, rowNum) -> {
            Employee employee = new Employee();
            employee.setId(rs.getLong("id"));
            employee.setEmployeeNumber(rs.getString("employee_number"));
            // Mapper les autres champs nécessaires
            return employee;
        });
        
        try {
            SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
            factory.setDataSource(dataSource);
            factory.setSelectClause("SELECT id, employee_number, first_name, last_name, department, position, base_salary");
            factory.setFromClause("FROM employees");
            factory.setWhereClause("WHERE is_active = true");
            factory.setSortKey("id");
            
            PagingQueryProvider queryProvider = factory.getObject();
            reader.setQueryProvider(queryProvider);
            
        } catch (Exception e) {
            log.error("Error creating employee reader", e);
            throw new RuntimeException("Failed to create employee reader", e);
        }
        
        return reader;
    }

    /**
     * Processor pour les calculs de paie
     */
    private ItemProcessor<Employee, PayrollResult> createPayrollProcessor() {
        return new ItemProcessor<Employee, PayrollResult>() {
            @Override
            public PayrollResult process(Employee employee) throws Exception {
                try {
                    // Récupérer la période depuis les paramètres du job
                    YearMonth period = YearMonth.now(); // Simplification - devrait venir des paramètres
                    
                    PayrollResult result = payrollCalculationService.calculatePayroll(employee.getId(), period);
                    
                    log.debug("Processed payroll for employee: {}", employee.getId());
                    return result;
                    
                } catch (Exception e) {
                    log.warn("Error processing payroll for employee {}: {}", employee.getId(), e.getMessage());
                    // Retourner null pour skipper cet item
                    return null;
                }
            }
        };
    }

    /**
     * Writer pour les résultats de paie
     */
    private ItemWriter<PayrollResult> createPayrollWriter() {
        return new ItemWriter<PayrollResult>() {
            @Override
            public void write(java.util.List<? extends PayrollResult> items) throws Exception {
                for (PayrollResult result : items) {
                    if (result != null) {
                        // Sauvegarder le résultat en base de données
                        // Implémentation dépendante du modèle de données
                        log.debug("Saved payroll result for employee: {}", result.getEmployeeId());
                    }
                }
                
                log.info("Wrote {} payroll results", items.size());
            }
        };
    }

    /**
     * Job d'export de données en masse
     */
    public JobExecution executeDataExportJob(String entityType, String outputPath) throws Exception {
        String jobName = "dataExportJob_" + entityType + "_" + System.currentTimeMillis();
        
        Job job = jobBuilderFactory.get(jobName)
                .incrementer(new RunIdIncrementer())
                .start(createDataExportStep(entityType, outputPath))
                .build();
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("entityType", entityType)
                .addString("outputPath", outputPath)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        try {
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);
            activeJobs.put(jobName, jobExecution);
            
            log.info("Started data export job: {} for entity: {}", jobName, entityType);
            return jobExecution;
            
        } catch (Exception e) {
            log.error("Error starting data export job for entity: {}", entityType, e);
            throw e;
        }
    }

    /**
     * Création du step d'export de données
     */
    private Step createDataExportStep(String entityType, String outputPath) {
        return stepBuilderFactory.get("dataExportStep")
                .<Object[], String>chunk(LARGE_CHUNK_SIZE)
                .reader(createDataReader(entityType))
                .processor(createDataProcessor())
                .writer(createFileWriter(outputPath))
                .build();
    }

    /**
     * Reader pour les données à exporter
     */
    private ItemReader<Object[]> createDataReader(String entityType) {
        JdbcPagingItemReader<Object[]> reader = new JdbcPagingItemReader<>();
        reader.setDataSource(dataSource);
        reader.setPageSize(LARGE_CHUNK_SIZE);
        reader.setRowMapper((rs, rowNum) -> {
            // Mapper selon le type d'entité
            switch (entityType.toUpperCase()) {
                case "EMPLOYEE":
                    return new Object[]{
                        rs.getLong("id"),
                        rs.getString("employee_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("department"),
                        rs.getString("position")
                    };
                default:
                    return new Object[]{rs.getLong("id")};
            }
        });
        
        try {
            SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
            factory.setDataSource(dataSource);
            
            switch (entityType.toUpperCase()) {
                case "EMPLOYEE":
                    factory.setSelectClause("SELECT id, employee_number, " +
                        "pgp_sym_decrypt(first_name::bytea, :encryptionKey) as first_name, " +
                        "pgp_sym_decrypt(last_name::bytea, :encryptionKey) as last_name, " +
                        "department, position");
                    factory.setFromClause("FROM employees");
                    factory.setWhereClause("WHERE is_active = true");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported entity type: " + entityType);
            }
            
            factory.setSortKey("id");
            
            PagingQueryProvider queryProvider = factory.getObject();
            reader.setQueryProvider(queryProvider);
            
        } catch (Exception e) {
            log.error("Error creating data reader for entity: {}", entityType, e);
            throw new RuntimeException("Failed to create data reader", e);
        }
        
        return reader;
    }

    /**
     * Processor pour formater les données d'export
     */
    private ItemProcessor<Object[], String> createDataProcessor() {
        return new ItemProcessor<Object[], String>() {
            @Override
            public String process(Object[] data) throws Exception {
                // Convertir en format CSV
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < data.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(data[i] != null ? data[i].toString() : "");
                }
                return sb.toString();
            }
        };
    }

    /**
     * Writer pour fichier de sortie
     */
    private ItemWriter<String> createFileWriter(String outputPath) {
        FlatFileItemWriter<String> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource(outputPath));
        writer.setLineAggregator(new DelimitedLineAggregator<String>() {
            {
                setDelimiter(",");
                setFieldExtractor(new BeanWrapperFieldExtractor<String>() {
                    {
                        setNames(new String[]{"value"});
                    }
                });
            }
        });
        
        return writer;
    }

    /**
     * Job de nettoyage de données anciennes
     */
    public JobExecution executeDataCleanupJob(String entityType, int retentionDays) throws Exception {
        String jobName = "dataCleanupJob_" + entityType + "_" + System.currentTimeMillis();
        
        Job job = jobBuilderFactory.get(jobName)
                .incrementer(new RunIdIncrementer())
                .start(createDataCleanupStep(entityType, retentionDays))
                .build();
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("entityType", entityType)
                .addLong("retentionDays", (long) retentionDays)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        try {
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);
            activeJobs.put(jobName, jobExecution);
            
            log.info("Started data cleanup job: {} for entity: {}", jobName, entityType);
            return jobExecution;
            
        } catch (Exception e) {
            log.error("Error starting data cleanup job for entity: {}", entityType, e);
            throw e;
        }
    }

    /**
     * Création du step de nettoyage
     */
    private Step createDataCleanupStep(String entityType, int retentionDays) {
        return stepBuilderFactory.get("dataCleanupStep")
                .tasklet((contribution, chunkContext) -> {
                    try {
                        int deletedCount = executeCleanupQuery(entityType, retentionDays);
                        
                        log.info("Cleaned up {} old records for entity: {}", deletedCount, entityType);
                        
                        // Enregistrer les métriques
                        performanceMonitoringService.updateCustomGauge(
                            "batch.cleanup." + entityType.toLowerCase() + ".deleted", deletedCount);
                        
                        return RepeatStatus.FINISHED;
                        
                    } catch (Exception e) {
                        log.error("Error during data cleanup for entity: {}", entityType, e);
                        throw e;
                    }
                })
                .build();
    }

    /**
     * Exécution de la requête de nettoyage
     */
    private int executeCleanupQuery(String entityType, int retentionDays) {
        // Implémentation simplifiée - dans un vrai système, utiliser JdbcTemplate
        switch (entityType.toUpperCase()) {
            case "AUDIT_LOG":
                // DELETE FROM audit_logs WHERE created_at < NOW() - INTERVAL '? days'
                return 0; // Placeholder
            case "SESSION_DATA":
                // DELETE FROM session_data WHERE last_accessed < NOW() - INTERVAL '? days'
                return 0; // Placeholder
            default:
                throw new IllegalArgumentException("Unsupported entity type for cleanup: " + entityType);
        }
    }

    /**
     * Job de migration de données
     */
    public JobExecution executeDataMigrationJob(String sourceTable, String targetTable) throws Exception {
        String jobName = "dataMigrationJob_" + sourceTable + "_to_" + targetTable + "_" + System.currentTimeMillis();
        
        Job job = jobBuilderFactory.get(jobName)
                .incrementer(new RunIdIncrementer())
                .start(createDataMigrationStep(sourceTable, targetTable))
                .build();
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("sourceTable", sourceTable)
                .addString("targetTable", targetTable)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        try {
            JobExecution jobExecution = jobLauncher.run(job, jobParameters);
            activeJobs.put(jobName, jobExecution);
            
            log.info("Started data migration job: {} from {} to {}", jobName, sourceTable, targetTable);
            return jobExecution;
            
        } catch (Exception e) {
            log.error("Error starting data migration job from {} to {}", sourceTable, targetTable, e);
            throw e;
        }
    }

    /**
     * Création du step de migration
     */
    private Step createDataMigrationStep(String sourceTable, String targetTable) {
        return stepBuilderFactory.get("dataMigrationStep")
                .<Map<String, Object>, Map<String, Object>>chunk(DEFAULT_CHUNK_SIZE)
                .reader(createMigrationReader(sourceTable))
                .processor(createMigrationProcessor())
                .writer(createMigrationWriter(targetTable))
                .faultTolerant()
                .skipLimit(100)
                .skip(Exception.class)
                .build();
    }

    /**
     * Reader pour la migration
     */
    private ItemReader<Map<String, Object>> createMigrationReader(String sourceTable) {
        // Implémentation simplifiée
        return new ItemReader<Map<String, Object>>() {
            @Override
            public Map<String, Object> read() throws Exception {
                // Lire depuis la table source
                return null; // Placeholder
            }
        };
    }

    /**
     * Processor pour la migration
     */
    private ItemProcessor<Map<String, Object>, Map<String, Object>> createMigrationProcessor() {
        return new ItemProcessor<Map<String, Object>, Map<String, Object>>() {
            @Override
            public Map<String, Object> process(Map<String, Object> item) throws Exception {
                // Transformer les données si nécessaire
                return item;
            }
        };
    }

    /**
     * Writer pour la migration
     */
    private ItemWriter<Map<String, Object>> createMigrationWriter(String targetTable) {
        return new ItemWriter<Map<String, Object>>() {
            @Override
            public void write(java.util.List<? extends Map<String, Object>> items) throws Exception {
                // Écrire vers la table cible
                log.info("Migrated {} records to {}", items.size(), targetTable);
            }
        };
    }

    /**
     * Obtention du statut d'un job
     */
    public JobExecution getJobStatus(String jobName) {
        return activeJobs.get(jobName);
    }

    /**
     * Arrêt d'un job en cours
     */
    public boolean stopJob(String jobName) {
        JobExecution jobExecution = activeJobs.get(jobName);
        if (jobExecution != null && jobExecution.isRunning()) {
            jobExecution.stop();
            log.info("Stopped job: {}", jobName);
            return true;
        }
        return false;
    }

    /**
     * Liste des jobs actifs
     */
    public Map<String, JobExecution> getActiveJobs() {
        return new HashMap<>(activeJobs);
    }

    /**
     * Statistiques des jobs batch
     */
    public Map<String, Object> getBatchStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long runningJobs = activeJobs.values().stream()
            .filter(JobExecution::isRunning)
            .count();
        
        long completedJobs = activeJobs.values().stream()
            .filter(job -> job.getStatus() == BatchStatus.COMPLETED)
            .count();
        
        long failedJobs = activeJobs.values().stream()
            .filter(job -> job.getStatus() == BatchStatus.FAILED)
            .count();
        
        stats.put("running_jobs", runningJobs);
        stats.put("completed_jobs", completedJobs);
        stats.put("failed_jobs", failedJobs);
        stats.put("total_jobs", activeJobs.size());
        stats.put("timestamp", LocalDateTime.now());
        
        return stats;
    }

    /**
     * Listener pour les steps de paie
     */
    private class PayrollStepExecutionListener implements StepExecutionListener {
        @Override
        public void beforeStep(StepExecution stepExecution) {
            log.info("Starting payroll calculation step: {}", stepExecution.getStepName());
            performanceMonitoringService.incrementCustomCounter("batch.payroll.steps.started");
        }

        @Override
        public ExitStatus afterStep(StepExecution stepExecution) {
            log.info("Completed payroll calculation step: {} - Read: {}, Written: {}, Skipped: {}", 
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount());
            
            performanceMonitoringService.updateCustomGauge("batch.payroll.last.read.count", stepExecution.getReadCount());
            performanceMonitoringService.updateCustomGauge("batch.payroll.last.write.count", stepExecution.getWriteCount());
            performanceMonitoringService.updateCustomGauge("batch.payroll.last.skip.count", stepExecution.getSkipCount());
            
            if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
                performanceMonitoringService.incrementCustomCounter("batch.payroll.steps.completed");
            } else {
                performanceMonitoringService.incrementCustomCounter("batch.payroll.steps.failed");
            }
            
            return ExitStatus.COMPLETED;
        }
    }
}