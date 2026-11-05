import { useRef, useState, type ChangeEvent, type DragEvent } from "react";

interface CvUploaderProps {
  onFileSelected: (file: File) => void;
  isUploading: boolean;
  /** Name of the CV that was read successfully, if any. */
  fileName: string | null;
}

export default function CvUploader({ onFileSelected, isUploading, fileName }: CvUploaderProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = useState(false);

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Clear the input so that picking the same file again still fires a change.
    event.target.value = "";
    if (file) onFileSelected(file);
  }

  function handleDragLeave(event: DragEvent<HTMLDivElement>) {
    // Moving over a child element also fires dragleave; ignore that.
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setIsDragging(false);
    }
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    const file = event.dataTransfer.files?.[0];
    if (file && !isUploading) onFileSelected(file);
  }

  const title = isUploading ? "Reading your CV..." : (fileName ?? "Drop your CV here");
  const hint = fileName && !isUploading ? "Drop another PDF to replace it." : "PDF, up to 5 MB.";

  return (
    <div
      className={`dropzone${isDragging ? " is-dragging" : ""}`}
      onDragOver={(event) => {
        event.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      <input
        ref={inputRef}
        type="file"
        accept="application/pdf,.pdf"
        onChange={handleChange}
        hidden
      />
      <p className="dropzone-title">{title}</p>
      <p className="dropzone-hint">{hint}</p>
      <button
        type="button"
        className="btn btn-signal"
        onClick={() => inputRef.current?.click()}
        disabled={isUploading}
      >
        {isUploading ? "Uploading..." : fileName ? "Upload a different CV" : "Upload CV"}
      </button>
    </div>
  );
}
