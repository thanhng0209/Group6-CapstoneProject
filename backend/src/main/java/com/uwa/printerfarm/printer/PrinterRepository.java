package com.uwa.printerfarm.printer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for Printer inventory against the 'printers' table.
 */
@Repository
public interface PrinterRepository extends JpaRepository<Printer, String> {

    List<Printer> findByStatus(String status);

    List<Printer> findByModel(String model);
}
