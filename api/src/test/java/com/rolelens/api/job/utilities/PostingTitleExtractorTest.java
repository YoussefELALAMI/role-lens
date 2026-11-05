package com.rolelens.api.job.utilities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostingTitleExtractorTest {

    @Test
    void returnsNullForNullText() {
        assertThat(PostingTitleExtractor.guessTitle(null)).isNull();
    }

    @Test
    void returnsNullWhenThereIsNoNonBlankLine() {
        assertThat(PostingTitleExtractor.guessTitle("")).isNull();
        assertThat(PostingTitleExtractor.guessTitle("  \n\t\n   ")).isNull();
    }

    @Test
    void usesTheFirstLine() {
        assertThat(PostingTitleExtractor.guessTitle("Senior Backend Engineer\nAcme Corp\nBerlin"))
                .isEqualTo("Senior Backend Engineer");
    }

    @Test
    void skipsLeadingBlankLinesAndTrimsTheTitle() {
        assertThat(PostingTitleExtractor.guessTitle("\n\n   Staff Engineer  \nDetails"))
                .isEqualTo("Staff Engineer");
    }

    @Test
    void handlesWindowsLineEndings() {
        assertThat(PostingTitleExtractor.guessTitle("Data Engineer\r\nAcme Corp"))
                .isEqualTo("Data Engineer");
    }

    @Test
    void cutsVeryLongTitlesToTwoHundredCharacters() {
        String title = PostingTitleExtractor.guessTitle("x".repeat(500));

        assertThat(title).hasSize(200);
    }

    @Test
    void keepsATitleOfExactlyTwoHundredCharacters() {
        String exactly200 = "y".repeat(200);

        assertThat(PostingTitleExtractor.guessTitle(exactly200)).isEqualTo(exactly200);
    }
}
