package io.github.rkaushikdev.garterhole.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StrokeTest {

    @Test
    void alternatesFromTheStrokeOfTheFirstRow() {
        assertThat(Stroke.at(0, Stroke.BACKSTROKE)).isEqualTo(Stroke.BACKSTROKE);
        assertThat(Stroke.at(1, Stroke.BACKSTROKE)).isEqualTo(Stroke.HANDSTROKE);
        assertThat(Stroke.at(6, Stroke.HANDSTROKE)).isEqualTo(Stroke.HANDSTROKE);
        assertThat(Stroke.at(7, Stroke.HANDSTROKE)).isEqualTo(Stroke.BACKSTROKE);
    }

    @Test
    void namesTheOtherStroke() {
        assertThat(Stroke.HANDSTROKE.other()).isEqualTo(Stroke.BACKSTROKE);
        assertThat(Stroke.BACKSTROKE.other()).isEqualTo(Stroke.HANDSTROKE);
    }

    @Test
    void writesItselfTheWayARingerDoes() {
        assertThat(Stroke.BACKSTROKE).hasToString("backstroke");
    }

    @Test
    void refusesARowBeforeTheFirst() {
        assertThatThrownBy(() -> Stroke.at(-1, Stroke.HANDSTROKE)).hasMessageContaining("before the first");
    }
}
