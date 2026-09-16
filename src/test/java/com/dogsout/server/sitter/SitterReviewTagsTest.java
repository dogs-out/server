package com.dogsout.server.sitter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tags are shown on a stranger's profile, so the allowed list is the only
 * thing standing between a highlight chip and a free-text field.
 */
class SitterReviewTagsTest {

    @Test
    void atMostThreeHighlightsMayBePicked() {
        assertThat(SitterReview.MAX_TAGS).isEqualTo(3);
    }

    @Test
    void starsRunOneToFive() {
        assertThat(SitterReview.MIN_STARS).isEqualTo(1);
        assertThat(SitterReview.MAX_STARS).isEqualTo(5);
    }

    @Test
    void theAllowedTagsAreDistinctAndNonEmpty() {
        assertThat(SitterReview.ALLOWED_TAGS)
                .doesNotHaveDuplicates()
                .allSatisfy(tag -> assertThat(tag).isNotBlank());
    }

    @Test
    void thereAreEnoughTagsToBeWorthPicking() {
        // Fewer than a handful and the chips say less than a sentence would.
        assertThat(SitterReview.ALLOWED_TAGS.size()).isGreaterThanOrEqualTo(SitterReview.MAX_TAGS * 2);
    }

    @Test
    void storedTagsRoundTripThroughTheJoinedColumn() {
        SitterReview review = new SitterReview();
        review.setTags("Punctual||Great communication");
        assertThat(review.tagList()).containsExactly("Punctual", "Great communication");
    }

    @Test
    void noTagsReadsAsAnEmptyListRatherThanNull() {
        assertThat(new SitterReview().tagList()).isEmpty();
    }

    @Test
    void aReviewIsVisibleUntilItIsReported() {
        SitterReview review = new SitterReview();
        assertThat(review.isHidden()).isFalse();
        review.setHiddenAt(java.time.Instant.now());
        assertThat(review.isHidden()).isTrue();
    }
}
