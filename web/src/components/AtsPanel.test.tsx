import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import AtsPanel from "./AtsPanel";

function renderPanel(rawText: string, fileName = "jane-cv.pdf") {
  return render(<AtsPanel fileName={fileName} rawText={rawText} />);
}

function lineTexts(): string[] {
  return screen.getAllByRole("listitem").map((item) => item.textContent ?? "");
}

describe("AtsPanel", () => {
  it("names the section and the file it was read from", () => {
    renderPanel("Jane Doe", "my-cv.pdf");

    expect(screen.getByRole("heading", { name: "What an ATS sees" })).toBeInTheDocument();
    expect(screen.getByText("my-cv.pdf")).toBeInTheDocument();
  });

  it("shows one list item per line of text", () => {
    renderPanel("Jane Doe\nBackend Engineer\nJava, Spring");

    expect(lineTexts()).toEqual(["Jane Doe", "Backend Engineer", "Java, Spring"]);
  });

  it("keeps blank lines in the middle, because they are part of what the ATS reads", () => {
    renderPanel("Skills\n\nExperience");

    // A blank line is shown as a non-breaking space so it keeps its height.
    expect(lineTexts()).toEqual(["Skills", " ", "Experience"]);
  });

  it("treats Windows line endings as normal line breaks", () => {
    renderPanel("Jane Doe\r\nBackend Engineer\r\n");

    expect(lineTexts()).toEqual(["Jane Doe", "Backend Engineer"]);
  });

  it("drops the empty lines at the very end only", () => {
    renderPanel("Jane\n\nDoe\n\n\n");

    expect(lineTexts()).toEqual(["Jane", " ", "Doe"]);
  });

  it("keeps the indentation of a line", () => {
    renderPanel("Experience\n  Built the billing API");

    // toHaveTextContent collapses whitespace, so compare the raw text.
    expect(lineTexts()).toEqual(["Experience", "  Built the billing API"]);
  });

  it("counts the lines and the words", () => {
    renderPanel("Jane Doe\nBackend Engineer\nJava Spring PostgreSQL");

    expect(screen.getByText("3 lines, 7 words")).toBeInTheDocument();
  });

  it("uses the singular for a single line and a single word", () => {
    renderPanel("Jane");

    expect(screen.getByText("1 line, 1 word")).toBeInTheDocument();
  });

  it("counts words across lines and repeated spaces", () => {
    renderPanel("one   two\n\nthree\tfour");

    expect(screen.getByText(/4 words/)).toBeInTheDocument();
  });

  it("shows text that looks like HTML as plain text and never as markup", () => {
    const { container } = renderPanel('<img src="x" onerror="alert(1)">\n<script>alert(2)</script>');

    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByText('<img src="x" onerror="alert(1)">')).toBeInTheDocument();
    expect(screen.getByText("<script>alert(2)</script>")).toBeInTheDocument();
  });

  it("puts the text in a labelled region that the keyboard can scroll", () => {
    renderPanel("Jane Doe");

    const region = screen.getByRole("region", { name: "Extracted CV text" });
    expect(region).toHaveAttribute("tabindex", "0");
    expect(within(region).getAllByRole("listitem")).toHaveLength(1);
  });
});
