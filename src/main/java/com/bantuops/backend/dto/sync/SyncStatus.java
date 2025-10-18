package com.bantuops.backend.dto.sync;

/**
 * Énumération des statuts de synchronisation
 */
public enum SyncStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    PARTIAL_SUCCESS,
    CONFLICT_DETECTED,
    CANCELLED
}