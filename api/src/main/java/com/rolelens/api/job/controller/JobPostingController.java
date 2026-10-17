package com.rolelens.api.job.controller;

import com.rolelens.api.job.dto.CreatePostingRequest;
import com.rolelens.api.job.dto.PostingResponse;
import com.rolelens.api.job.service.JobPostingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/postings")
public class JobPostingController {

    private final JobPostingService service;

    public JobPostingController(JobPostingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostingResponse create(
            @Valid @RequestBody CreatePostingRequest request
    ){
        return service.create(request);
    }

    @GetMapping
    public List<PostingResponse> findAll() {
        return service.findAll();
    }
}
