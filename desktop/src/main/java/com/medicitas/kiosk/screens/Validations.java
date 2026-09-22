package com.medicitas.kiosk.screens;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reglas de validación del formulario del kiosko, alineadas con docs/api-contract.md.
 * Cada método devuelve el mensaje de error listo para mostrar, o vacío si el valor es válido.
 */
public final class Validations {

    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern CE = Pattern.compile("[A-Za-z0-9]{9,12}");
    private static final Pattern PAS = Pattern.compile("[A-Za-z0-9]{6,12}");
    private static final Pattern PHONE = Pattern.compile("9\\d{8}");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} '.-]{1,99}$");

    private Validations() {
    }

    public static Optional<String> document(String type, String number) {
        String value = number == null ? "" : number.trim();
        if (value.isEmpty()) {
            return Optional.of("Ingresa tu número de documento.");
        }
        return switch (type == null ? "" : type) {
            case "DNI" -> DNI.matcher(value).matches()
                    ? Optional.empty() : Optional.of("El DNI debe tener 8 dígitos.");
            case "CE" -> CE.matcher(value).matches()
                    ? Optional.empty() : Optional.of("El carné de extranjería debe tener entre 9 y 12 caracteres.");
            case "PAS" -> PAS.matcher(value).matches()
                    ? Optional.empty() : Optional.of("El pasaporte debe tener entre 6 y 12 caracteres.");
            default -> Optional.of("Elige un tipo de documento.");
        };
    }

    /** Longitud máxima que acepta el teclado para cada tipo de documento. */
    public static int maxLength(String type) {
        return "DNI".equals(type) ? 8 : 12;
    }

    public static Optional<String> name(String value, String field) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return Optional.of("Ingresa tus " + field + ".");
        }
        return NAME.matcher(v).matches()
                ? Optional.empty() : Optional.of("Revisa tus " + field + ": usa solo letras y espacios.");
    }

    public static Optional<String> phone(String value) {
        String v = value == null ? "" : value.replace(" ", "");
        if (v.isEmpty()) {
            return Optional.of("Ingresa tu número de celular.");
        }
        return PHONE.matcher(v).matches()
                ? Optional.empty() : Optional.of("El celular debe tener 9 dígitos y empezar con 9.");
    }

    public static Optional<String> email(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return Optional.of("Ingresa tu correo electrónico.");
        }
        return EMAIL.matcher(v).matches()
                ? Optional.empty() : Optional.of("Revisa tu correo, por ejemplo: nombre@correo.pe");
    }

    public static Optional<String> birthDate(LocalDate date, LocalDate today) {
        if (date == null) {
            return Optional.of("Ingresa tu fecha de nacimiento.");
        }
        if (!date.isBefore(today)) {
            return Optional.of("La fecha de nacimiento debe ser anterior a hoy.");
        }
        if (date.isBefore(today.minusYears(120))) {
            return Optional.of("Revisa el año de tu fecha de nacimiento.");
        }
        return Optional.empty();
    }

    public static Optional<String> visitReason(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() < 5) {
            return Optional.of("Cuéntanos brevemente el motivo de tu consulta.");
        }
        return v.length() > 250
                ? Optional.of("El motivo puede tener hasta 250 caracteres.") : Optional.empty();
    }
}
