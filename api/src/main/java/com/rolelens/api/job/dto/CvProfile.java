package com.rolelens.api.job.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;
import java.util.List;

public record CvProfile(
        @NotNull @Size(max = 50) List<@NotNull @Valid Experience> experience,
        @NotNull @Size(max = 20) List<@NotNull @Valid Education> education,
        @NotNull @Size(max = 100) List<@NotNull @Valid Skill> skills,
        @NotNull @Size(max = 20) List<@NotNull @Valid Language> languages
) {

    /** Stored in cv_profile.schema_version; bump when the shape of this record changes. */
    public static final String SCHEMA_VERSION = "1";

    public CvProfile {
        experience = experience == null ? List.of() : List.copyOf(experience);
        education = education == null ? List.of() : List.copyOf(education);
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    public record Experience(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String employer,
            @NotNull YearMonth startDate,
            YearMonth endDate,
            @Size(max = 2000) String description
    ) {

        @JsonIgnore
        @AssertTrue(message = "endDate must not be before startDate")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }

        @JsonIgnore
        public boolean isCurrent() {
            return endDate == null;
        }
    }

    public record Education(
            @NotBlank @Size(max = 200) String degree,
            @Size(max = 200) String institution,
            @Size(max = 200) String fieldOfStudy,
            YearMonth startDate,
            YearMonth endDate
    ) {

        @JsonIgnore
        @AssertTrue(message = "endDate must not be before startDate")
        public boolean isDateRangeValid() {
            return endDate == null || startDate == null || !endDate.isBefore(startDate);
        }
    }

    /** A skill and the CV line that shows it. No quote, no skill: the grounding check drops it. */
    public record Skill(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 400) String quote
    ) {
    }

    public record Language(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 50) String proficiency
    ) {
    }
}
