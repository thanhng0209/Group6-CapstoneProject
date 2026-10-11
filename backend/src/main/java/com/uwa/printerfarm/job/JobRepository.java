package com.uwa.printerfarm.job;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Job persistence against the 'jobs' table.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatus(JobStatus status);

    List<Job> findByStatusOrderByQueuedAtAsc(JobStatus status);

    List<Job> findByOwnerUniIdOrderByQueuedAtDesc(String ownerUniId);

    List<Job> findByPrinterId(String printerId);

    /**
     * Loads the job with a pessimistic write lock (SELECT ... FOR UPDATE) so that
     * concurrent status-changing requests against the same job (pause/resume/cancel)
     * are serialised instead of one silently overwriting the other's outcome.
     * Must be called inside a transaction; the lock is released when it ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(Long id);
}
