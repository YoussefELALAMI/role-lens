import { useEffect, useState } from "react";
import { ApiError } from "./api/httpClient";
import { createPosting, listPostings, type PostingResponse } from "./api/postings";
import "./App.css";

function App() {
  const [text, setText] = useState("");
  const [postings, setPostings] = useState<PostingResponse[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadPostings();
  }, []);

  async function loadPostings() {
    try {
      const data = await listPostings();
      setPostings(data);
    } catch {
      setError("Could not load saved ads.");
    }
  }

  async function handleSave() {
    const trimmed = text.trim();
    if (!trimmed) return;

    setIsSaving(true);
    setError(null);
    try {
      await createPosting({ source: "PASTE", text: trimmed });
      setText("");
      await loadPostings();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save the job ad.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="page">
      <h1>RoleLens</h1>

      <textarea
        value={text}
        onChange={(event) => setText(event.target.value)}
        placeholder="Paste a job ad here..."
        rows={12}
      />

      <button type="button" onClick={handleSave} disabled={isSaving || !text.trim()}>
        {isSaving ? "Saving..." : "Save"}
      </button>

      {error && <p className="error">{error}</p>}

      <h2>Saved ads ({postings.length})</h2>

      {postings.length === 0 ? (
        <p>Nothing saved yet.</p>
      ) : (
        <ul>
          {postings.map((posting) => (
            <li key={posting.id}>
              <strong>{posting.title ?? "(untitled)"}</strong>
              {posting.company ? ` @ ${posting.company}` : ""}
              {" — "}
              {new Date(posting.createdAt).toLocaleString()}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default App;
