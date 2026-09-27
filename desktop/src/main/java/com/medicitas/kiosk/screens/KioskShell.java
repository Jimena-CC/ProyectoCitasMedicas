package com.medicitas.kiosk.screens;

import atlantafx.base.controls.ModalPane;
import com.medicitas.kiosk.api.ApiClient;
import com.medicitas.kiosk.api.ApiException;
import com.medicitas.kiosk.screens.BookingSession.Step;
import com.medicitas.kiosk.ui.Banner;
import com.medicitas.kiosk.ui.Brand;
import com.medicitas.kiosk.ui.Icons;
import com.medicitas.kiosk.ui.Stepper;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Contenedor del kiosko: barra lateral con marca y pasos, área de pantalla, avisos y botones de navegación.
 * También reinicia la visita tras 90 segundos de inactividad para proteger los datos del paciente.
 */
public class KioskShell implements KioskContext {

    static final Duration INACTIVITY = Duration.seconds(90);
    static final int NOTICE_SECONDS = 10;

    private final ApiClient api;
    private final BookingSession session = new BookingSession();
    private final Map<Step, Screen> screens = new EnumMap<>(Step.class);
    private MyAppointmentsScreen myAppointments;
    private Screen current;

    private final Stepper stepper = new Stepper(Arrays.stream(Step.values()).map(Step::title).toList());
    private final Banner banner = new Banner();
    private final StackPane area = new StackPane();
    private final Button back = new Button("Atrás");
    private final Button next = new Button("Siguiente");
    private final ModalPane modal = new ModalPane();
    private final StackPane root;

    private final PauseTransition inactivity = new PauseTransition(INACTIVITY);
    private Timeline countdown;

    public KioskShell(ApiClient api) {
        this.api = api;
        createScreens();

        back.getStyleClass().addAll("kiosk-button", "button-outlined");
        back.setOnAction(e -> current.onBack());
        next.getStyleClass().addAll("kiosk-button", "accent");
        next.setDefaultButton(false);
        next.setOnAction(e -> current.onNext(next));
        stepper.setOnStepSelected(i -> {
            if (session.getMode() == BookingSession.Mode.BOOKING && session.getConfirmedAppointment() == null) {
                hideNotice();
                showStep(Step.values()[i]);
            }
        });

        HBox actions = new HBox(back, next);
        actions.getStyleClass().add("actions");
        back.managedProperty().bind(back.visibleProperty());
        next.managedProperty().bind(next.visibleProperty());

        VBox.setVgrow(area, Priority.ALWAYS);
        VBox content = new VBox(banner, area, actions);
        content.getStyleClass().add("content");
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox card = new HBox(buildRail(), content);
        card.getStyleClass().add("shell-card");
        card.setMaxSize(1480, 940);

        StackPane background = new StackPane(card);
        background.getStyleClass().add("ground");
        root = new StackPane(background, modal);

        inactivity.setOnFinished(e -> onInactivityTimeout());
    }

    private Node buildRail() {
        Node helpIcon = Icons.of("help-circle", 20);
        Label help = new Label("¿Necesitas ayuda? Acércate a Admisión.");
        help.setGraphic(helpIcon);
        help.setGraphicTextGap(10);
        help.getStyleClass().add("rail-footer");
        help.setWrapText(true);
        help.setMaxWidth(Double.MAX_VALUE);
        help.setMinHeight(Region.USE_PREF_SIZE);
        Label motto = new Label("PACIENTE ANTE TODO");
        motto.getStyleClass().add("rail-motto");

        Region space = new Region();
        VBox.setVgrow(space, Priority.ALWAYS);
        VBox rail = new VBox(Brand.logo(), stepper, space, new VBox(10, help, motto));
        rail.getStyleClass().add("rail");
        rail.setMinWidth(330);
        rail.setPrefWidth(340);
        return rail;
    }

    private void createScreens() {
        screens.put(Step.IDENTIFICATION, new IdentificationScreen(this));
        screens.put(Step.PERSONAL_DATA_AND_INSURANCE, new PersonalDataAndInsuranceScreen(this));
        screens.put(Step.SPECIALTY_AND_LOCATION, new SpecialtyAndLocationScreen(this));
        screens.put(Step.DATE_AND_TIME, new DateAndTimeScreen(this));
        screens.put(Step.CONFIRMATION, new ConfirmationScreen(this));
        myAppointments = new MyAppointmentsScreen(this);
    }

    public Parent root() {
        return root;
    }

    /** Registra la detección de actividad sobre la escena y muestra la primera pantalla. */
    public void start(Scene scene) {
        for (var type : Arrays.asList(MouseEvent.MOUSE_PRESSED, KeyEvent.KEY_PRESSED,
                TouchEvent.TOUCH_PRESSED, ScrollEvent.SCROLL)) {
            scene.addEventFilter(type, this::onActivityDetected);
        }
        show(screens.get(Step.IDENTIFICATION));
        inactivity.playFromStart();
    }

    private void show(Screen screen) {
        current = screen;
        Node node = screen.content();
        area.getChildren().setAll(node);
        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setFromValue(0.4);
        fade.setToValue(1);
        fade.play();
        screen.onShow();
        updateNavigation();
    }

    @Override
    public void updateNavigation() {
        if (current == null) {
            return;
        }
        back.setVisible(current.showBack());
        next.setVisible(current.showNext());
        next.setText(current.nextText());
        boolean loading = next.getGraphic() instanceof ProgressIndicator;
        if (!loading) {
            next.setDisable(!current.nextEnabled());
        }
        stepper.update(session.getCurrentStep().ordinal(), session.getMaxStep().ordinal());
    }

    @Override
    public void advance() {
        if (session.advance()) {
            hideNotice();
            show(screens.get(session.getCurrentStep()));
        }
    }

    @Override
    public void primaryAction() {
        if (next.isVisible() && !next.isDisabled()) {
            current.onNext(next);
        }
    }

    @Override
    public void goBack() {
        if (session.goBack()) {
            hideNotice();
            show(screens.get(session.getCurrentStep()));
        }
    }

    @Override
    public void showStep(Step step) {
        if (session.goTo(step)) {
            show(screens.get(step));
        }
    }

    @Override
    public void showMyAppointments() {
        hideNotice();
        show(myAppointments);
    }

    @Override
    public void endVisit() {
        stopCountdown();
        closeDialog();
        hideNotice();
        session.clear();
        createScreens(); // descarta formularios con datos del paciente anterior
        show(screens.get(Step.IDENTIFICATION));
        inactivity.playFromStart();
    }

    @Override
    public ApiClient api() {
        return api;
    }

    @Override
    public BookingSession session() {
        return session;
    }

    @Override
    public void showError(ApiException error, Runnable retry) {
        banner.error(error.getMessage(), retry);
    }

    @Override
    public void showInfo(String message) {
        banner.info(message);
    }

    @Override
    public void hideNotice() {
        banner.hide();
    }

    @Override
    public void showDialog(Node content) {
        modal.show(content);
    }

    @Override
    public void closeDialog() {
        modal.hide(true);
    }

    private void onActivityDetected(Event event) {
        if (countdown != null) {
            stopCountdown();
            closeDialog();
        }
        inactivity.playFromStart();
    }

    private boolean hasPatientData() {
        return session.getPatient() != null || session.getDocumentNumber() != null
                || session.getCurrentStep() != Step.IDENTIFICATION || current == myAppointments;
    }

    private void onInactivityTimeout() {
        if (!hasPatientData()) {
            endVisit();
            return;
        }
        Label seconds = new Label(String.valueOf(NOTICE_SECONDS));
        seconds.getStyleClass().add("countdown");
        Label title = new Label("¿Sigues ahí?");
        title.getStyleClass().add("title");
        Label text = new Label("Por tu privacidad, cerraremos tu sesión y borraremos los datos ingresados.");
        text.getStyleClass().add("subtitle");
        text.setWrapText(true);
        Button resume = new Button("Sí, continuar");
        resume.getStyleClass().addAll("kiosk-button", "accent");
        HBox row = new HBox(20, seconds, new VBox(6, title, text));
        row.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(resume);
        actions.getStyleClass().add("actions");
        VBox dialog = new VBox(row, actions);
        dialog.getStyleClass().add("dialog-card");
        showDialog(dialog);

        int[] remaining = {NOTICE_SECONDS};
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remaining[0]--;
            seconds.setText(String.valueOf(remaining[0]));
            if (remaining[0] <= 0) {
                endVisit();
            }
        }));
        countdown.setCycleCount(NOTICE_SECONDS);
        countdown.play();
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }
}
