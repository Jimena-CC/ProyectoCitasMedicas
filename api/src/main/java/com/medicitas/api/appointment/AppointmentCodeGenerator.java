package com.medicitas.api.appointment;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Genera códigos legibles como {@code MCA-7K2Q9}. Omite 0/O, 1/I/L para que el paciente
 * pueda dictarlos por teléfono sin confusiones.
 */
@Component
public class AppointmentCodeGenerator {

    static final String PREFIX = "MCA-";
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 5;

    private final SecureRandom random = new SecureRandom();
    private final AppointmentRepository appointmentRepository;

    public AppointmentCodeGenerator(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public String generate() {
        String code;
        do {
            code = compose(random);
        } while (appointmentRepository.existsByCode(code));
        return code;
    }

    public static String compose(RandomGenerator generator) {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(generator.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
