package com.medicitas.api.report;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("XlsxWriter (Apache POI)")
class XlsxWriterTest {

    private static XSSFWorkbook open(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("Escribe la cabecera en negrita, el texto como texto, los enteros como números y las tasas con 4 decimales")
    void escribeCabeceraTextoNumerosYTasas() throws IOException {
        byte[] file = new XlsxWriter("Citas")
                .row("grupo", "nombre", "total")
                .row("Especialidad", "Cardiología", 6L)
                .row("Total", "Todas", 0.25)
                .toBytes();

        try (XSSFWorkbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(workbook.getSheetName(0)).isEqualTo("Citas");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("grupo");
            assertThat(((XSSFCellStyle) sheet.getRow(0).getCell(0).getCellStyle()).getFont().getBold()).isTrue();
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Cardiología");
            assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(6.0);
            assertThat(sheet.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(0.25);
            assertThat(sheet.getRow(2).getCell(2).getCellStyle().getDataFormatString()).isEqualTo("0.0000");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    @DisplayName("Un texto que parece fórmula se guarda como texto y nunca como fórmula")
    void noInterpretaFormulas() throws IOException {
        String malicious = "=HYPERLINK(\"http://ejemplo.invalid\",\"clic\")";

        byte[] file = new XlsxWriter("Citas").row("nombre").row(malicious).row("+1+1").row("@SUM(A1)").toBytes();

        try (XSSFWorkbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(malicious);
            assertThat(sheet.getRow(2).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(3).getCell(0).getCellType()).isEqualTo(CellType.STRING);
        }
    }

    @Test
    @DisplayName("Los valores nulos quedan como celdas vacías")
    void nulosQuedanVacios() throws IOException {
        byte[] file = new XlsxWriter("Citas").row("a", "b").row("x", null).toBytes();

        try (XSSFWorkbook workbook = open(file)) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    @DisplayName("Recorta los textos al máximo que admite una celda de Excel")
    void recortaTextosDemasiadoLargos() throws IOException {
        String huge = "a".repeat(XlsxWriter.MAX_CELL_LENGTH + 500);

        byte[] file = new XlsxWriter("Citas").row("texto").row(huge).toBytes();

        try (XSSFWorkbook workbook = open(file)) {
            String stored = workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue();
            assertThat(stored).hasSize(XlsxWriter.MAX_CELL_LENGTH);
        }
    }

    @Test
    @DisplayName("Ajusta el título de la hoja a las reglas de Excel")
    void ajustaElTituloDeLaHoja() throws IOException {
        byte[] file = new XlsxWriter("Ocupación: sede/1 [prueba] con un título larguísimo").row("a").toBytes();

        try (XSSFWorkbook workbook = open(file)) {
            String name = workbook.getSheetName(0);
            assertThat(name).hasSizeLessThanOrEqualTo(31);
            assertThat(name).isNotBlank();
            assertThat(name).doesNotContain(":", "/", "[", "]", "?", "*", "\\");
        }
    }

    @Test
    @DisplayName("El ancho de columna queda entre 8 y 60 caracteres")
    void acotaElAnchoDeColumna() throws IOException {
        byte[] file = new XlsxWriter("Citas").row("a", "b").row("x", "y".repeat(500)).toBytes();

        try (XSSFWorkbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getColumnWidth(0)).isEqualTo(8 * 256);
            assertThat(sheet.getColumnWidth(1)).isEqualTo(60 * 256);
        }
    }

    @Test
    @DisplayName("Rechaza un libro sin filas, una hoja sin título y una fila sin celdas")
    void validaSusArgumentos() {
        assertThatThrownBy(() -> new XlsxWriter("Citas").toBytes()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new XlsxWriter("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new XlsxWriter("Citas").row()).isInstanceOf(IllegalArgumentException.class);
    }
}
