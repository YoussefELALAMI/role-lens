/**
 * Mirrors com.rolelens.api.job.dto.CvUploadResponse on the backend.
 * Endpoint: job/controller/CvController.java (/api/cv)
 */
import { httpClient } from "./httpClient";

/** Matches the limit enforced by the backend (CvService.MAX_FILE_SIZE). */
export const MAX_CV_SIZE_BYTES = 5 * 1024 * 1024;

export interface CvUploadResponse {
  id: string;
  /** The text extracted from the PDF, always present on a successful upload. */
  rawText: string;
  /** The model-derived fields are null when the analysis did not produce a result. */
  atsView: unknown;
  atsFindings: unknown;
  profile: unknown;
}

export function uploadCv(file: File): Promise<CvUploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return httpClient.postForm<CvUploadResponse>("/cv", formData);
}
