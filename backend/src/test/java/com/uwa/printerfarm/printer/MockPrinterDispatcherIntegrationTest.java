package com.uwa.printerfarm.printer;

import com.uwa.printerfarm.enums.Role;
import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobLifecycleService;
import com.uwa.printerfarm.job.JobRepository;
import com.uwa.printerfarm.job.JobStatus;
import com.uwa.printerfarm.model.User;
import com.uwa.printerfarm.notification.JobNotificationService;
import com.uwa.printerfarm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest
class MockPrinterDispatcherIntegrationTest {

    @Autowired
    private PrinterRepository printerRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;

    private MailSender mailSender;
    private JobNotificationService notificationService;
    private JobLifecycleService lifecycleService;
    private MockPrinterDispatcher dispatcher;
    private Instant now;

    @BeforeEach
    void setUp() {
        mailSender = mock(MailSender.class);
        notificationService = new JobNotificationService(
                mailSender,
                userRepository,
                "printerfarm-noreply@uwa.edu.au",
                "@student.uwa.edu.au"
        );
        lifecycleService = new JobLifecycleService(5);

        now = Instant.parse("2026-09-19T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));

        dispatcher = new MockPrinterDispatcher(
                printerRepository,
                jobRepository,
                lifecycleService,
                notificationService,
                60L,
                clock
        );

        // Seed test user
        User user = User.builder()
                .uniId("22998877")
                .email("real.student@uwa.edu.au")
                .fullName("Real Student")
                .passwordHash("secret")
                .role(Role.STUDENT)
                .balanceCents(10000L)
                .build();
        userRepository.save(user);

        // Ensure seeded PRUSA_XL_1 is IDLE and loaded with PLA
        Printer printer = printerRepository.findById("PRUSA_XL_1").orElse(null);
        if (printer == null) {
            printer = new Printer("PRUSA_XL_1", "Prusa XL #1", "PRUSA_XL", "IDLE", "PLA", "Orange");
            printerRepository.save(printer);
        } else {
            printer.setStatus("IDLE");
            printer.setCurrentMaterial("PLA");
            printerRepository.save(printer);
        }
    }

    @Test
    void fullLifecycleDispatchesQueuedJobSimulatesTimeCompletesAndEmailsActualDatabaseEmail() {
        // 1. Submit queued job
        Job job = new Job("22998877", "PRUSA_XL_1", "model.gcode", "PLA", new BigDecimal("40.0"), new BigDecimal("90.0"));
        job.setCost(new BigDecimal("4.00"));
        job = jobRepository.save(job);

        // 2. Run dispatcher tick -> should assign job to PRUSA_XL_1 and change status to PRINTING
        dispatcher.dispatchAndSimulate();

        Job printingJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(printingJob.getStatus()).isEqualTo(JobStatus.PRINTING);
        assertThat(printingJob.getPrinterId()).isEqualTo("PRUSA_XL_1");

        Printer runningPrinter = printerRepository.findById("PRUSA_XL_1").orElseThrow();
        assertThat(runningPrinter.getStatus()).isEqualTo("PRINTING");

        // 3. Fast-forward clock by 65 seconds
        Instant future = now.plusSeconds(65);
        Clock futureClock = Clock.fixed(future, ZoneId.of("UTC"));
        MockPrinterDispatcher futureDispatcher = new MockPrinterDispatcher(
                printerRepository,
                jobRepository,
                lifecycleService,
                notificationService,
                60L,
                futureClock
        );
        futureDispatcher.getPrintingStartedAt().put(printingJob.getId(), now);

        // 4. Run dispatcher simulation again -> should complete job and notify user
        futureDispatcher.dispatchAndSimulate();

        Job completedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completedJob.getCompletedAt()).isNotNull();

        Printer idlePrinter = printerRepository.findById("PRUSA_XL_1").orElseThrow();
        assertThat(idlePrinter.getStatus()).isEqualTo("IDLE");

        // Verify email notification sent to the actual email in DB
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage captured = messageCaptor.getValue();
        assertThat(captured.getTo()).containsExactly("real.student@uwa.edu.au");
        assertThat(captured.getSubject()).contains("ready for collection");
    }
}
