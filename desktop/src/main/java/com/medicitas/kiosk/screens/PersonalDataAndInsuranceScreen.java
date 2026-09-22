package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.ApiException;
import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.ui.Async;
import com.medicitas.kiosk.ui.InsurerCard;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Paso 2: registro de paciente nuevo (RF-01) y asociación de aseguradora o atención particular (RF-03). */
public class PersonalDataAndInsuranceScreen extends Screen {

    private static final String SELF_PAY = "SELF_PAY";

    // Datos personales
    private final VBox dataSection = new VBox(16);
    private final TextField firstNames = field("firstNames");
    private final TextField lastNames = field("lastNames");
    private final DatePicker birthDate = new DatePicker();
    private final TextField phone = field("phone");
    private final TextField email = field("email");
    private final CheckBox acceptsNotifications = new CheckBox("Acepto recibir confirmaciones y recordatorios por correo y SMS");
    private final Map<Node, Label> errors = new LinkedHashMap<>();
    private final Map<Node, Supplier<Optional<String>>> rules = new LinkedHashMap<>();

    // Cobertura
    private final ToggleGroup coverages = new ToggleGroup();
    private final FlowPane cards = new FlowPane();
    private final ComboBox<Dtos.Plan> plan = new ComboBox<>();
    private final TextField policy = field("policy");
    private final VBox planDetail = new VBox(10);
    private final Label currentCoverage = label("", "muted");
    private List<Dtos.Insurer> insurers;
    private boolean coverageChanged;

    public PersonalDataAndInsuranceScreen(KioskContext ctx) {
        super(ctx);
    }

    @Override
    protected Node build() {
        birthDate.getStyleClass().add("kiosk-field");
        birthDate.setId("birthDate");
        birthDate.setPromptText("dd/mm/aaaa");
        birthDate.setMaxWidth(Double.MAX_VALUE);
        acceptsNotifications.setSelected(true);
        acceptsNotifications.setId("acceptsNotifications");

        registerRule(firstNames, () -> Validations.name(firstNames.getText(), "nombres"));
        registerRule(lastNames, () -> Validations.name(lastNames.getText(), "apellidos"));
        registerRule(birthDate, () -> Validations.birthDate(birthDate.getValue(), LocalDate.now()));
        registerRule(phone, () -> Validations.phone(phone.getText()));
        registerRule(email, () -> Validations.email(email.getText()));

        cards.setRowValignment(javafx.geometry.VPos.TOP);
        cards.setHgap(14);
        cards.setVgap(14);
        coverages.selectedToggleProperty().addListener((obs, before, now) -> {
            if (now == null && before != null) {
                before.setSelected(true);
                return;
            }
            onCoverageChanged(now);
        });

        plan.getStyleClass().add("kiosk-field");
        plan.setId("plan");
        plan.setPromptText("Elige tu plan");
        plan.setMaxWidth(Double.MAX_VALUE);
        plan.setConverter(new StringConverter<>() {
            @Override
            public String toString(Dtos.Plan p) {
                if (p == null) {
                    return "";
                }
                return p.consultationCopay() == null ? p.name()
                        : p.name() + "  ·  copago S/ " + p.consultationCopay().setScale(2, java.math.RoundingMode.HALF_UP);
            }

            @Override
            public Dtos.Plan fromString(String s) {
                return null;
            }
        });
        plan.valueProperty().addListener((o, a, b) -> {
            coverageChanged = true;
            ctx.updateNavigation();
        });
        policy.setPromptText("Opcional, figura en tu tarjeta del seguro");
        policy.textProperty().addListener((o, a, b) -> coverageChanged = true);

        HBox planRow = new HBox(16,
                group("Plan", plan, null),
                group("Número de póliza", policy, null));
        planRow.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        planDetail.getChildren().add(planRow);
        planDetail.managedProperty().bind(planDetail.visibleProperty());
        planDetail.setVisible(false);

        VBox coverageSection = new VBox(12,
                label("Tu cobertura", "section-label"),
                currentCoverage, cards, planDetail);

        VBox body = new VBox(20,
                title("Datos y seguro"),
                subtitle("Confirma tus datos de contacto y cómo se cubrirá tu consulta."),
                dataSection, coverageSection);
        ScrollPane scroll = new ScrollPane(card(body));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        return scroll;
    }

    @Override
    public void onShow() {
        buildDataSection();
        coverageChanged = false;
        if (insurers == null) {
            loadInsurers();
        } else {
            renderCards();
        }
    }

    private void loadInsurers() {
        cards.getChildren().setAll(label("Cargando aseguradoras…", "muted"));
        Async.execute(ctx.api().insurers(), null,
                list -> {
                    insurers = list;
                    renderCards();
                },
                error -> {
                    cards.getChildren().clear();
                    ctx.showError(error, this::loadInsurers);
                });
    }

    private void buildDataSection() {
        BookingSession s = ctx.session();
        Dtos.Patient p = s.getPatient();
        dataSection.getChildren().clear();
        dataSection.getChildren().add(label("Tus datos", "section-label"));

        if (p != null) {
            dataSection.getChildren().addAll(
                    row("Documento", p.documentType() + " " + p.documentNumber(), true),
                    row("Paciente", p.fullName(), false),
                    row("Celular", value(p.phone()), false),
                    row("Correo", value(p.email()), false));
            Label note = label("¿Algún dato cambió? Actualízalo en el módulo de Admisión.", "muted");
            note.setStyle("-fx-font-size: 14px;");
            dataSection.getChildren().add(note);
            return;
        }

        Label document = label(s.getDocumentType() + " " + s.getDocumentNumber(), "review-value", "mono");
        Label notice = label("Es tu primera vez en la clínica. Completa tus datos para registrarte.", "muted");
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);
        grid.add(group("Nombres", firstNames, "Como figuran en tu documento"), 0, 0);
        grid.add(group("Apellidos", lastNames, null), 1, 0);
        grid.add(group("Fecha de nacimiento", birthDate, null), 0, 1);
        grid.add(group("Celular", phone, "9 dígitos, empieza con 9"), 1, 1);
        grid.add(group("Correo electrónico", email, "Aquí enviaremos tu confirmación"), 0, 2, 2, 1);
        var col = new javafx.scene.layout.ColumnConstraints();
        col.setPercentWidth(50);
        grid.getColumnConstraints().setAll(col, col);
        dataSection.getChildren().addAll(new HBox(12, label("Documento", "review-key"), document),
                notice, grid, acceptsNotifications);
    }

    private static String value(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private HBox row(String key, String value, boolean mono) {
        Label v = label(value, "review-value");
        if (mono) {
            v.getStyleClass().add("mono");
        }
        HBox h = new HBox(label(key, "review-key"), v);
        h.getStyleClass().add("review-row");
        return h;
    }

    private VBox group(String text, Node control, String help) {
        VBox v = new VBox(6, label(text, "form-label"), control);
        if (help != null) {
            Label a = label(help, "muted");
            a.setStyle("-fx-font-size: 13px;");
            v.getChildren().add(a);
        }
        Label error = errors.get(control);
        if (error != null) {
            v.getChildren().add(error);
        }
        return v;
    }

    private void registerRule(Node control, Supplier<Optional<String>> rule) {
        Label error = label("", "field-error");
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);
        errors.put(control, error);
        rules.put(control, rule);
    }

    private boolean validateForm() {
        boolean valid = true;
        Node first = null;
        for (var entry : rules.entrySet()) {
            Optional<String> problem = entry.getValue().get();
            Label error = errors.get(entry.getKey());
            error.setText(problem.orElse(""));
            error.setVisible(problem.isPresent());
            if (problem.isPresent() && first == null) {
                first = entry.getKey();
                valid = false;
            }
        }
        if (first != null) {
            first.requestFocus();
        }
        return valid;
    }

    private void renderCards() {
        cards.getChildren().clear();
        coverages.getToggles().clear();
        for (Dtos.Insurer i : insurers) {
            int plans = i.plans() == null ? 0 : i.plans().size();
            InsurerCard card = new InsurerCard(i.name(), plans == 1 ? "1 plan en convenio" : plans + " planes en convenio");
            card.setUserData(i);
            card.setToggleGroup(coverages);
            cards.getChildren().add(card);
        }
        InsurerCard selfPay = new InsurerCard("Atención particular", "Pago directo en caja de la clínica");
        selfPay.setUserData(SELF_PAY);
        selfPay.setToggleGroup(coverages);
        cards.getChildren().add(selfPay);

        Dtos.Patient p = ctx.session().getPatient();
        Dtos.InsuranceSummary insurance = p == null ? null : p.insurance();
        if (p != null) {
            currentCoverage.setText(insurance == null
                    ? "Cobertura registrada: atención particular."
                    : "Cobertura registrada: " + insurance.description() + " · " + insurance.coverageStatus());
        }
        currentCoverage.setVisible(p != null && !ctx.session().isNewPatient());
        currentCoverage.setManaged(currentCoverage.isVisible());

        if (insurance != null) {
            for (Toggle t : coverages.getToggles()) {
                if (t.getUserData() instanceof Dtos.Insurer i && i.name().equals(insurance.insurer())) {
                    t.setSelected(true);
                    i.plans().stream().filter(pl -> pl.name().equals(insurance.plan())).findFirst()
                            .ifPresent(plan::setValue);
                    policy.setText(insurance.policyNumber() == null ? "" : insurance.policyNumber());
                }
            }
        } else if (p != null && !ctx.session().isNewPatient()) {
            selfPay.setSelected(true);
        }
        coverageChanged = false;
        ctx.updateNavigation();
    }

    private void onCoverageChanged(Toggle selection) {
        coverageChanged = true;
        if (selection != null && selection.getUserData() instanceof Dtos.Insurer i) {
            plan.getItems().setAll(i.plans() == null ? List.of() : i.plans());
            plan.setValue(plan.getItems().size() == 1 ? plan.getItems().get(0) : null);
            policy.clear();
            planDetail.setVisible(true);
        } else {
            plan.getItems().clear();
            planDetail.setVisible(false);
        }
        ctx.updateNavigation();
    }

    private boolean coverageChosen() {
        Toggle t = coverages.getSelectedToggle();
        if (t == null) {
            return false;
        }
        return SELF_PAY.equals(t.getUserData()) || plan.getValue() != null;
    }

    @Override
    public boolean nextEnabled() {
        return coverageChosen();
    }

    @Override
    public void onNext(Button button) {
        ctx.hideNotice();
        BookingSession s = ctx.session();
        if (s.getPatient() == null) {
            if (!validateForm()) {
                return;
            }
            register(button);
        } else {
            saveCoverage(button);
        }
    }

    private void register(Button button) {
        BookingSession s = ctx.session();
        Dtos.NewPatient newPatient = new Dtos.NewPatient(s.getDocumentType(), s.getDocumentNumber(),
                firstNames.getText().trim(), lastNames.getText().trim(), birthDate.getValue(), null,
                phone.getText().replace(" ", ""), email.getText().trim(), acceptsNotifications.isSelected());
        Async.execute(ctx.api().registerPatient(newPatient), button,
                patient -> {
                    s.patientRegistered(patient);
                    saveCoverage(button);
                },
                error -> {
                    if ("DUPLICATE_DOCUMENT".equals(error.getCode())) {
                        recoverProfile(button);
                    } else if (error.getStatus() == 400 && !error.getFields().isEmpty()) {
                        ctx.showError(new ApiException(400, error.getCode(),
                                error.getFields().get(0).message(), List.of()), null);
                    } else {
                        ctx.showError(error, () -> register(button));
                    }
                });
    }

    /** RF-01: si el documento ya existe se recupera el perfil en lugar de duplicarlo. */
    private void recoverProfile(Button button) {
        BookingSession s = ctx.session();
        Async.execute(ctx.api().findPatient(s.getDocumentType(), s.getDocumentNumber()), button,
                patient -> {
                    s.patientFound(patient);
                    ctx.showInfo("Ya estabas registrado. Recuperamos tu perfil para continuar.");
                    onShow();
                    ctx.updateNavigation();
                },
                error -> ctx.showError(error, () -> recoverProfile(button)));
    }

    private void saveCoverage(Button button) {
        BookingSession s = ctx.session();
        Dtos.Patient patient = s.getPatient();
        if (!coverageChanged && !s.isNewPatient()) {
            s.insuranceUpdated(patient);
            ctx.advance();
            return;
        }
        boolean selfPay = SELF_PAY.equals(coverages.getSelectedToggle().getUserData());
        Dtos.UpdateInsurance body = selfPay
                ? new Dtos.UpdateInsurance(null, null)
                : new Dtos.UpdateInsurance(plan.getValue().id(), policy.getText().isBlank() ? null : policy.getText().trim());
        Async.execute(ctx.api().updateInsurance(patient.id(), body), button,
                updated -> {
                    s.insuranceUpdated(updated);
                    coverageChanged = false;
                    ctx.advance();
                },
                error -> ctx.showError(error, () -> saveCoverage(button)));
    }

    private static TextField field(String id) {
        TextField t = new TextField();
        t.getStyleClass().add("kiosk-field");
        t.setId(id);
        return t;
    }
}
