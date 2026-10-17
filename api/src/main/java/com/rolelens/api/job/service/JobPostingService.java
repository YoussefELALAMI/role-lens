package com.rolelens.api.job.service;

import com.rolelens.api.job.dto.CreatePostingRequest;
import com.rolelens.api.job.dto.PostingResponse;
import com.rolelens.api.job.entity.JobPosting;
import com.rolelens.api.job.repository.JobPostingRepository;
import com.rolelens.api.job.utilities.ContentHasher;
import com.rolelens.api.job.utilities.PostingTitleExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class JobPostingService {

    private final JobPostingRepository repository;

    public JobPostingService(JobPostingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PostingResponse create(CreatePostingRequest request) {
        String text = request.text().trim();
        String contentHash = ContentHasher.sha256(text);
        JobPosting jobPosting = new JobPosting(
                request.source(),
                text,
                request.url()
        );

        jobPosting.setContentHash(contentHash.getBytes());
        jobPosting.setTitle(PostingTitleExtractor.guessTitle(text));
        JobPosting savedJobPosting = repository.save(jobPosting);
        return PostingResponse.from(savedJobPosting);
    }

    @Transactional(readOnly = true)
    public List<PostingResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(PostingResponse::from)
                .toList();
    }
}
