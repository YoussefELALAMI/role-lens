CREATE TABLE job_posting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source TEXT NOT NULL CHECK (source IN('PAGE', 'PASTE', 'GREENHOUSE', 'LEVER')),
    source_url TEXT,
    raw_text TEXT NOT NULL, -- can hold third-party PD (a recruiter's name, email)
    content_hash BYTEA NOT NULL UNIQUE,
    extraction_status TEXT NOT NULL DEFAULT 'PENDING'
                                    CHECK (extraction_status IN('PENDING', 'DONE', 'NOT_A_JOB_AD')),
    extracted JSONB,
    title TEXT,
    company TEXT,
    language TEXT,
    location TEXT,
    prompt_version TEXT,
    schema_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX job_posting_newest ON job_posting (created_at DESC);