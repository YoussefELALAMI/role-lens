package com.rolelens.api.job.utilities;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    @Test
    void matchesAKnownSha256Value() {
        // sha256("hello world")
        assertThat(ContentHasher.sha256("hello world"))
                .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void returnsSixtyFourLowercaseHexCharacters() {
        assertThat(ContentHasher.sha256("anything")).matches("[0-9a-f]{64}");
    }

    @Test
    void ignoresCase() {
        assertThat(ContentHasher.sha256("Senior Engineer")).isEqualTo(ContentHasher.sha256("SENIOR ENGINEER"));
    }

    @Test
    void ignoresSurroundingAndRepeatedWhitespace() {
        assertThat(ContentHasher.sha256("  Senior   Engineer\n\nBerlin\t "))
                .isEqualTo(ContentHasher.sha256("Senior Engineer Berlin"));
    }

    @Test
    void differentTextGivesADifferentHash() {
        assertThat(ContentHasher.sha256("Backend Engineer")).isNotEqualTo(ContentHasher.sha256("Frontend Engineer"));
    }
}
