package com.rolelens.api.job.utilities;

public final class PostingTitleExtractor {

    private static final int MAX_TITLE_LENGTH = 200;

    private PostingTitleExtractor() {
    }

    // Best-effort guess (first non-blank line) so the UI isn't blank while a
    // real extraction pipeline isn't built yet; company is left for that pipeline.
    public static String guessTitle(String rawText) {
        if (rawText == null) {
            return null;
        }
        return rawText.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .map(line -> line.length() > MAX_TITLE_LENGTH ? line.substring(0, MAX_TITLE_LENGTH) : line)
                .orElse(null);
    }
}
