import { createEvent, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import CvUploader from "./CvUploader";

const pdf = new File(["%PDF-1.4"], "jane-cv.pdf", { type: "application/pdf" });

interface Options {
  isUploading?: boolean;
  fileName?: string | null;
}

function setup({ isUploading = false, fileName = null }: Options = {}) {
  const onFileSelected = vi.fn();
  const { container } = render(
    <CvUploader onFileSelected={onFileSelected} isUploading={isUploading} fileName={fileName} />,
  );
  const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
  const zone = container.firstElementChild as HTMLElement;
  return { onFileSelected, input, zone };
}

describe("CvUploader wording", () => {
  it("invites the user to drop a CV before there is one", () => {
    setup();

    expect(screen.getByText("Drop your CV here")).toBeInTheDocument();
    expect(screen.getByText("PDF, up to 5 MB.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upload CV" })).toBeEnabled();
  });

  it("shows the file name and offers a replacement once a CV was read", () => {
    setup({ fileName: "jane-cv.pdf" });

    expect(screen.getByText("jane-cv.pdf")).toBeInTheDocument();
    expect(screen.getByText("Drop another PDF to replace it.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upload a different CV" })).toBeEnabled();
  });

  it("says it is reading the CV and disables the button while uploading", () => {
    setup({ isUploading: true, fileName: "jane-cv.pdf" });

    expect(screen.getByText("Reading your CV...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Uploading..." })).toBeDisabled();
  });
});

describe("CvUploader file picker", () => {
  it("only offers PDF files", () => {
    const { input } = setup();

    expect(input.accept).toContain("application/pdf");
    expect(input.accept).toContain(".pdf");
  });

  it("opens the file picker when the button is clicked", async () => {
    const { input } = setup();
    const openPicker = vi.spyOn(input, "click");

    await userEvent.click(screen.getByRole("button", { name: "Upload CV" }));

    expect(openPicker).toHaveBeenCalledTimes(1);
  });

  it("passes the chosen file on", async () => {
    const { input, onFileSelected } = setup();

    await userEvent.upload(input, pdf);

    expect(onFileSelected).toHaveBeenCalledTimes(1);
    expect(onFileSelected).toHaveBeenCalledWith(pdf);
  });

  it("fires again when the same file is chosen twice in a row", async () => {
    const { input, onFileSelected } = setup();

    await userEvent.upload(input, pdf);
    await userEvent.upload(input, pdf);

    expect(onFileSelected).toHaveBeenCalledTimes(2);
  });

  it("does nothing when the picker is closed without a file", () => {
    const { input, onFileSelected } = setup();

    fireEvent.change(input, { target: { files: [] } });

    expect(onFileSelected).not.toHaveBeenCalled();
  });
});

describe("CvUploader drag and drop", () => {
  it("passes a dropped file on", () => {
    const { zone, onFileSelected } = setup();

    fireEvent.drop(zone, { dataTransfer: { files: [pdf] } });

    expect(onFileSelected).toHaveBeenCalledWith(pdf);
  });

  it("ignores a drop that carries no file", () => {
    const { zone, onFileSelected } = setup();

    fireEvent.drop(zone, { dataTransfer: { files: [] } });

    expect(onFileSelected).not.toHaveBeenCalled();
  });

  it("ignores a drop while a CV is still being read", () => {
    const { zone, onFileSelected } = setup({ isUploading: true });

    fireEvent.drop(zone, { dataTransfer: { files: [pdf] } });

    expect(onFileSelected).not.toHaveBeenCalled();
  });

  it("highlights the area while a file is dragged over it", () => {
    const { zone } = setup();

    fireEvent.dragOver(zone);
    expect(zone).toHaveClass("is-dragging");

    fireEvent.dragLeave(zone);
    expect(zone).not.toHaveClass("is-dragging");
  });

  it("stays highlighted when the drag only moves over a child element", () => {
    const { zone } = setup();
    const child = screen.getByText("Drop your CV here");

    fireEvent.dragOver(zone);
    // jsdom's drag events have no relatedTarget, so set it by hand.
    const leave = createEvent.dragLeave(zone);
    Object.defineProperty(leave, "relatedTarget", { value: child });
    fireEvent(zone, leave);

    expect(zone).toHaveClass("is-dragging");
  });

  it("removes the highlight after a drop", () => {
    const { zone } = setup();

    fireEvent.dragOver(zone);
    fireEvent.drop(zone, { dataTransfer: { files: [pdf] } });

    expect(zone).not.toHaveClass("is-dragging");
  });
});
