import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { MAX_CV_SIZE_BYTES, uploadCv, type CvUploadResponse } from "./api/cvService";
import { ApiError } from "./api/httpClient";
import { createPosting, listPostings, type PostingResponse } from "./api/postings";

// Replace only the network calls. ApiError stays real, so the app's
// `instanceof ApiError` checks run exactly as they do in the browser.
vi.mock("./api/cvService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/cvService")>()),
  uploadCv: vi.fn(),
}));
vi.mock("./api/postings", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/postings")>()),
  listPostings: vi.fn(),
  createPosting: vi.fn(),
}));

const uploadCvMock = vi.mocked(uploadCv);
const listPostingsMock = vi.mocked(listPostings);
const createPostingMock = vi.mocked(createPosting);

function posting(overrides: Partial<PostingResponse> = {}): PostingResponse {
  return {
    id: "1",
    title: "Backend Engineer",
    company: null,
    createdAt: "2026-11-05T10:00:00Z",
    ...overrides,
  };
}

function cvResponse(rawText: string): CvUploadResponse {
  return { id: "cv-1", rawText, atsView: null, atsFindings: [], profile: null };
}

function pdfFile(name = "jane-cv.pdf"): File {
  return new File(["%PDF-1.4"], name, { type: "application/pdf" });
}

/** Renders the app and waits for the first load of saved ads to finish. */
async function renderApp() {
  const view = render(<App />);
  await waitFor(() => expect(listPostingsMock).toHaveBeenCalled());
  const fileInput = view.container.querySelector<HTMLInputElement>('input[type="file"]')!;
  return { ...view, fileInput };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  vi.resetAllMocks();
  listPostingsMock.mockResolvedValue([]);
});

describe("the page", () => {
  it("has one main heading and the two inputs", async () => {
    await renderApp();

    expect(screen.getByRole("heading", { level: 1, name: "See your CV the way an ATS does." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upload CV" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Job ad" })).toBeInTheDocument();
  });

  it("does not show the ATS panel before a CV is uploaded", async () => {
    await renderApp();

    expect(screen.queryByRole("heading", { name: "What an ATS sees" })).not.toBeInTheDocument();
  });
});

describe("saved job ads", () => {
  it("lists the saved ads with their company and count", async () => {
    listPostingsMock.mockResolvedValue([
      posting({ id: "1", title: "Backend Engineer", company: "Acme" }),
      posting({ id: "2", title: "Platform Engineer", company: null }),
    ]);

    await renderApp();

    expect(await screen.findByText("Backend Engineer")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.getByText("Platform Engineer")).toBeInTheDocument();
    expect(screen.getByText("2 saved")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });

  it("shows the date each ad was saved", async () => {
    listPostingsMock.mockResolvedValue([posting({ createdAt: "2026-11-05T10:00:00Z" })]);

    await renderApp();

    expect(await screen.findByText(new Date("2026-11-05T10:00:00Z").toLocaleDateString())).toBeInTheDocument();
  });

  it("falls back to (untitled) when an ad has no title", async () => {
    listPostingsMock.mockResolvedValue([posting({ title: null })]);

    await renderApp();

    expect(await screen.findByText("(untitled)")).toBeInTheDocument();
  });

  it("tells the user what to do when nothing is saved", async () => {
    await renderApp();

    expect(await screen.findByText(/Nothing saved yet/)).toBeInTheDocument();
    expect(screen.getByText("0 saved")).toBeInTheDocument();
  });

  it("shows an error when the saved ads cannot be loaded", async () => {
    listPostingsMock.mockRejectedValue(new Error("network down"));

    await renderApp();

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load saved ads.");
  });
});

describe("uploading a CV", () => {
  it("sends the file and shows what an ATS sees", async () => {
    uploadCvMock.mockResolvedValue(cvResponse("Jane Doe\nBackend Engineer"));
    const { fileInput } = await renderApp();
    const file = pdfFile();

    await userEvent.upload(fileInput, file);

    expect(uploadCvMock).toHaveBeenCalledWith(file);
    expect(await screen.findByRole("heading", { name: "What an ATS sees" })).toBeInTheDocument();
    const text = screen.getByRole("region", { name: "Extracted CV text" });
    expect(within(text).getByText("Jane Doe")).toBeInTheDocument();
    expect(within(text).getByText("Backend Engineer")).toBeInTheDocument();
    // The upload area now shows the file that was read, and offers a replacement.
    expect(screen.getByRole("button", { name: "Upload a different CV" })).toBeInTheDocument();
  });

  it("shows progress while the CV is being read", async () => {
    const pending = deferred<CvUploadResponse>();
    uploadCvMock.mockReturnValue(pending.promise);
    const { fileInput } = await renderApp();

    await userEvent.upload(fileInput, pdfFile());

    expect(screen.getByText("Reading your CV...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Uploading..." })).toBeDisabled();

    pending.resolve(cvResponse("Jane Doe"));
    expect(await screen.findByRole("heading", { name: "What an ATS sees" })).toBeInTheDocument();
    expect(screen.queryByText("Reading your CV...")).not.toBeInTheDocument();
  });

  it("shows the server's message when the upload is refused", async () => {
    uploadCvMock.mockRejectedValue(new ApiError(400, "PDF contains no extractable text"));
    const { fileInput } = await renderApp();

    await userEvent.upload(fileInput, pdfFile());

    expect(await screen.findByRole("alert")).toHaveTextContent("PDF contains no extractable text");
    expect(screen.queryByRole("heading", { name: "What an ATS sees" })).not.toBeInTheDocument();
    expect(screen.getByText("Drop your CV here")).toBeInTheDocument();
  });

  it("shows a general message when something unexpected goes wrong", async () => {
    uploadCvMock.mockRejectedValue(new TypeError("Failed to fetch"));
    const { fileInput } = await renderApp();

    await userEvent.upload(fileInput, pdfFile());

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not upload the CV.");
  });

  it("refuses a file over 5 MB without sending it", async () => {
    const { fileInput } = await renderApp();
    const tooBig = new File([new Uint8Array(MAX_CV_SIZE_BYTES + 1)], "big.pdf", { type: "application/pdf" });

    await userEvent.upload(fileInput, tooBig);

    expect(await screen.findByRole("alert")).toHaveTextContent("File must not exceed 5 MB");
    expect(uploadCvMock).not.toHaveBeenCalled();
  });

  it("accepts a file of exactly 5 MB", async () => {
    uploadCvMock.mockResolvedValue(cvResponse("Jane Doe"));
    const { fileInput } = await renderApp();
    const exactly = new File([new Uint8Array(MAX_CV_SIZE_BYTES)], "limit.pdf", { type: "application/pdf" });

    await userEvent.upload(fileInput, exactly);

    expect(uploadCvMock).toHaveBeenCalledWith(exactly);
  });

  it("removes the previous CV when the next upload fails, so a stale result is never shown", async () => {
    uploadCvMock.mockResolvedValueOnce(cvResponse("First CV text"));
    const { fileInput } = await renderApp();
    await userEvent.upload(fileInput, pdfFile("first.pdf"));
    expect(await screen.findByText("First CV text")).toBeInTheDocument();

    uploadCvMock.mockRejectedValueOnce(new ApiError(400, "Invalid PDF file"));
    await userEvent.upload(fileInput, pdfFile("second.pdf"));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid PDF file");
    expect(screen.queryByText("First CV text")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "What an ATS sees" })).not.toBeInTheDocument();
  });

  it("replaces the CV and clears the old error after a successful upload", async () => {
    uploadCvMock.mockRejectedValueOnce(new ApiError(400, "Invalid PDF file"));
    const { fileInput } = await renderApp();
    await userEvent.upload(fileInput, pdfFile("bad.pdf"));
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    uploadCvMock.mockResolvedValueOnce(cvResponse("Good CV text"));
    await userEvent.upload(fileInput, pdfFile("good.pdf"));

    expect(await screen.findByText("Good CV text")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("saving a job ad", () => {
  it("keeps Save disabled until there is some text", async () => {
    await renderApp();
    const save = screen.getByRole("button", { name: "Save job ad" });
    const box = screen.getByRole("textbox", { name: "Job ad" });

    expect(save).toBeDisabled();

    await userEvent.type(box, "   ");
    expect(save).toBeDisabled();

    await userEvent.type(box, "Backend Engineer");
    expect(save).toBeEnabled();
  });

  it("saves the trimmed text as a pasted ad, empties the box and reloads the list", async () => {
    createPostingMock.mockResolvedValue(posting());
    await renderApp();
    const box = screen.getByRole("textbox", { name: "Job ad" });

    await userEvent.type(box, "  Backend Engineer at Acme  ");
    await userEvent.click(screen.getByRole("button", { name: "Save job ad" }));

    await waitFor(() => expect(box).toHaveValue(""));
    expect(createPostingMock).toHaveBeenCalledWith({ source: "PASTE", text: "Backend Engineer at Acme" });
    // Once on load, once after saving.
    expect(listPostingsMock).toHaveBeenCalledTimes(2);
  });

  it("shows the new ad in the list after saving", async () => {
    createPostingMock.mockResolvedValue(posting({ title: "Data Engineer" }));
    await renderApp();
    listPostingsMock.mockResolvedValue([posting({ title: "Data Engineer" })]);

    await userEvent.type(screen.getByRole("textbox", { name: "Job ad" }), "Data Engineer");
    await userEvent.click(screen.getByRole("button", { name: "Save job ad" }));

    expect(await screen.findByText("Data Engineer")).toBeInTheDocument();
    expect(screen.getByText("1 saved")).toBeInTheDocument();
  });

  it("shows that it is saving and prevents a second click", async () => {
    const pending = deferred<PostingResponse>();
    createPostingMock.mockReturnValue(pending.promise);
    await renderApp();

    await userEvent.type(screen.getByRole("textbox", { name: "Job ad" }), "Backend Engineer");
    await userEvent.click(screen.getByRole("button", { name: "Save job ad" }));

    expect(screen.getByRole("button", { name: "Saving..." })).toBeDisabled();

    pending.resolve(posting());
    expect(await screen.findByRole("button", { name: "Save job ad" })).toBeInTheDocument();
  });

  it("shows the server's message and keeps the text when saving fails", async () => {
    createPostingMock.mockRejectedValue(new ApiError(409, "This job ad is already saved"));
    await renderApp();
    const box = screen.getByRole("textbox", { name: "Job ad" });

    await userEvent.type(box, "Backend Engineer");
    await userEvent.click(screen.getByRole("button", { name: "Save job ad" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This job ad is already saved");
    // Nothing is lost: the user can fix the problem and try again.
    expect(box).toHaveValue("Backend Engineer");
  });

  it("shows a general message when saving fails for another reason", async () => {
    createPostingMock.mockRejectedValue(new TypeError("Failed to fetch"));
    await renderApp();

    await userEvent.type(screen.getByRole("textbox", { name: "Job ad" }), "Backend Engineer");
    await userEvent.click(screen.getByRole("button", { name: "Save job ad" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not save the job ad.");
  });
});
