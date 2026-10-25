CREATE TABLE cv (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name TEXT NOT NULL,
    file_sha256 BYTEA NOT NULL UNIQUE, -- same file again = same row
    page_count smallint NOT NULL,
    raw_text TEXT NOT NULL,  -- PD: PDFBox output as is = "what an ATS sees"
    links JSONB NOT NULL DEFAULT '[]', -- PD: link annotations, read separately
    ats_findings JSONB NOT NULL, -- PD: findings can quote the CV
    language TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX cv_newest ON cv (uploaded_at DESC);