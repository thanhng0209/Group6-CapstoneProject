package com.uwa.printerfarm.job;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store of jobs admitted into the queue. Placeholder until the team
 * wires up real JPA persistence for the Jobs table (Person 1's schema work);
 * exists so job status/cost/filament data can be listed for the admin
 * monitoring view without waiting on that.
 */
@Repository
public class JobRepository {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Job> jobs = new ConcurrentHashMap<>();

    public Job save(Job job) {
        if (job.getId() == null) {
            job.assignId(sequence.incrementAndGet());
        }
        jobs.put(job.getId(), job);
        return job;
    }

    public Optional<Job> findById(Long id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public List<Job> findAll() {
        return List.copyOf(jobs.values());
    }
}
