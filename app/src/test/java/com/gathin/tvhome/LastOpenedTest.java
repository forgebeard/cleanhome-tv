package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for the "Last opened" line: how the day and the time are joined. */
public class LastOpenedTest {

    @Test
    public void russianJoinsTheDayAndTheTimeWithV() {
        assertEquals("вчера в 21:10", LastOpened.joinDayAndTime("вчера", "21:10", "ru"));
        assertEquals("3 дня назад в 21:40", LastOpened.joinDayAndTime("3 дня назад", "21:40", "ru"));
    }

    @Test
    public void otherLanguagesKeepTheCommaThatWasAlwaysThere() {
        assertEquals("yesterday, 9:40 pm", LastOpened.joinDayAndTime("yesterday", "9:40 pm", "en"));
        assertEquals("gestern, 21:10", LastOpened.joinDayAndTime("gestern", "21:10", "de"));
    }
}
