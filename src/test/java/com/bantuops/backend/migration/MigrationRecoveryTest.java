package com.bantuops.backend.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.PayrollRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.service.migration.DataMigrationService;
import com.bantuops.backend.service.migration.RollbackMigrationService;
import com.bantuops.backend.service.migration.ValidationMigrationService;
import com.bantuops.backend.dto.migration.MigrationResult;
import com.bantuops.backend.dto.migration.RollbackResult;
import com.bantuops.backend.dto.migration.MigrationBackup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.persistence.EntityManager;
import javax.p