package com.uwa.printerfarm.enums;

/**
 * User roles for the printer farm system.
 * STUDENT / STAFF can submit and monitor their own jobs.
 * ADMIN (farm manager) can view all printers, jobs, costs, and filament usage.
 */
public enum Role {
    STUDENT,
    STAFF,
    ADMIN
}
