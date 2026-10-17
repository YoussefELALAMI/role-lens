package com.rolelens.api.job.dto;

import com.rolelens.api.job.enums.Source;
import jakarta.validation.constraints.NotNull;

/**
 * {source: PAGE or PASTE, url?, text}
 */
public record CreatePostingRequest(
        @NotNull(message = "Source cannot be null")
        Source source,
        String url,
        String text
) {
}
