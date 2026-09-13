package com.uwa.printerfarm.notification;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobLifecycleService;
import com.uwa.printerfarm.job.JobStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobNotificationServiceTest {

    private final MailSender mailSender = mock(MailSender.class);
    private final JobNotificationService notificationService =
            new JobNotificationService(mailSender, "printerfarm-noreply@uwa.edu.au", "@student.uwa.edu.au");
    private final JobLifecycleService jobLifecycleService = new JobLifecycleService(5);

    private Job newPrintingJob() {
        Job job = new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("50"), new BigDecimal("120"));
        job.setCost(new BigDecimal("6.20"));
        jobLifecycleService.transition(job, JobStatus.PRINTING);
        return job;
    }

    @Test
    void completedJobEmailsStudentAtResolvedUwaAddress() {
        Job job = newPrintingJob();
        jobLifecycleService.transition(job, JobStatus.COMPLETED);

        notificationService.notifyJobFinished(job);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("22345678@student.uwa.edu.au");
        assertThat(sent.getFrom()).isEqualTo("printerfarm-noreply@uwa.edu.au");
        assertThat(sent.getSubject()).contains("ready for collection");
        assertThat(sent.getText()).contains("prusa-xl-1").contains("6.20");
    }

    @Test
    void failedJobEmailsStudentWithFailureNotice() {
        Job job = newPrintingJob();
        jobLifecycleService.transition(job, JobStatus.FAILED);

        notificationService.notifyJobFinished(job);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("has failed");
    }

    @Test
    void nonTerminalJobCannotBeNotified() {
        Job job = newPrintingJob();

        assertThatThrownBy(() -> notificationService.notifyJobFinished(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mailSendFailureIsWrappedInNotificationDeliveryException() {
        Job job = newPrintingJob();
        jobLifecycleService.transition(job, JobStatus.COMPLETED);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notificationService.notifyJobFinished(job))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasCauseInstanceOf(MailSendException.class);
    }
}
