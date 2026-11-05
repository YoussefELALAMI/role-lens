import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, httpClient } from "./httpClient";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

/** The URL and options of the one fetch call a test expects. */
function lastCall(): { url: string; init: RequestInit; headers: Record<string, string> } {
  expect(fetchMock).toHaveBeenCalledTimes(1);
  const [url, init = {}] = fetchMock.mock.calls[0];
  return { url: String(url), init, headers: (init.headers ?? {}) as Record<string, string> };
}

describe("httpClient requests", () => {
  it("sends GET requests under /api and returns the parsed JSON", async () => {
    fetchMock.mockResolvedValue(jsonResponse([{ id: "1" }]));

    const result = await httpClient.get<{ id: string }[]>("/postings");

    const { url, init, headers } = lastCall();
    expect(url).toBe("/api/postings");
    expect(init.method).toBe("GET");
    expect(headers["Content-Type"]).toBe("application/json");
    expect(result).toEqual([{ id: "1" }]);
  });

  it("sends POST bodies as JSON", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "9" }, { status: 201 }));

    const result = await httpClient.post<{ id: string }>("/postings", { source: "PASTE", text: "Hi" });

    const { url, init, headers } = lastCall();
    expect(url).toBe("/api/postings");
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ source: "PASTE", text: "Hi" }));
    expect(headers["Content-Type"]).toBe("application/json");
    expect(result).toEqual({ id: "9" });
  });

  it("sends a form as multipart and lets the browser set the Content-Type", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "3" }, { status: 201 }));
    const form = new FormData();
    form.append("file", new File(["x"], "cv.pdf", { type: "application/pdf" }));

    await httpClient.postForm("/cv", form);

    const { url, init, headers } = lastCall();
    expect(url).toBe("/api/cv");
    expect(init.method).toBe("POST");
    // The very same FormData goes out untouched, not a JSON string.
    expect(init.body).toBe(form);
    // A hand-written Content-Type would drop the multipart boundary and break the upload.
    expect(headers).not.toHaveProperty("Content-Type");
  });

  it("returns undefined for a 204 response", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(httpClient.get("/anything")).resolves.toBeUndefined();
  });

  it("lets a network failure through", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(httpClient.get("/postings")).rejects.toThrow("Failed to fetch");
  });
});

describe("httpClient errors", () => {
  it("throws an ApiError with the status and the message from the response body", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ message: "Only PDF files are accepted" }, { status: 400 }),
    );

    const error = await httpClient.get("/cv").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toBeInstanceOf(Error);
    expect(error).toMatchObject({
      name: "ApiError",
      status: 400,
      message: "Only PDF files are accepted",
    });
  });

  it("falls back to the status text when the body is not JSON", async () => {
    fetchMock.mockResolvedValue(
      new Response("<html>Bad gateway</html>", { status: 502, statusText: "Bad Gateway" }),
    );

    await expect(httpClient.get("/cv")).rejects.toMatchObject({ status: 502, message: "Bad Gateway" });
  });

  it("falls back to the status text when the JSON has no message", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ error: "boom" }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(httpClient.get("/cv")).rejects.toMatchObject({ message: "Internal Server Error" });
  });

  it("ignores a message that is not a string", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ message: { nested: true } }, { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(httpClient.get("/cv")).rejects.toMatchObject({ message: "Internal Server Error" });
  });

  it("names the status when there is no body and no status text", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 503 }));

    await expect(httpClient.get("/cv")).rejects.toMatchObject({
      status: 503,
      message: "Request failed with status 503",
    });
  });
});
