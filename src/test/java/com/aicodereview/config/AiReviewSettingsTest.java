package com.aicodereview.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiReviewSettingsTest {

    @Test
    void keepsOutputInProjectRoot() {
        assertEquals("notes.md", AiReviewSettings.sanitizeFileName("docs/notes.md"));
        assertEquals("notes.md", AiReviewSettings.sanitizeFileName("..\\notes.md"));
        assertEquals("CODE_REVIEW.md", AiReviewSettings.sanitizeFileName("../"));
        assertEquals("CODE_REVIEW.md", AiReviewSettings.sanitizeFileName("   "));
    }

    @Test
    void appendsMarkdownExtensionWhenMissing() {
        assertEquals("review.md", AiReviewSettings.sanitizeFileName("review"));
        assertEquals("review.md", AiReviewSettings.sanitizeFileName("review.md"));
    }

    @Test
    void normalizesEmptyEndpointAndModelToDefaults() {
        AiReviewSettings.State state = new AiReviewSettings.State();
        state.endpoint = " ";
        state.model = "";
        state.temperature = 9;
        state.maxDiffChars = 1;

        AiReviewSettings.normalize(state);

        assertEquals(AiReviewSettings.DEFAULT_ENDPOINT, state.endpoint);
        assertEquals(AiReviewSettings.DEFAULT_MODEL, state.model);
        assertEquals(0.1, state.temperature);
        assertEquals(4000, state.maxDiffChars);
    }
}
