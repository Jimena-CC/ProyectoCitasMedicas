package com.medicitas.kiosk.screens;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Textos de fechas y horas en español peruano. Los nombres se fijan aquí para no depender
 * de los datos de localización del JDK (que agregan puntos o mayúsculas según la versión).
 */
public final class Dates {

    private static final String[] SHORT_DAYS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
    private static final String[] DAYS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Dates() {
    }

    /** Etiqueta superior de la franja de fechas: "Hoy", "Mañana" o el día abreviado ("lun"). */
    public static String dayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "Hoy";
        }
        if (date.equals(today.plusDays(1))) {
            return "Mañana";
        }
        return SHORT_DAYS[date.getDayOfWeek().getValue() - 1];
    }

    /** Etiqueta compacta de un día: "Hoy", "Mañana" o "lun 14". */
    public static String compactLabel(LocalDate date, LocalDate today) {
        String label = dayLabel(date, today);
        return label.equals("Hoy") || label.equals("Mañana")
                ? label : label + " " + date.getDayOfMonth();
    }

    /** "Septiembre 2026". */
    public static String monthYear(LocalDate date) {
        return capitalize(MONTHS[date.getMonthValue() - 1]) + " " + date.getYear();
    }

    /** "Lunes 14 de septiembre". */
    public static String longDate(LocalDate date) {
        return capitalize(DAYS[date.getDayOfWeek().getValue() - 1]) + " " + date.getDayOfMonth()
                + " de " + MONTHS[date.getMonthValue() - 1];
    }

    public static String time(LocalTime time) {
        return time == null ? "" : TIME.format(time);
    }

    /** "07:15 – 07:35". */
    public static String range(LocalTime start, LocalTime end) {
        return time(start) + " – " + time(end);
    }

    static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
