package com.medicitas.api.report;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Escritor CSV mínimo para los reportes: separador coma, saltos {@code \r\n}, UTF-8 con BOM para que Excel
 * reconozca las tildes, y comillas solo en los campos que las necesitan.
 */
final class CsvWriter {

    /** Marca de orden de bytes UTF-8 que Excel usa para detectar la codificación. */
    static final String BOM = "﻿";

    private static final String LINE_SEPARATOR = "\r\n";

    private final List<String> lines = new ArrayList<>();

    /**
     * Agrega una fila; los valores nulos se escriben vacíos y las tasas se formatean con cuatro decimales.
     *
     * @param values celdas de la fila
     * @return este escritor, para encadenar
     */
    CsvWriter row(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(format(values[i])));
        }
        lines.add(line.toString());
        return this;
    }

    /**
     * Contenido completo del archivo.
     *
     * @return los bytes UTF-8 con BOM y una línea por fila
     */
    byte[] toBytes() {
        return (BOM + String.join(LINE_SEPARATOR, lines) + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8);
    }

    private static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double rate) {
            return String.format(Locale.ROOT, "%.4f", rate);
        }
        return value.toString();
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
