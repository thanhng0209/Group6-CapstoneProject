package com.uwa.printerfarm.job;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for RefundRequest persistence against the 'refund_requests' table.
 */
@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(Long id);

    List<RefundRequest> findByStatus(RefundStatus status);

    List<RefundRequest> findByOwnerUniId(String ownerUniId);
}
