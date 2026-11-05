interface AtsPanelProps {
  fileName: string;
  rawText: string;
}

function count(n: number, noun: string): string {
  return `${n} ${noun}${n === 1 ? "" : "s"}`;
}

export default function AtsPanel({ fileName, rawText }: AtsPanelProps) {
  const lines = rawText.replace(/\r\n?/g, "\n").replace(/\n+$/, "").split("\n");
  const words = rawText.trim().split(/\s+/).filter(Boolean).length;

  return (
    <section className="machine" aria-labelledby="machine-title">
      <header className="machine-head">
        <div>
          <h2 id="machine-title">What an ATS sees</h2>
          <p className="machine-sub">
            The plain text read from <strong>{fileName}</strong>. If something is
            missing here, an ATS can't read it either.
          </p>
        </div>
        <p className="machine-stats">
          {count(lines.length, "line")}, {count(words, "word")}
        </p>
      </header>

      {/* Focusable so keyboard users can scroll a long CV. */}
      <div className="machine-scroll" role="region" aria-label="Extracted CV text" tabIndex={0}>
        <ol className="machine-text">
          {lines.map((line, index) => (
            <li key={index}>{line || " "}</li>
          ))}
        </ol>
      </div>
    </section>
  );
}
