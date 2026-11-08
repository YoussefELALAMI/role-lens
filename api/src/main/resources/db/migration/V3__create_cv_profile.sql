-- The model's structured reading of the CV, regenerated when the prompt improves.
-- No contact details, photo, age or nationality: matching doesn't need them.
CREATE TABLE cv_profile (
    cv_id              UUID        PRIMARY KEY REFERENCES cv ON DELETE CASCADE,
    profile            JSONB       NOT NULL,           -- PD: dated experience, education, skills, languages
    experience_months  INTEGER,                        -- computed in code from the dated entries
    prompt_version     TEXT        NOT NULL,
    schema_version     TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
