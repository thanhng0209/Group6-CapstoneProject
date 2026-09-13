package com.uwa.printerfarm.notification;

import com.uwa.printerfarm.job.Job;
import com.uwa.printerfarm.job.JobStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

/**
 * Emails a student when their job reaches a terminal state (MVP acceptance
 * criteria: notify the student via their registered UWA email once a job
 * finishes). Whoever drives the job status forward - currently mock callers,
 * later the Prusa status poller (Workstream 4) - calls this once the job is
 * COMPLETED or FAILED.
 */
@Service
public class JobNotificationService {

    private final MailSender mailSender;
    private final String fromAddress;
    private final String emailDomain;

    public JobNotificationService(MailSender mailSender,
                                   @Value("${printerfarm.notification.from-address:printerfarm-noreply@uwa.edu.au}")
                                   String fromAddress,
                                   @Value("${printerfarm.notification.email-domain:@student.uwa.edu.au}")
                                   String emailDomain) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.emailDomain = emailDomain;
    }

    /**
     * Sends the completion/failure notification for a job already in a
     * terminal state. Throws if the job hasn't actually finished yet, so
     * callers don't accidentally notify students about an in-progress job.
     */
    public void notifyJobFinished(Job job) {
        JobStatus status = job.getStatus();
        if (status != JobStatus.COMPLETED && status != JobStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Cannot send a finished-job notification for job in status " + status);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(resolveEmail(job.getOwnerUniId()));
        message.setSubject(subjectFor(job));
        message.setText(bodyFor(job));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new NotificationDeliveryException(job.getOwnerUniId(), e);
        }
    }

    /**
     * Placeholder UNI-ID-to-email mapping until the auth/user module exposes
     * each student's actual registered UWA email address.
     */
    String resolveEmail(String ownerUniId) {
        return ownerUniId + emailDomain;
    }

    private String subjectFor(Job job) {
        return job.getStatus() == JobStatus.COMPLETED
                ? "Your print job on " + job.getPrinterId() + " is ready for collection"
                : "Your print job on " + job.getPrinterId() + " has failed";
    }

    private String bodyFor(Job job) {
        if (job.getStatus() == JobStatus.COMPLETED) {
            return "Hi,\n\n"
                    + "Your print job #" + job.getId() + " on printer " + job.getPrinterId()
                    + " (material: " + job.getMaterial() + ") has finished and is awaiting collection.\n"
                    + "Cost charged: " + job.getCost() + "\n\n"
                    + "- UWA Printer Farm";
        }
        return "Hi,\n\n"
                + "Your print job #" + job.getId() + " on printer " + job.getPrinterId()
                + " (material: " + job.getMaterial() + ") has failed during printing.\n"
                + "Please check the job details or contact a farm manager about a refund.\n\n"
                + "- UWA Printer Farm";
    }
}
