package com.medicitas.api.demo;

import com.medicitas.api.appointment.AppointmentHistoryRepository;
import com.medicitas.api.appointment.AppointmentRepository;
import com.medicitas.api.catalog.Doctor;
import com.medicitas.api.catalog.DoctorRepository;
import com.medicitas.api.catalog.InsurancePlanRepository;
import com.medicitas.api.catalog.InsurerRepository;
import com.medicitas.api.catalog.LocationRepository;
import com.medicitas.api.catalog.LocationSpecialtyRepository;
import com.medicitas.api.catalog.Specialty;
import com.medicitas.api.catalog.SpecialtyRepository;
import com.medicitas.api.notification.NotificationRepository;
import com.medicitas.api.patient.PatientInsuranceRepository;
import com.medicitas.api.patient.PatientRepository;
import com.medicitas.api.schedule.SlotRepository;
import com.medicitas.api.security.AppUser;
import com.medicitas.api.security.AppUserRepository;
import com.medicitas.api.security.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Decisiones de carga de {@link DemoDataLoader} según el estado de la base; la carga completa se cubre
 * en las pruebas de integración (H2) y en {@code PostgresSchemaTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemoDataLoader")
class DemoDataLoaderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T15:00:00Z"), ZoneId.of("America/Lima"));

    @Mock
    private LocationRepository locationRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private LocationSpecialtyRepository locationSpecialtyRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private InsurerRepository insurerRepository;
    @Mock
    private InsurancePlanRepository insurancePlanRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientInsuranceRepository patientInsuranceRepository;
    @Mock
    private SlotRepository slotRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<List<AppUser>> savedUsers;

    private DemoDataLoader loader(boolean enabled) {
        return new DemoDataLoader(enabled, locationRepository, specialtyRepository, locationSpecialtyRepository,
                doctorRepository, insurerRepository, insurancePlanRepository, patientRepository,
                patientInsuranceRepository, slotRepository, appointmentRepository, appointmentHistoryRepository,
                notificationRepository, appUserRepository, passwordEncoder, CLOCK);
    }

    @Test
    @DisplayName("Con clinic.demo-data=false no consulta ni escribe nada")
    void desactivadoNoTocaLaBase() {
        loader(false).run(new DefaultApplicationArguments());

        verifyNoInteractions(locationRepository, appUserRepository, doctorRepository);
    }

    @Nested
    @DisplayName("Con una base que ya tiene datos")
    class BaseConDatos {

        @Test
        @DisplayName("Si ya existen usuarios no vuelve a cargar nada")
        void conUsuariosNoCargaNada() {
            when(locationRepository.count()).thenReturn(2L);
            when(appUserRepository.count()).thenReturn(5L);

            loader(true).run(new DefaultApplicationArguments());

            verify(appUserRepository, never()).saveAll(savedUsers.capture());
            verify(locationRepository, never()).save(any());
            verifyNoInteractions(doctorRepository);
        }

        @Test
        @DisplayName("Sin usuarios crea admin, admisión y un médico por cada doctor existente, sin tocar el resto")
        void sinUsuariosCreaSoloLosUsuarios() {
            Specialty cardiologia = new Specialty("Cardiología", null, (short) 20);
            Doctor benavides = new Doctor("045712", null, "Carla", "Benavides Ortiz", cardiologia);
            Doctor galvez = new Doctor("045713", null, "Luis", "Gálvez Torres", cardiologia);
            when(locationRepository.count()).thenReturn(2L);
            when(appUserRepository.count()).thenReturn(0L);
            when(doctorRepository.findAll()).thenReturn(List.of(benavides, galvez));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");

            loader(true).run(new DefaultApplicationArguments());

            verify(appUserRepository).saveAll(savedUsers.capture());
            List<AppUser> users = savedUsers.getValue();
            assertAll(
                    () -> assertThat(users).extracting(AppUser::getUsername)
                            .containsExactly(DemoDataLoader.ADMIN_USERNAME, DemoDataLoader.FRONT_DESK_USERNAME,
                                    "dr.benavides", "dr.galvez"),
                    () -> assertThat(users).extracting(AppUser::getRole)
                            .containsExactly(UserRole.ADMIN, UserRole.FRONT_DESK, UserRole.DOCTOR, UserRole.DOCTOR),
                    () -> assertThat(users.get(2).getDoctor()).isSameAs(benavides),
                    () -> assertThat(users.get(3).getDoctor()).isSameAs(galvez),
                    () -> assertThat(users).allSatisfy(user -> assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hash")));
            verify(locationRepository, never()).save(any());
            verifyNoInteractions(specialtyRepository, patientRepository, slotRepository, appointmentRepository);
        }
    }
}
