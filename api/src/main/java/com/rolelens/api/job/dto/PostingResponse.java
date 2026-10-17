package com.rolelens.api.job.dto;

import com.rolelens.api.job.entity.JobPosting;
import com.rolelens.api.job.enums.ExtractionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PostingResponse(

        UUID id,
        String title,
        String company,
        OffsetDateTime createdAt
        //LatestReportSummary latestReport
) {
    public record LatestReportSummary(
            UUID id,
            ExtractionStatus status,
            Integer fitScore
    ) {
    }

    // Overload if your JobAdEntity already contains a direct relationship to the report
    public static PostingResponse from(JobPosting entity) {
        return new PostingResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getCompany(),
                entity.getCreatedAt()
                //LatestReportSummary.from(entity.getLatestReport()) // assuming getter exists on entity
        );
    }
}
