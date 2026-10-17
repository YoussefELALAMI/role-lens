/**
 * Thin fetch wrapper. Nothing here knows about job postings or any other
 * domain concept — that lives in the per-resource modules (e.g. postings.ts).
 * Keeping this generic is what lets HTTP concerns stay out of the pages.
 */

const BASE_URL = "/api";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === "string") {
      return body.message;
    }
  } catch {
    // Response body wasn't JSON (or was empty) - fall back below.
  }
  return response.statusText || `Request failed with status ${response.status}`;
}

export const httpClient = {
  get: <T>(path: string): Promise<T> => request<T>(path, { method: "GET" }),

  post: <T>(path: string, body: unknown): Promise<T> =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
};
