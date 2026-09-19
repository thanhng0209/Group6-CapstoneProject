package com.uwa.printerfarm.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for Job persistence against the 'jobs' table.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatus(JobStatus status);

    List<Job> findByStatusOrderByQueuedAtAsc(JobStatus status);

    List<Job> findByOwnerUniIdOrderByQueuedAtDesc(String ownerUniId);

    List<Job> findByPrinterId(String printerId);
}
