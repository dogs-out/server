package com.dogsout.server.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which reports carry the reported photos.
 *
 * <p>Two rules pulling against each other: a report nobody can act on is no use,
 * and nobody's pictures belong in an inbox without a reason. This is where the
 * line sits, so a new reason cannot quietly move it.
 */
class PhotoReasonTest {

    private static boolean wants(String reason) {
        return Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(ModerationService.class, "wantsPhotos", reason));
    }

    @Test
    void picturesComeWithAReportAboutPictures() {
        assertThat(wants("Inappropriate photos")).isTrue();
    }

    @Test
    void picturesComeWithAFakeProfileReport() {
        // A stolen or stock photograph is how that judgement is made; without
        // them the report cannot be acted on at all.
        assertThat(wants("Fake profile")).isTrue();
    }

    @Test
    void nothingElseCarriesThem() {
        assertThat(wants("Inappropriate name or bio")).isFalse();
        assertThat(wants("Harassment or bullying")).isFalse();
        assertThat(wants("Spam or scam")).isFalse();
        assertThat(wants("Safety concern")).isFalse();
        assertThat(wants("Other")).isFalse();
        assertThat(wants("Inappropriate messages")).isFalse();
    }

    @Test
    void anAbsentOrOddReasonCarriesNothing() {
        assertThat(wants(null)).isFalse();
        assertThat(wants("")).isFalse();
        assertThat(wants("   ")).isFalse();
    }

    @Test
    void caseAndSpacingDoNotChangeTheAnswer() {
        assertThat(wants("  FAKE PROFILE ")).isTrue();
        assertThat(wants("inappropriate photos")).isTrue();
    }

    @Test
    void aFutureReasonAboutPicturesIsCoveredToo() {
        // The net, so adding "Inappropriate photos or video" on the client does
        // not silently drop the attachments.
        assertThat(wants("Inappropriate photos or video")).isTrue();
    }
}
