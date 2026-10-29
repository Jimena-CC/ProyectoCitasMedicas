package com.medicitas.api.report;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Escritor de Excel ({@code .xlsx}) para los reportes, con Apache POI: una sola hoja, cabecera en negrita con
 * fondo gris, fila de cabecera fija, números como números (las tasas con cuatro decimales) y columnas con un
 * ancho acorde a su contenido.
 *
 * <p>Seguridad: todo texto se escribe como celda de texto y nunca como fórmula, así que un valor que empiece con
 * {@code =}, {@code +}, {@code -} o {@code @} no se ejecuta al abrir el archivo (inyección de fórmulas). Cada
 * celda se recorta al máximo que admite Excel para que un texto desmesurado no rompa la generación.
 *
 * <p>El ancho de columna se calcula a partir del texto, sin {@code autoSizeColumn}: este usa las fuentes del
 * sistema (AWT) y falla en imágenes mínimas de Java como la del contenedor de la API.
 */
final class XlsxWriter implements TabularWriter {

    /** Máximo de caracteres que admite una celda de Excel. */
    static final int MAX_CELL_LENGTH = 32_767;

    private static final int MIN_COLUMN_CHARS = 8;
    private static final int MAX_COLUMN_CHARS = 60;
    /** Unidad del ancho de columna de POI: 1/256 del ancho de un carácter. */
    private static final int COLUMN_UNIT = 256;
    private static final String RATE_FORMAT = "0.0000";

    private final String sheetName;
    private final List<Object[]> rows = new ArrayList<>();

    /**
     * Crea un escritor para una hoja.
     *
     * @param sheetName título de la hoja; se ajusta a las reglas de Excel (31 caracteres, sin {@code : \ / ? * [ ]})
     * @throws IllegalArgumentException si el título está en blanco
     */
    XlsxWriter(String sheetName) {
        Validate.notBlank(sheetName, "La hoja necesita un título.");
        this.sheetName = WorkbookUtil.createSafeSheetName(sheetName.trim());
    }

    /**
     * Agrega una fila; los valores nulos quedan como celdas vacías.
     *
     * @param values celdas de la fila
     * @return este escritor, para encadenar
     */
    @Override
    public XlsxWriter row(Object... values) {
        Preconditions.checkArgument(values != null && values.length > 0, "Una fila necesita al menos una celda.");
        rows.add(values.clone());
        return this;
    }

    /**
     * Genera el libro de Excel con las filas recibidas; la primera es la cabecera.
     *
     * @return los bytes del archivo {@code .xlsx}
     * @throws IllegalStateException si no se agregó ninguna fila
     * @throws UncheckedIOException  si POI no puede escribir el libro
     */
    @Override
    public byte[] toBytes() {
        Preconditions.checkState(!rows.isEmpty(), "El reporte no tiene filas.");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle rateStyle = workbook.createCellStyle();
            rateStyle.setDataFormat(workbook.createDataFormat().getFormat(RATE_FORMAT));

            int columns = rows.stream().mapToInt(row -> row.length).max().orElse(1);
            int[] widths = new int[columns];
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Object[] values = rows.get(rowIndex);
                Row row = sheet.createRow(rowIndex);
                for (int column = 0; column < values.length; column++) {
                    Cell cell = row.createCell(column);
                    widths[column] = Math.max(widths[column], write(cell, values[column], rateStyle));
                    if (rowIndex == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }
            for (int column = 0; column < columns; column++) {
                int chars = Math.min(Math.max(widths[column] + 2, MIN_COLUMN_CHARS), MAX_COLUMN_CHARS);
                sheet.setColumnWidth(column, chars * COLUMN_UNIT);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel del reporte.", e);
        }
    }

    /**
     * Escribe un valor en la celda.
     *
     * @return la cantidad de caracteres que ocupa, para calcular el ancho de la columna
     */
    private static int write(Cell cell, Object value, CellStyle rateStyle) {
        if (value == null) {
            cell.setBlank();
            return 0;
        }
        if (value instanceof Double rate) {
            cell.setCellValue(rate);
            cell.setCellStyle(rateStyle);
            return RATE_FORMAT.length();
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return String.valueOf(number).length();
        }
        String text = StringUtils.abbreviate(value.toString(), MAX_CELL_LENGTH);
        cell.setCellValue(text);
        return text.length();
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
