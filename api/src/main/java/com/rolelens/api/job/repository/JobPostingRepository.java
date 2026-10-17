package com.rolelens.api.job.repository;

import com.rolelens.api.job.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {
}
