package com.uwa.printerfarm.repository;

import com.uwa.printerfarm.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUniId(String uniId);
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUniId(String uniId);

    boolean existsByEmail(String email);
}
