import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPosting, listPostings } from "./postings";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("postings api", () => {
  it("lists postings with GET /api/postings", async () => {
    const postings = [{ id: "1", title: "Backend Engineer", company: null, createdAt: "2026-11-05T10:00:00Z" }];
    fetchMock.mockResolvedValue(jsonResponse(postings));

    await expect(listPostings()).resolves.toEqual(postings);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/postings");
    expect(init?.method).toBe("GET");
  });

  it("creates a posting with POST /api/postings and a JSON body", async () => {
    const created = { id: "2", title: "Data Engineer", company: null, createdAt: "2026-11-05T11:00:00Z" };
    fetchMock.mockResolvedValue(jsonResponse(created, 201));

    const result = await createPosting({ source: "PASTE", text: "Data Engineer\nAcme" });

    expect(result).toEqual(created);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/postings");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ source: "PASTE", text: "Data Engineer\nAcme" });
  });
});
