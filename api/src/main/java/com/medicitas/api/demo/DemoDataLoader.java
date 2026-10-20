package com.medicitas.api.demo;

import com.medicitas.api.appointment.Appointment;
import com.medicitas.api.appointment.AppointmentCodeGenerator;
import com.medicitas.api.appointment.AppointmentEvent;
import com.medicitas.api.appointment.AppointmentHistory;
import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.appointment.AppointmentStatus;
import com.medicitas.api.appointment.BookingChannel;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.InsurancePlan;
import com.medicitas.api.catalog.InsurancePlanRepository;
import com.medicitas.api.catalog.Insurer;
import com.medicitas.api.catalog.InsurerRepository;
import com.medicitas.api.catalog.Location;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.LocationSpecialty;
import com.medicitas.api.catalog.LocationSpecialtyRepository;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.notification.Notification;
import com.medicitas.api.notification.NotificationChannel;
import com.medicitas.api.notification.NotificationRepository;
import com.medicitas.api.notification.NotificationType;
import com.medicitas.api.patient.CoverageStatus;
import com.medicitas.api.patient.DocumentType;
import com.medicitas.api.patient.Patient;
import com.medicitas.api.patient.PatientInsurance;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.Slot;
import com.medicitas.api.schedule.SlotRepository;
import com.medicitas.api.schedule.SlotStatus;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import com.medicitas.api.security.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * DATOS DE DEMOSTRACIÓN. Carga un escenario determinista para presentar el kiosko sin depender de datos reales:
 * las dos sedes de la clínica, 12 especialidades, médicos y pacientes ficticios, aseguradoras con planes y
 * copagos inventados, y 14 días de agenda (lunes a sábado, 07:00 a 13:00, cupos de 20 minutos) con cerca del
 * 30 % de cupos ya ocupados. Los nombres, CMP, RUC y copagos no corresponden a personas ni contratos reales.
 * También crea los usuarios del personal: {@code admin}, {@code admision} y un {@code dr.<apellido>} por médico
 * (credenciales en api/README.md).
 */
@Component
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataLoader.class);

    private static final LocalTime FIRST_HOUR = LocalTime.of(7, 0);
    private static final int SLOTS_PER_DAY = 18;
    private static final int MINUTES_PER_SLOT = 20;
    private static final int SCHEDULE_DAYS = 14;
    private static final int FILLER_PATIENTS = 40;

    static final String DNI_LUCIA = "45871236";
    static final String DNI_DIEGO = "70123456";
    static final String LUCIA_APPOINTMENT_CODE = "MCA-7K2Q9";

    /** Usuarios de demostración por perfil; las contraseñas son públicas y solo valen para la demo. */
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";
    public static final String FRONT_DESK_USERNAME = "admision";
    public static final String FRONT_DESK_PASSWORD = "admision123";
    public static final String DOCTOR_USERNAME_PREFIX = "dr.";
    public static final String DOCTOR_PASSWORD = "doctor123";

    private static final String[][] SPECIALTIES = {
            {"Cardiología", "Evaluación y control del corazón, la presión arterial y el ritmo cardiaco."},
            {"Dermatología", "Diagnóstico y tratamiento de enfermedades de la piel, el cabello y las uñas."},
            {"Endocrinología", "Control de diabetes, tiroides y otros trastornos hormonales."},
            {"Gastroenterología", "Atención de molestias digestivas y enfermedades del hígado y el páncreas."},
            {"Ginecología y Obstetricia", "Salud de la mujer, control prenatal y planificación familiar."},
            {"Medicina Interna", "Evaluación integral del adulto y control de enfermedades crónicas."},
            {"Neurología", "Dolores de cabeza, epilepsia y trastornos del sistema nervioso."},
            {"Oftalmología", "Control de la visión y enfermedades de los ojos."},
            {"Otorrinolaringología", "Afecciones de oído, nariz y garganta."},
            {"Pediatría", "Control de crecimiento y atención de niños y adolescentes."},
            {"Traumatología y Ortopedia", "Lesiones de huesos, articulaciones, músculos y columna."},
            {"Urología", "Salud del sistema urinario y del aparato reproductor masculino."}
    };

    /** Índices de {@link #SPECIALTIES} que también se atienden en La Molina. */
    private static final int[] LA_MOLINA_SPECIALTIES = {0, 1, 3, 4, 5, 7, 9, 10};

    private record DemoDoctor(String firstNames, String lastNames, int specialty, boolean sanIsidro) {
    }

    private static final List<DemoDoctor> DOCTORS = List.of(
            new DemoDoctor("Carla", "Benavides Ortiz", 0, true),
            new DemoDoctor("Andrés", "Villanueva Castro", 1, true),
            new DemoDoctor("Rosa", "Huamán Delgado", 2, true),
            new DemoDoctor("Martín", "Salinas Prado", 3, true),
            new DemoDoctor("Patricia", "Chávez Olivera", 4, true),
            new DemoDoctor("Jorge", "Espinoza Vargas", 5, true),
            new DemoDoctor("Lorena", "Zapata Ríos", 6, true),
            new DemoDoctor("Ricardo", "Montoya Farfán", 7, true),
            new DemoDoctor("Silvia", "Cárdenas León", 8, true),
            new DemoDoctor("Gabriela", "Torres Meza", 9, true),
            new DemoDoctor("Luis Alberto", "Rojas Paz", 10, true),
            new DemoDoctor("Fernando", "Gálvez Arana", 11, true),
            new DemoDoctor("Óscar", "Palomino Reyes", 0, false),
            new DemoDoctor("Mónica", "Aguilar Soto", 1, false),
            new DemoDoctor("Daniel", "Quiroz Bernal", 3, false),
            new DemoDoctor("Verónica", "Lozano Cueva", 4, false),
            new DemoDoctor("Hugo", "Tapia Mendoza", 5, false),
            new DemoDoctor("Claudia", "Ramos Ugarte", 7, false),
            new DemoDoctor("Natalia", "Flores Cordero", 9, false),
            new DemoDoctor("Raúl", "Medina Arce", 10, false));

    private static final String[] FIRST_NAMES = {
            "Ana", "Bruno", "Camila", "Eduardo", "Elena", "Fabio", "Gloria", "Héctor", "Irene", "Julio",
            "Karina", "Leonardo", "Milagros", "Nicolás", "Olga", "Pablo", "Renata", "Samuel", "Tatiana", "Víctor"};
    private static final String[] LAST_NAMES = {
            "Alvarado", "Bustamante", "Cáceres", "Díaz", "Escobar", "Fernández", "Guerrero", "Herrera", "Ibáñez",
            "Jiménez", "Lazo", "Mendoza", "Núñez", "Ochoa", "Pinto", "Quispe", "Rivas", "Sánchez", "Távara", "Valdivia"};

    private final boolean enabled;
    private final LocationRepository locationRepository;
    private final SpecialtyRepository specialtyRepository;
    private final LocationSpecialtyRepository locationSpecialtyRepository;
    private final DoctorRepository doctorRepository;
    private final InsurerRepository insurerRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataLoader(@Value("${clinic.demo-data:false}") boolean enabled,
                          LocationRepository locationRepository, SpecialtyRepository specialtyRepository,
                          LocationSpecialtyRepository locationSpecialtyRepository, DoctorRepository doctorRepository,
                          InsurerRepository insurerRepository, InsurancePlanRepository insurancePlanRepository,
                          PatientRepository patientRepository, PatientInsuranceRepository patientInsuranceRepository,
                          SlotRepository slotRepository, AppointmentRepository appointmentRepository,
                          AppointmentHistoryRepository appointmentHistoryRepository,
                          NotificationRepository notificationRepository, AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.enabled = enabled;
        this.locationRepository = locationRepository;
        this.specialtyRepository = specialtyRepository;
        this.locationSpecialtyRepository = locationSpecialtyRepository;
        this.doctorRepository = doctorRepository;
        this.insurerRepository = insurerRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.patientRepository = patientRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Carga los datos de demostración si la base está vacía. Si la base ya tiene datos pero ningún usuario
     * (por ejemplo, un volumen de PostgreSQL creado antes de existir {@code app_user}), crea solo los usuarios
     * del personal para que alguien pueda iniciar sesión; el resto de datos no se toca.
     *
     * @param args argumentos de arranque (no se usan)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (locationRepository.count() > 0) {
            if (appUserRepository.count() == 0) {
                createUsers(doctorRepository.findAll());
                logger.info("La base ya contenía datos pero ningún usuario; se crearon los usuarios de demostración.");
            } else {
                logger.info("La base ya contiene datos; no se cargan datos de demostración.");
            }
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);

        Location sanIsidro = locationRepository.save(
                new Location("SI", "Sede San Isidro", "Av. Alfredo Salazar 350", "San Isidro", null));
        Location laMolina = locationRepository.save(
                new Location("LM", "Sede La Molina", "Av. La Fontana 362", "La Molina", null));

        List<Specialty> specialties = new ArrayList<>();
        for (String[] row : SPECIALTIES) {
            specialties.add(specialtyRepository.save(new Specialty(row[0], row[1], (short) MINUTES_PER_SLOT)));
        }
        for (Specialty specialty : specialties) {
            locationSpecialtyRepository.save(new LocationSpecialty(sanIsidro, specialty));
        }
        for (int index : LA_MOLINA_SPECIALTIES) {
            locationSpecialtyRepository.save(new LocationSpecialty(laMolina, specialties.get(index)));
        }

        List<Doctor> doctors = new ArrayList<>();
        for (int i = 0; i < DOCTORS.size(); i++) {
            DemoDoctor demo = DOCTORS.get(i);
            doctors.add(doctorRepository.save(new Doctor(String.format("%06d", 21400 + i * 1379),
                    String.format("%05d", 10200 + i * 317), demo.firstNames(), demo.lastNames(),
                    specialties.get(demo.specialty()))));
        }
        AppUser frontDesk = createUsers(doctors);

        // Aseguradoras con convenio mencionadas en la especificación; RUC, planes y copagos son ficticios.
        InsurancePlan redMedica = createInsurer("Rímac Seguros", "20999999001",
                "Red Preferente", "45.00", "Red Médica", "60.00").get(1);
        createInsurer("Pacífico Seguros", "20999999002", "Plan Esencial", "55.00", "Plan Integral", "35.00");
        createInsurer("La Positiva", "20999999003", "Plan Esencial", "50.00", "Plan Integral", "30.00");
        createInsurer("Mapfre", "20999999004", "Plan Esencial", "50.00", "Plan Integral", "40.00");

        Patient lucia = patientRepository.save(new Patient(DocumentType.DNI, DNI_LUCIA, "Lucía", "Paredes Quispe",
                LocalDate.of(1991, 4, 18), "F", "987654321", "lucia.paredes@correo.pe", true, now));
        patientRepository.save(new Patient(DocumentType.DNI, DNI_DIEGO, "Diego", "Salazar Rojas",
                LocalDate.of(1988, 11, 2), "M", "956112233", "diego.salazar@correo.pe", true, now));
        PatientInsurance luciaInsurance = patientInsuranceRepository.save(new PatientInsurance(lucia, redMedica,
                "RM-00458712", CoverageStatus.ACTIVE, today.plusYears(1)));
        List<Patient> fillers = createFillerPatients(now);

        List<LocalDate> days = today.datesUntil(today.plusDays(SCHEDULE_DAYS))
                .filter(date -> date.getDayOfWeek() != DayOfWeek.SUNDAY)
                .toList();
        LocalDate luciaAppointmentDay = days.stream()
                .filter(date -> !date.isBefore(today.plusDays(2))).findFirst().orElseThrow();
        int luciaSlotIndex = 6; // 09:00

        List<Slot> slots = new ArrayList<>();
        List<Integer> patientByTakenSlot = new ArrayList<>();
        Slot luciaSlot = null;
        for (int m = 0; m < doctors.size(); m++) {
            Location location = DOCTORS.get(m).sanIsidro() ? sanIsidro : laMolina;
            String room = "Consultorio " + (DOCTORS.get(m).sanIsidro() ? 201 + m : 101 + m);
            for (int d = 0; d < days.size(); d++) {
                for (int s = 0; s < SLOTS_PER_DAY; s++) {
                    LocalTime start = FIRST_HOUR.plusMinutes((long) s * MINUTES_PER_SLOT);
                    Slot slot = new Slot(doctors.get(m), location, days.get(d), start,
                            start.plusMinutes(MINUTES_PER_SLOT), room);
                    boolean isLuciaAppointment = m == 0 && days.get(d).equals(luciaAppointmentDay) && s == luciaSlotIndex;
                    if (isLuciaAppointment) {
                        slot.setStatus(SlotStatus.BOOKED);
                        luciaSlot = slot;
                        patientByTakenSlot.add(null);
                    } else if ((m * 31 + d * 17 + s * 7) % 10 < 3) {
                        slot.setStatus(SlotStatus.BOOKED);
                        // (7m + s) mod 40 asigna pacientes distintos a cupos con la misma hora: nunca hay cruces.
                        patientByTakenSlot.add((m * 7 + s) % FILLER_PATIENTS);
                    } else {
                        patientByTakenSlot.add(-1);
                    }
                    slots.add(slot);
                }
            }
        }
        slotRepository.saveAll(slots);

        Random generator = new Random(20260913L);
        Set<String> usedCodes = new HashSet<>(Set.of(LUCIA_APPOINTMENT_CODE));
        List<Appointment> appointments = new ArrayList<>();
        List<AppointmentHistory> history = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            Integer patientIndex = patientByTakenSlot.get(i);
            if (patientIndex == null || patientIndex < 0) {
                continue;
            }
            String code;
            do {
                code = AppointmentCodeGenerator.compose(generator);
            } while (!usedCodes.add(code));
            Appointment appointment = new Appointment(code, fillers.get(patientIndex), slots.get(i), null,
                    "Consulta programada por la central de citas", BookingChannel.FRONT_DESK,
                    UUID.nameUUIDFromBytes(("demo-slot-" + i).getBytes(StandardCharsets.UTF_8)), now);
            appointments.add(appointment);
            history.add(AppointmentHistory.record(appointment, AppointmentEvent.BOOKING, null,
                    AppointmentStatus.BOOKED, null, slots.get(i), frontDesk, null, now));
        }

        Appointment luciaAppointment = new Appointment(LUCIA_APPOINTMENT_CODE, lucia, luciaSlot, luciaInsurance,
                "Control de presión arterial", BookingChannel.KIOSK,
                UUID.nameUUIDFromBytes("demo-appointment-lucia".getBytes(StandardCharsets.UTF_8)), now);
        appointments.add(luciaAppointment);
        history.add(AppointmentHistory.record(luciaAppointment, AppointmentEvent.BOOKING, null,
                AppointmentStatus.BOOKED, null, luciaSlot, null, null, now));
        appointmentRepository.saveAll(appointments);
        appointmentHistoryRepository.saveAll(history);

        Notification confirmation = Notification.create(luciaAppointment, NotificationType.CONFIRMATION,
                NotificationChannel.EMAIL, lucia.getEmail(), now);
        confirmation.markSent(now, "SIM-DEMO0001");
        Notification reminder = Notification.create(luciaAppointment, NotificationType.REMINDER,
                NotificationChannel.SMS, lucia.getPhone(),
                luciaSlot.startAt(clock.getZone()).minus(Duration.ofHours(24)));
        notificationRepository.saveAll(List.of(confirmation, reminder));

        // Issue #46: citas pasadas ya cerradas para que los reportes de demostración tengan cifras
        createPastAppointmentsForReports(doctors, fillers, sanIsidro, laMolina, frontDesk, today, generator, usedCodes);

        logger.info("Datos de demostración cargados: 2 sedes, {} especialidades, {} médicos, {} cupos ({} ocupados). "
                        + "Pacientes demo: DNI {} (Rímac · Red Médica, cita {}) y DNI {} (particular). "
                        + "Usuarios: {} / {} (ADMIN), {} / {} (FRONT_DESK) y {}<apellido> / {} (DOCTOR).",
                specialties.size(), doctors.size(), slots.size(), appointments.size(), DNI_LUCIA,
                LUCIA_APPOINTMENT_CODE, DNI_DIEGO, ADMIN_USERNAME, ADMIN_PASSWORD, FRONT_DESK_USERNAME,
                FRONT_DESK_PASSWORD, DOCTOR_USERNAME_PREFIX, DOCTOR_PASSWORD);
    }

    /**
     * Crea los usuarios del personal: un administrador, uno de admisión y un médico por cada doctor,
     * con usuario {@code dr.<primer apellido sin tildes>}.
     *
     * @param doctors médicos de demostración ya guardados
     * @return el usuario de admisión, responsable de las citas de la central de citas
     */
    private AppUser createUsers(List<Doctor> doctors) {
        List<AppUser> users = new ArrayList<>();
        users.add(new AppUser(ADMIN_USERNAME, passwordEncoder.encode(ADMIN_PASSWORD), UserRole.ADMIN, null));
        AppUser frontDesk = new AppUser(FRONT_DESK_USERNAME, passwordEncoder.encode(FRONT_DESK_PASSWORD),
                UserRole.FRONT_DESK, null);
        users.add(frontDesk);
        String doctorHash = passwordEncoder.encode(DOCTOR_PASSWORD);
        for (Doctor doctor : doctors) {
            users.add(new AppUser(doctorUsername(doctor), doctorHash, UserRole.DOCTOR, doctor));
        }
        appUserRepository.saveAll(users);
        return frontDesk;
    }

    /**
     * Nombre de usuario de un médico: {@code dr.} más su primer apellido en minúsculas y sin tildes.
     *
     * @param doctor médico
     * @return el nombre de usuario, por ejemplo {@code dr.benavides}
     */
    public static String doctorUsername(Doctor doctor) {
        String firstLastName = doctor.getLastNames().trim().split("\\s+")[0];
        String plain = Normalizer.normalize(firstLastName, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return DOCTOR_USERNAME_PREFIX + plain.toLowerCase(Locale.ROOT);
    }

    private List<InsurancePlan> createInsurer(String name, String ruc, String plan1, String copay1,
                                              String plan2, String copay2) {
        Insurer insurer = insurerRepository.save(new Insurer(name, ruc, "SEGURO_PRIVADO"));
        return insurancePlanRepository.saveAll(List.of(
                new InsurancePlan(insurer, plan1, new BigDecimal(copay1)),
                new InsurancePlan(insurer, plan2, new BigDecimal(copay2))));
    }

    // Issue #46: agenda pasada con resultado para los reportes

    /** Posiciones en {@link #DOCTORS} con agenda pasada: cuatro médicos de San Isidro y dos de La Molina. */
    private static final int[] PAST_AGENDA_DOCTORS = {0, 1, 2, 3, 12, 13};
    private static final int PAST_AGENDA_DAYS = 14;
    private static final LocalTime PAST_FIRST_HOUR = LocalTime.of(8, 0);
    private static final int PAST_SLOTS_PER_DAY = 6;

    /**
     * Crea, para seis médicos y los 14 días anteriores a hoy (sin domingos), seis cupos diarios de 08:00 a 10:00
     * con citas ya cerradas en un patrón determinista: la mitad atendidas, una quinta parte no asistidas, una
     * décima parte anuladas y el resto cupos que quedaron libres. Así los reportes de citas, inasistencia y
     * ocupación muestran cifras distintas de cero en la demo. Las citas del futuro no se tocan.
     *
     * @param doctors   médicos de demostración ya guardados
     * @param fillers   pacientes de relleno
     * @param sanIsidro sede San Isidro
     * @param laMolina  sede La Molina
     * @param frontDesk usuario de admisión que figura como responsable de las reservas
     * @param today     fecha de hoy en la zona de la clínica
     * @param generator generador de códigos de cita
     * @param usedCodes códigos ya asignados, para no repetirlos
     */
    private void createPastAppointmentsForReports(List<Doctor> doctors, List<Patient> fillers, Location sanIsidro,
                                                  Location laMolina, AppUser frontDesk, LocalDate today,
                                                  Random generator, Set<String> usedCodes) {
        List<LocalDate> pastDays = today.minusDays(PAST_AGENDA_DAYS).datesUntil(today)
                .filter(date -> date.getDayOfWeek() != DayOfWeek.SUNDAY)
                .toList();
        List<Slot> slots = new ArrayList<>();
        List<Appointment> appointments = new ArrayList<>();
        List<AppointmentHistory> history = new ArrayList<>();
        int sequence = 0;
        for (int ordinal = 0; ordinal < PAST_AGENDA_DOCTORS.length; ordinal++) {
            int m = PAST_AGENDA_DOCTORS[ordinal];
            Doctor doctor = doctors.get(m);
            AppUser doctorUser = appUserRepository.findByUsernameIgnoreCase(doctorUsername(doctor)).orElse(null);
            Location location = DOCTORS.get(m).sanIsidro() ? sanIsidro : laMolina;
            String room = "Consultorio " + (DOCTORS.get(m).sanIsidro() ? 201 + m : 101 + m);
            for (int d = 0; d < pastDays.size(); d++) {
                for (int s = 0; s < PAST_SLOTS_PER_DAY; s++) {
                    LocalTime start = PAST_FIRST_HOUR.plusMinutes((long) s * MINUTES_PER_SLOT);
                    Slot slot = new Slot(doctor, location, pastDays.get(d), start, start.plusMinutes(MINUTES_PER_SLOT), room);
                    slots.add(slot);
                    int pattern = (m * 5 + d * 3 + s * 7) % 10;
                    AppointmentStatus outcome = pattern < 5 ? AppointmentStatus.ATTENDED
                            : pattern < 7 ? AppointmentStatus.NO_SHOW
                            : pattern == 7 ? AppointmentStatus.CANCELLED : null;
                    if (outcome == null) {
                        continue;
                    }
                    if (outcome != AppointmentStatus.CANCELLED) {
                        slot.setStatus(SlotStatus.BOOKED);
                    }
                    Instant bookedAt = pastDays.get(d).minusDays(3).atTime(10, 0).atZone(clock.getZone()).toInstant();
                    Instant closedAt = outcome == AppointmentStatus.CANCELLED
                            ? slot.startAt(clock.getZone()).minus(Duration.ofHours(26))
                            : slot.startAt(clock.getZone()).plus(Duration.ofMinutes(25));
                    String code;
                    do {
                        code = AppointmentCodeGenerator.compose(generator);
                    } while (!usedCodes.add(code));
                    // Un paciente distinto por médico en cada horario: nunca dos citas del mismo paciente a la misma hora.
                    Patient patient = fillers.get((ordinal + PAST_AGENDA_DOCTORS.length * (d * PAST_SLOTS_PER_DAY + s))
                            % FILLER_PATIENTS);
                    Appointment appointment = new Appointment(code, patient, slot, null,
                            "Consulta programada por la central de citas", BookingChannel.FRONT_DESK,
                            UUID.nameUUIDFromBytes(("demo-past-slot-" + sequence++).getBytes(StandardCharsets.UTF_8)),
                            bookedAt);
                    history.add(AppointmentHistory.record(appointment, AppointmentEvent.BOOKING, null,
                            AppointmentStatus.BOOKED, null, slot, frontDesk, null, bookedAt));
                    if (outcome == AppointmentStatus.CANCELLED) {
                        appointment.cancel(closedAt);
                        history.add(AppointmentHistory.record(appointment, AppointmentEvent.CANCELLATION,
                                AppointmentStatus.BOOKED, AppointmentStatus.CANCELLED, slot, null, frontDesk,
                                "El paciente avisó que no podrá asistir", closedAt));
                    } else {
                        appointment.registerOutcome(outcome, closedAt);
                        history.add(AppointmentHistory.record(appointment, AppointmentEvent.ATTENDANCE,
                                AppointmentStatus.BOOKED, outcome, slot, slot, doctorUser, null, closedAt));
                    }
                    appointments.add(appointment);
                }
            }
        }
        slotRepository.saveAll(slots);
        appointmentRepository.saveAll(appointments);
        appointmentHistoryRepository.saveAll(history);
        logger.info("Agenda pasada de demostración: {} cupos y {} citas cerradas de {} médicos en los últimos {} días.",
                slots.size(), appointments.size(), PAST_AGENDA_DOCTORS.length, PAST_AGENDA_DAYS);
    }

    private List<Patient> createFillerPatients(Instant now) {
        List<Patient> patients = new ArrayList<>();
        for (int i = 0; i < FILLER_PATIENTS; i++) {
            String dni = String.format("%08d", 60_000_000 + i * 7919);
            String lastNames = LAST_NAMES[i % LAST_NAMES.length] + " " + LAST_NAMES[(i * 7 + 3) % LAST_NAMES.length];
            patients.add(new Patient(DocumentType.DNI, dni, FIRST_NAMES[i % FIRST_NAMES.length], lastNames,
                    LocalDate.of(1960, 1, 1).plusDays(i * 211L), i % 2 == 0 ? "F" : "M",
                    String.format("9%08d", 10_000_000 + i * 7919), "paciente" + i + "@demo.medicitas.pe", true, now));
        }
        return patientRepository.saveAll(patients);
    }
}
