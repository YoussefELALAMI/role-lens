package com.rolelens.api.job.service;

import com.rolelens.api.job.dto.CvProfile;
import com.rolelens.api.job.exception.ModelProcessingException;

/**
 * Turns the text of a CV into a structured profile. Hides the model provider (ADR-002):
 * the rest of the code depends on this interface, never on an SDK.
 * <p>
 * The result is schema-valid but not yet grounded: skills may carry quotes that are not in the CV.
 * Checking that is the caller's job ("the model reads, your code decides").
 */
public interface ProfileExtractor {

    /** Identifies the prompt, stored in cv_profile.prompt_version. */
    String promptVersion();

    /**
     * @param cvText the text extracted from the PDF, as is
     * @throws ModelProcessingException when the model is unreachable, times out, or still returns
     *                                  an invalid profile after the single retry
     */
    CvProfile extract(String cvText) throws ModelProcessingException;
}
