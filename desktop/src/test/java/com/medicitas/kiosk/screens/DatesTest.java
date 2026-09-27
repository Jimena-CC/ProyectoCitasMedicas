package com.medicitas.kiosk.screens;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13); // domingo

    @Test
    void labelsTodayAndTomorrow() {
        assertEquals("Hoy", Dates.dayLabel(TODAY, TODAY));
        assertEquals("Mañana", Dates.dayLabel(TODAY.plusDays(1), TODAY));
    }

    @Test
    void labelsShortDayInSpanish() {
        assertEquals("mar", Dates.dayLabel(TODAY.plusDays(2), TODAY));
        assertEquals("mié", Dates.dayLabel(TODAY.plusDays(3), TODAY));
        assertEquals("sáb", Dates.dayLabel(TODAY.plusDays(6), TODAY));
    }

    @Test
    void compactLabelIncludesDayNumber() {
        assertEquals("Hoy", Dates.compactLabel(TODAY, TODAY));
        assertEquals("Mañana", Dates.compactLabel(TODAY.plusDays(1), TODAY));
        assertEquals("lun 21", Dates.compactLabel(LocalDate.of(2026, 9, 21), TODAY));
    }

    @Test
    void longTexts() {
        assertEquals("Lunes 14 de septiembre", Dates.longDate(LocalDate.of(2026, 9, 14)));
        assertEquals("Septiembre 2026", Dates.monthYear(TODAY));
        assertEquals("07:15 – 07:35", Dates.range(LocalTime.of(7, 15), LocalTime.of(7, 35)));
    }
}
