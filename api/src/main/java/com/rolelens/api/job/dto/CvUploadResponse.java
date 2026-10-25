package com.rolelens.api.job.dto;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record CvUploadResponse(
        UUID id,
        JsonNode atsView,
        JsonNode atsFindings,
        JsonNode profile
) {}
