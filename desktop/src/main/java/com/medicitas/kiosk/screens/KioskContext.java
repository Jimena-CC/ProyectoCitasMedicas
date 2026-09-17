package com.medicitas.kiosk.screens;

import com.medicitas.kiosk.api.ApiClient;
import com.medicitas.kiosk.api.ApiException;

/** Servicios que el contenedor del kiosko ofrece a cada pantalla. */
public interface KioskContext {

    ApiClient api();

    BookingSession session();

    /** Vuelve a evaluar los botones Atrás / Siguiente y la barra de pasos. */
    void updateNavigation();

    void advance();

    /** Equivale a presionar el botón principal (Siguiente / Continuar) de la pantalla actual. */
    void primaryAction();

    void goBack();

    void showStep(BookingSession.Step step);

    void showMyAppointments();

    /** Borra la sesión y regresa a la identificación para el siguiente paciente. */
    void endVisit();

    /** Muestra el mensaje de la API en el aviso superior; ofrece reintentar si se perdió la conexión. */
    void showError(ApiException error, Runnable retry);

    void showInfo(String message);

    /** Muestra un diálogo modal (AtlantaFX ModalPane) sobre el kiosko. */
    void showDialog(javafx.scene.Node content);

    void closeDialog();

    void hideNotice();
}
