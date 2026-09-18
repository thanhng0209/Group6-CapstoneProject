package com.uwa.printerfarm.job;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.printer.Printer;
import com.uwa.printerfarm.printer.PrinterRepository;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrinterRepository printerRepository;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUniId("22345678").isEmpty()) {
            User user = User.builder()
                    .uniId("22345678")
                    .email("22345678@student.uwa.edu.au")
                    .fullName("Test Student")
                    .passwordHash("hashed")
                    .role(Role.STUDENT)
                    .balanceCents(5000L)
                    .build();
            userRepository.save(user);
        }

        if (printerRepository.findById("prusa-xl-1").isEmpty()) {
            Printer printer = new Printer("prusa-xl-1", "Prusa XL #1", "PRUSA_XL", "IDLE", "PLA", "Orange");
            printerRepository.save(printer);
        }
    }

    private Job newJob() {
        return new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("100.00"), new BigDecimal("60.00"));
    }

    @Test
    void savingAJobAssignsAnIdAndMakesItFindable() {
        Job job = newJob();

        jobRepository.save(job);

        assertThat(job.getId()).isNotNull();
        assertThat(jobRepository.findById(job.getId())).contains(job);
        assertThat(jobRepository.findAll()).contains(job);
    }

    @Test
    void savingAnAlreadySavedJobDoesNotReassignItsId() {
        Job job = newJob();
        jobRepository.save(job);
        Long firstId = job.getId();

        jobRepository.save(job);

        assertThat(job.getId()).isEqualTo(firstId);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(jobRepository.findById(999L)).isEmpty();
    }
}
