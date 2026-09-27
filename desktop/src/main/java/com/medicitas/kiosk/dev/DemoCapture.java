package com.medicitas.kiosk.dev;

import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.screens.BookingSession;
import com.medicitas.kiosk.screens.BookingSession.Step;
import com.medicitas.kiosk.screens.KioskShell;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Recorre las pantallas del kiosko y guarda cada una como PNG. Solo para revisar el diseño
 * durante el desarrollo: se activa con {@code --capture=<carpeta>} junto a {@code --demo-stub}.
 *
 * <p>Escribe el PNG directamente para no agregar dependencias de imagen al proyecto.</p>
 */
public final class DemoCapture {

    private static final Logger LOG = Logger.getLogger(DemoCapture.class.getName());

    private DemoCapture() {
    }

    /** Lo invoca {@code KioskApp} por reflexión. */
    public static void schedule(KioskShell shell, Scene scene, String folder) {
        Path target = Path.of(folder);
        shell.api().locations().thenCombine(shell.api().specialties(1L), Data::new)
                .thenCombine(shell.api().findPatient("DNI", "45871236"), Data::withPatient)
                .thenCombine(shell.api().availability(1L, 1L, LocalDate.now(), LocalDate.now().plusDays(13)),
                        Data::withAvailability)
                .thenAccept(data -> Platform.runLater(() -> walk(shell, scene, target, data)))
                .exceptionally(error -> {
                    LOG.log(Level.WARNING, "No se pudo preparar la captura", error);
                    Platform.runLater(Platform::exit);
                    return null;
                });
    }

    private static void walk(KioskShell shell, Scene scene, Path target, Data data) {
        BookingSession session = shell.session();
        List<Stage> stages = new ArrayList<>();

        stages.add(new Stage(1200, () -> { }, "1-identification"));
        stages.add(new Stage(900, () -> {
            session.documentEntered("DNI", "45871236");
            session.patientFound(data.patient);
            session.advance();
            shell.showStep(Step.PERSONAL_DATA_AND_INSURANCE);
        }, "2-personal-data-and-insurance"));
        stages.add(new Stage(1000, () -> {
            session.insuranceUpdated(data.patient);
            session.advance();
            shell.showStep(Step.SPECIALTY_AND_LOCATION);
        }, "3-specialty-and-location"));
        stages.add(new Stage(1600, () -> {
            session.selectLocation(data.locations.get(0));
            session.selectSpecialty(data.specialties.get(0));
            session.advance();
            shell.showStep(Step.DATE_AND_TIME);
        }, "4-date-and-time"));
        stages.add(new Stage(1000, () -> {
            data.firstSlot().ifPresent(day -> session.selectSlot(day.date(), day.slots().get(0)));
            session.setVisitReason("Control de presión arterial");
            session.advance();
            shell.showStep(Step.CONFIRMATION);
        }, "5-confirmation"));
        stages.add(new Stage(1400, shell::showMyAppointments, "6-my-appointments"));

        chain(scene, target, stages, 0);
    }

    private static void chain(Scene scene, Path target, List<Stage> stages, int index) {
        if (index >= stages.size()) {
            Platform.exit();
            return;
        }
        Stage stage = stages.get(index);
        stage.action.run();
        PauseTransition wait = new PauseTransition(Duration.millis(stage.waitMs));
        wait.setOnFinished(event -> {
            capture(scene, target, stage.name);
            chain(scene, target, stages, index + 1);
        });
        wait.play();
    }

    private static void capture(Scene scene, Path target, String name) {
        try {
            Files.createDirectories(target);
            WritableImage image = scene.snapshot(null);
            writePng(image, target.resolve(name + ".png"));
            LOG.info(() -> "Captura guardada: " + name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writePng(WritableImage image, Path target) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();

        ByteArrayOutputStream raw = new ByteArrayOutputStream(height * (1 + width * 3));
        for (int y = 0; y < height; y++) {
            raw.write(0);
            for (int x = 0; x < width; x++) {
                int argb = pixels.getArgb(x, y);
                raw.write((argb >> 16) & 0xFF);
                raw.write((argb >> 8) & 0xFF);
                raw.write(argb & 0xFF);
            }
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(8);    // bits por canal
        ihdr.write(2);    // color verdadero RGB
        ihdr.write(0);    // compresión
        ihdr.write(0);    // filtro
        ihdr.write(0);    // sin entrelazado
        chunk(png, "IHDR", ihdr.toByteArray());
        chunk(png, "IDAT", compress(raw.toByteArray()));
        chunk(png, "IEND", new byte[0]);

        Files.write(target, png.toByteArray());
    }

    private static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4);
        byte[] buffer = new byte[16384];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /** Datos que las pantallas necesitan para mostrarse ya rellenadas. */
    private record Data(List<Dtos.Location> locations, List<Dtos.Specialty> specialties,
                        Dtos.Patient patient, Dtos.Availability availability) {

        Data(List<Dtos.Location> locations, List<Dtos.Specialty> specialties) {
            this(locations, specialties, null, null);
        }

        Data withPatient(Dtos.Patient found) {
            return new Data(locations, specialties, found, availability);
        }

        Data withAvailability(Dtos.Availability free) {
            return new Data(locations, specialties, patient, free);
        }

        java.util.Optional<Dtos.AvailableDay> firstSlot() {
            return availability == null ? java.util.Optional.empty()
                    : availability.days().stream().filter(d -> !d.slots().isEmpty()).findFirst();
        }
    }

    private record Stage(int waitMs, Runnable action, String name) {
    }
}
