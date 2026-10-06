package com.uwa.printerfarm.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for Transaction persistence against the 'transactions' table.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByOwnerUniIdOrderByOccurredAtDesc(String ownerUniId);

    Page<Transaction> findByOwnerUniId(String ownerUniId, Pageable pageable);

    List<Transaction> findAllByOrderByOccurredAtDesc();
}
