import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MAX_CV_SIZE_BYTES, uploadCv } from "./cvService";
import { ApiError } from "./httpClient";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

const cv = new File(["%PDF-1.4"], "jane-cv.pdf", { type: "application/pdf" });

describe("uploadCv", () => {
  it("posts the file as multipart form data to /api/cv", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: "abc", rawText: "Jane Doe" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await uploadCv(cv);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/cv");
    expect(init?.method).toBe("POST");

    const body = init?.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get("file")).toBe(cv);
    // The browser has to add the multipart boundary itself.
    expect(init?.headers).not.toHaveProperty("Content-Type");
  });

  it("returns the extracted text from the response", async () => {
    const payload = {
      id: "abc",
      rawText: "Jane Doe\nBackend Engineer",
      atsView: null,
      atsFindings: [],
      profile: null,
    };
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await expect(uploadCv(cv)).resolves.toEqual(payload);
  });

  it("rejects with the server's message when the upload is refused", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ message: "PDF contains no extractable text" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const error = await uploadCv(cv).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, message: "PDF contains no extractable text" });
  });
});

describe("MAX_CV_SIZE_BYTES", () => {
  it("matches the backend limit of 5 MB", () => {
    expect(MAX_CV_SIZE_BYTES).toBe(5 * 1024 * 1024);
  });
});
