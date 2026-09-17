package com.medicitas.kiosk.screens;

import atlantafx.base.theme.Styles;
import com.medicitas.kiosk.api.Dtos;
import com.medicitas.kiosk.ui.Async;
import com.medicitas.kiosk.ui.Icons;
import com.medicitas.kiosk.ui.Keypad;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Paso 1: identificación por documento con teclado en pantalla (RF-02). */
public class IdentificationScreen extends Screen {

    private final ToggleGroup types = new ToggleGroup();
    private final TextField document = new TextField();
    private final Label error = label("", "field-error");
    private final StackPane container = new StackPane();
    private Node form;
    private VBox greeting;
    private Label greetingTitle;

    public IdentificationScreen(KioskContext ctx) {
        super(ctx);
    }

    @Override
    protected Node build() {
        form = buildForm();
        greeting = buildGreeting();
        container.getChildren().setAll(form);
        return container;
    }

    private Node buildForm() {
        HBox segmented = new HBox(
                type("DNI", "DNI", Styles.LEFT_PILL),
                type("Carné de extranjería", "CE", Styles.CENTER_PILL),
                type("Pasaporte", "PAS", Styles.RIGHT_PILL));
        segmented.getStyleClass().add("segmented");
        types.getToggles().get(0).setSelected(true);
        types.selectedToggleProperty().addListener((obs, before, now) -> {
            if (now == null) {
                before.setSelected(true);
                return;
            }
            document.clear();
            error.setText("");
            document.setPromptText(selectedType().equals("DNI") ? "8 dígitos" : "Letras y números");
        });

        document.getStyleClass().add("document-field");
        document.setPromptText("8 dígitos");
        document.setId("document");
        document.setTextFormatter(new TextFormatter<String>(change -> {
            String updated = change.getControlNewText().toUpperCase();
            String pattern = selectedType().equals("DNI") ? "\\d*" : "[A-Z0-9]*";
            if (!updated.matches(pattern) || updated.length() > Validations.maxLength(selectedType())) {
                return null;
            }
            change.setText(change.getText().toUpperCase());
            return change;
        }));
        document.textProperty().addListener((obs, a, b) -> {
            error.setText("");
            ctx.updateNavigation();
        });
        document.setOnAction(e -> ctx.primaryAction());

        Label privacy = label("Tus datos se usan solo para gestionar tus citas, según la Ley 29733 "
                + "de Protección de Datos Personales.", "muted");
        privacy.setWrapText(true);
        privacy.setStyle("-fx-font-size: 14px;");

        VBox left = new VBox(14,
                title("Identifícate para empezar"),
                subtitle("Buscaremos tu registro en la clínica. Si es tu primera vez, te ayudamos a registrarte."),
                label("Tipo de documento", "section-label"), segmented,
                label("Número de documento", "section-label"), document, error, privacy);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMaxWidth(560);

        Keypad keypad = new Keypad(
                digit -> document.appendText(digit),
                () -> {
                    String t = document.getText();
                    if (!t.isEmpty()) {
                        document.setText(t.substring(0, t.length() - 1));
                    }
                },
                document::clear);
        keypad.setAlignment(Pos.CENTER);

        HBox row = new HBox(40, left, keypad);
        row.setAlignment(Pos.CENTER_LEFT);
        return card(row);
    }

    private VBox buildGreeting() {
        greetingTitle = title("");
        Button book = option("Reservar una nueva cita",
                "Elige especialidad, sede, fecha y horario.", "calendar-check");
        book.setOnAction(e -> ctx.advance());
        Button myAppointments = option("Mis citas",
                "Revisa, reprograma o anula tus citas reservadas.", "list");
        myAppointments.setOnAction(e -> ctx.showMyAppointments());

        Button notMe = new Button("No soy yo, volver a identificarme");
        notMe.getStyleClass().add("link-button");
        notMe.setOnAction(e -> ctx.endVisit());

        HBox options = new HBox(18, book, myAppointments);
        VBox box = card(greetingTitle, subtitle("¿Qué deseas hacer hoy?"), options, notMe);
        box.setSpacing(22);
        return box;
    }

    private static Button option(String title, String detail, String icon) {
        Label t = label(title, "insurer-title");
        t.setStyle("-fx-font-size: 20px;");
        Label d = label(detail, "insurer-sub");
        d.setWrapText(true);
        Node i = Icons.of(icon, 30, "icon-action");
        VBox texts = new VBox(4, t, d);
        HBox content = new HBox(16, i, texts, Icons.of("nav-arrow-right", 24));
        content.setAlignment(Pos.CENTER_LEFT);
        Button b = new Button();
        b.setGraphic(content);
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        b.getStyleClass().add("insurer-card");
        b.setMinHeight(120);
        b.setPrefWidth(360);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private ToggleButton type(String text, String code, String pill) {
        ToggleButton t = new ToggleButton(text);
        t.setUserData(code);
        t.setToggleGroup(types);
        t.getStyleClass().add(pill);
        t.setFocusTraversable(false);
        return t;
    }

    private String selectedType() {
        return types.getSelectedToggle() == null ? "DNI" : (String) types.getSelectedToggle().getUserData();
    }

    @Override
    public void onShow() {
        Dtos.Patient patient = ctx.session().getPatient();
        if (patient != null) {
            greetingTitle.setText("Hola, " + patient.firstName());
            container.getChildren().setAll(greeting);
        } else {
            if (ctx.session().getDocumentNumber() == null) {
                document.clear();
                types.getToggles().get(0).setSelected(true);
            }
            error.setText("");
            container.getChildren().setAll(form);
            document.requestFocus();
        }
    }

    @Override
    public boolean showNext() {
        return ctx.session().getPatient() == null;
    }

    @Override
    public boolean showBack() {
        return false;
    }

    @Override
    public String nextText() {
        return "Continuar";
    }

    @Override
    public boolean nextEnabled() {
        return !document.getText().isBlank();
    }

    @Override
    public void onNext(Button button) {
        String type = selectedType();
        String number = document.getText().trim();
        var problem = Validations.document(type, number);
        if (problem.isPresent()) {
            error.setText(problem.get());
            return;
        }
        ctx.hideNotice();
        ctx.session().documentEntered(type, number);
        Async.execute(ctx.api().findPatient(type, number), button,
                patient -> {
                    ctx.session().patientFound(patient);
                    onShow();
                    ctx.updateNavigation();
                },
                failure -> {
                    if (failure.isNotFound()) {
                        ctx.session().patientNotRegistered();
                        ctx.advance();
                    } else {
                        ctx.showError(failure, () -> onNext(button));
                    }
                });
    }
}
