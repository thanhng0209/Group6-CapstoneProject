package com.uwa.printerfarm.job;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JobRepositoryTest {

    private final JobRepository repository = new JobRepository();

    private Job newJob() {
        return new Job("22345678", "prusa-xl-1", "PLA", new BigDecimal("100"), new BigDecimal("60"));
    }

    @Test
    void savingAJobAssignsAnIdAndMakesItFindable() {
        Job job = newJob();

        repository.save(job);

        assertThat(job.getId()).isNotNull();
        assertThat(repository.findById(job.getId())).contains(job);
        assertThat(repository.findAll()).containsExactly(job);
    }

    @Test
    void savingAnAlreadySavedJobDoesNotReassignItsId() {
        Job job = newJob();
        repository.save(job);
        Long firstId = job.getId();

        repository.save(job);

        assertThat(job.getId()).isEqualTo(firstId);
        assertThat(repository.findAll()).containsExactly(job);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(999L)).isEmpty();
    }
}
