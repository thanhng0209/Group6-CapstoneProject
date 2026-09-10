package com.uwa.printerfarm.job;

public class InvalidJobStatusTransitionException extends RuntimeException {

    public InvalidJobStatusTransitionException(JobStatus from, JobStatus to) {
        super("Cannot transition job from " + from + " to " + to);
    }
}
