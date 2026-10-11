package com.uwa.printerfarm.job;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.notification.JobNotificationService;
import com.uwa.printerfarm.printer.MockPrinterDispatcher;
import com.uwa.printerfarm.printer.Printer;
import com.uwa.printerfarm.printer.PrinterRepository;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that concurrent pause requests against the same job (e.g. a flaky double-click)
 * are serialised by the row lock instead of both passing the status check before either
 * writes. JobController is instantiated directly (not obtained from the Spring container),
 * so a TransactionTemplate bound to the real PlatformTransactionManager stands in for the
 * @Transactional AOP proxy that wraps pause()/resume() in production.
 * The test itself must not run inside a transaction: each worker thread needs its own
 * committed transaction, otherwise the row lock would never be contended.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobControlConcurrencyIntegrationTest {

    private static final String TEST_UNI_ID = "55667788";
    private static final String TEST_PRINTER_ID = "prusa-xl-1";
    private static final int THREADS = 8;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PrinterRepository printerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private JobController jobController;
    private Long jobId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        JobLifecycleService jobLifecycleService = new JobLifecycleService(5);
        JobNotificationService jobNotificationService =
                new JobNotificationService(mock(MailSender.class), "noreply@example.com", "@student.uwa.edu.au");
        MockPrinterDispatcher dispatcher = new MockPrinterDispatcher(
                printerRepository, jobRepository, jobLifecycleService, jobNotificationService, 60L, Clock.systemUTC());

        @SuppressWarnings("unchecked")
        ObjectProvider<MockPrinterDispatcher> dispatcherProvider = mock(ObjectProvider.class);
        when(dispatcherProvider.getIfAvailable()).thenReturn(dispatcher);

        jobController = new JobController(null, jobLifecycleService, jobRepository, printerRepository,
                dispatcherProvider, null);

        jobId = transactionTemplate.execute(status -> {
            userRepository.save(User.builder()
                    .uniId(TEST_UNI_ID)
                    .email(TEST_UNI_ID + "@student.uwa.edu.au")
                    .fullName("Concurrency Test Student")
                    .passwordHash("hashed")
                    .role(Role.STUDENT)
                    .balanceCents(10_000L)
                    .build());
            printerRepository.save(new Printer(TEST_PRINTER_ID, "Prusa XL Test", "PRUSA_XL", "PRINTING", "PLA", "Orange"));

            Job job = new Job(TEST_UNI_ID, TEST_PRINTER_ID, "cube.gcode", "PLA",
                    new BigDecimal("50.00"), new BigDecimal("120.00"));
            job.setStatus(JobStatus.PRINTING);
            return jobRepository.save(job).getId();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.execute(status -> {
            jobRepository.findById(jobId).ifPresent(jobRepository::delete);
            printerRepository.findById(TEST_PRINTER_ID).ifPresent(printerRepository::delete);
            userRepository.findByUniId(TEST_UNI_ID).ifPresent(userRepository::delete);
            return null;
        });
    }

    @Test
    void concurrentPauseRequestsOnlyOneSucceeds() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(TEST_UNI_ID, null, List.of());

        List<Callable<ResponseEntity<?>>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> transactionTemplate.execute(status -> jobController.pause(jobId, null, auth)));
        }

        List<Future<ResponseEntity<?>>> results = runConcurrently(tasks);

        long succeeded = 0;
        long rejected = 0;
        for (Future<ResponseEntity<?>> future : results) {
            ResponseEntity<?> response = future.get();
            if (response.getStatusCode().is2xxSuccessful()) {
                succeeded++;
            } else {
                rejected++;
            }
        }

        // With the row lock, exactly one pause can win the race. Without it, more than one
        // request could read PRINTING and pass the status check before either wrote back -
        // the exact bug this test guards against.
        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(THREADS - 1);
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.PAUSED);
    }

    private <T> List<Future<T>> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            for (Future<T> future : futures) {
                future.get();
            }
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }
}
