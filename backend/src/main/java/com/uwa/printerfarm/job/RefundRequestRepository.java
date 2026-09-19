package com.uwa.printerfarm.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for RefundRequest persistence against the 'refund_requests' table.
 */
@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findByStatus(RefundStatus status);

    List<RefundRequest> findByOwnerUniId(String ownerUniId);
}
