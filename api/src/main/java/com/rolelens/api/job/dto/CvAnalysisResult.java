package com.rolelens.api.job.dto;

import tools.jackson.databind.JsonNode;

public record CvAnalysisResult(
        JsonNode atsView,
        JsonNode atsFindings,
        JsonNode profile
) {
}
