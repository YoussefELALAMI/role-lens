/**
 * Mirrors com.rolelens.api.job.* on the backend:
 *   - Source           -> job/enums/Source.java
 *   - CreatePostingRequest -> job/dto/CreatePostingRequest.java
 *   - PostingResponse  -> job/dto/PostingResponse.java
 * Endpoints: job/controller/JobPostingController.java (/api/postings)
 */
import { httpClient } from "./httpClient";

export type Source = "PAGE" | "PASTE" | "GREENHOUSE" | "LEVER";

export interface CreatePostingRequest {
  source: Source;
  url?: string;
  text: string;
}

export interface PostingResponse {
  id: string;
  title: string | null;
  company: string | null;
  createdAt: string;
}

const RESOURCE = "/postings";

export function listPostings(): Promise<PostingResponse[]> {
  return httpClient.get<PostingResponse[]>(RESOURCE);
}

export function createPosting(
  request: CreatePostingRequest,
): Promise<PostingResponse> {
  return httpClient.post<PostingResponse>(RESOURCE, request);
}
