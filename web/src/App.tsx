import { useEffect, useState } from "react";
import { ApiError } from "./api/httpClient";
import { MAX_CV_SIZE_BYTES, uploadCv } from "./api/cvService";
import { createPosting, listPostings, type PostingResponse } from "./api/postings";
import AtsPanel from "./components/AtsPanel";
import CvUploader from "./components/CvUploader";
import "./App.css";

function App() {
  const [text, setText] = useState("");
  const [postings, setPostings] = useState<PostingResponse[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cv, setCv] = useState<{ fileName: string; rawText: string } | null>(null);
  const [isUploadingCv, setIsUploadingCv] = useState(false);
  const [cvError, setCvError] = useState<string | null>(null);

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

  async function handleCvSelected(file: File) {
    // Drop the previous result so a failed upload can't leave a stale CV on screen.
    setCv(null);
    setCvError(null);

    if (file.size > MAX_CV_SIZE_BYTES) {
      setCvError("File must not exceed 5 MB");
      return;
    }

    setIsUploadingCv(true);
    try {
      const response = await uploadCv(file);
      setCv({ fileName: file.name, rawText: response.rawText });
    } catch (err) {
      setCvError(err instanceof ApiError ? err.message : "Could not upload the CV.");
    } finally {
      setIsUploadingCv(false);
    }
  }

  return (
    <main className="page">
      <header className="masthead">
        <div className="wordmark">
          <svg width="26" height="26" viewBox="0 0 26 26" fill="none" aria-hidden="true">
            <circle cx="13" cy="13" r="10.5" stroke="currentColor" strokeWidth="3" />
            <circle cx="16" cy="10" r="3" fill="currentColor" />
          </svg>
          RoleLens
        </div>
        <h1>See your CV the way an ATS does.</h1>
      </header>

      <div className="desk">
        <section className="sheet sheet-cv" aria-labelledby="cv-title">
          <h2 id="cv-title">Your CV</h2>

          <CvUploader
            onFileSelected={handleCvSelected}
            isUploading={isUploadingCv}
            fileName={cv?.fileName ?? null}
          />

          {cvError && (
            <p className="error" role="alert">
              {cvError}
            </p>
          )}
        </section>

        <section className="sheet sheet-ad" aria-labelledby="ad-title">
          <h2 id="ad-title">Job ad</h2>

          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Paste the job ad here"
            aria-labelledby="ad-title"
          />

          <div className="actions">
            <button
              type="button"
              className="btn btn-ink"
              onClick={handleSave}
              disabled={isSaving || !text.trim()}
            >
              {isSaving ? "Saving..." : "Save job ad"}
            </button>
          </div>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
        </section>
      </div>

      {cv && <AtsPanel fileName={cv.fileName} rawText={cv.rawText} />}

      <section className="saved">
        <div className="saved-head">
          <h2>Saved job ads</h2>
          <span className="saved-count">{postings.length} saved</span>
        </div>

        {postings.length === 0 ? (
          <p className="empty">Nothing saved yet. Paste a job ad and save it to keep it here.</p>
        ) : (
          <ul className="saved-list">
            {postings.map((posting) => (
              <li key={posting.id}>
                <span>
                  <span className="saved-title">{posting.title ?? "(untitled)"}</span>
                  {posting.company && <span className="saved-company">{posting.company}</span>}
                </span>
                <span className="saved-date">
                  {new Date(posting.createdAt).toLocaleDateString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

export default App;
