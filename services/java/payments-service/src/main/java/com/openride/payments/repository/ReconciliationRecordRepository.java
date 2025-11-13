package com.openride.payments.repository;

import com.openride.payments.model.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for reconciliation record operations.
 */
@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {

    /**
     * Finds reconciliation record by date.
     *
     * @param reconciliationDate reconciliation date
     * @return optional reconciliation record
     */
    Optional<ReconciliationRecord> findByReconciliationDate(LocalDate reconciliationDate);

    /**
     * Finds all reconciliation records ordered by date descending.
     *
     * @return list of reconciliation records
     */
    List<ReconciliationRecord> findAllByOrderByReconciliationDateDesc();

    /**
     * Finds reconciliation records with discrepancies.
     *
     * @return list of records with discrepancies
     */
    List<ReconciliationRecord> findByStatusOrderByReconciliationDateDesc(
        ReconciliationRecord.ReconciliationStatus status
    );
}
