package com.medicitas.api.common;

/**
 * Formatos JSON acordados en el contrato: horas {@code HH:mm} e instantes ISO-8601 con zona {@code -05:00}.
 */
public final class Formats {

    public static final String TIME = "HH:mm";
    public static final String DATE_TIME = "yyyy-MM-dd'T'HH:mm:ssXXX";

    private Formats() {
    }
}
