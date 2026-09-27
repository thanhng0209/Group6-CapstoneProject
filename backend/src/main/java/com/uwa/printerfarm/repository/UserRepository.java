package com.uwa.printerfarm.repository;

import com.uwa.printerfarm.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUniId(String uniId);

    /**
     * Loads the user with a pessimistic write lock (SELECT ... FOR UPDATE) so concurrent
     * balance changes for the same user are serialised instead of overwriting each other.
     * Must be called inside a transaction; the lock is released when it ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.uniId = :uniId")
    Optional<User> findByUniIdForUpdate(String uniId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUniId(String uniId);

    boolean existsByEmail(String email);
}
