package com.medicitas.api.report;

/**
 * Escritor de tablas de los reportes: {@link CsvWriter} y {@link XlsxWriter} reciben exactamente las mismas filas,
 * de modo que el CSV y el Excel de un reporte llevan siempre las mismas cifras.
 */
interface TabularWriter {

    /**
     * Agrega una fila; la primera es la cabecera.
     *
     * @param values celdas de la fila (texto, números o {@code null})
     * @return este escritor, para encadenar
     */
    TabularWriter row(Object... values);

    /**
     * Contenido completo del archivo.
     *
     * @return los bytes del archivo ya formateado
     */
    byte[] toBytes();
}
