package com.bantuops.backend.service.sync;

import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service de synchronisation entre le frontend et le backend.
 * Gère la synchronisation bidirectionnelle des données critiques.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FrontendBackendSyncService {

    private final EmployeeRepository employeeRepository;
    private final InvoiceRepository invoiceRepository;
    // TODO: Add PayrollRecordRepository when it's created
    // private final PayrollRecordRepository payrollRecordRepository;

    // TODO: Implement sync methods
}
