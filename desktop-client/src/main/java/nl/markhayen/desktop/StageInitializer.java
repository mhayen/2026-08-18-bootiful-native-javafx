package nl.markhayen.desktop;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import nl.markhayen.desktop.auth.SystemBrowserOAuth2Login;
import nl.markhayen.desktop.auth.UserSignedInEvent;
import nl.markhayen.desktop.model.DriveFile;
import nl.markhayen.desktop.remote.GoogleDriveService;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static nl.markhayen.desktop.DesktopApplication.CLIENT_REGISTRATION_ID;

@Component
class StageInitializer {

    private static final String DARK_THEME = "/theme-dark.css";
    private static final String LIGHT_THEME = "/theme-light.css";
    private static final String TITLE = "Formulier beheer";

    private final SystemBrowserOAuth2Login login;
    private final GoogleDriveService googleDrive;
    private final Resource loginFxml = new ClassPathResource("/fxml/login-view.fxml");
    private final Resource fxml = new ClassPathResource("/fxml/ui.fxml");

    private final ObservableList<DriveFile> spreadsheets = FXCollections.observableArrayList();
    private final Map<String, Tab> openTabs = new HashMap<>();

    private Stage stage;
    private TabPane tabs;
    private Label status;

    StageInitializer(SystemBrowserOAuth2Login login, //
                     GoogleDriveService googleDrive) {
        this.login = login;
        this.googleDrive = googleDrive;
    }

    @EventListener
    void on(StageReadyEvent event) {
        this.stage = event.stage();
        showLoginScreen();
    }

    private void showLoginScreen() {
        var scene = new Scene(load(this.loginFxml));
        applyTheme(scene, false);

        Button signIn = (Button) scene.lookup("#signIn");
        signIn.setOnAction(_ -> Threads.offTheFxThread(() -> this.login.start(CLIENT_REGISTRATION_ID)));

        this.stage.setTitle(TITLE);
        this.stage.setScene(scene);
        this.stage.setOnHidden(_ -> System.exit(0));
        this.stage.show();
    }

    @EventListener
    void on(UserSignedInEvent event) {
        Threads.onTheFxThread(() -> showMainScreen(event));
    }

    @SuppressWarnings("unchecked")
    private void showMainScreen(UserSignedInEvent event) {
        ListView<DriveFile> spreadsheetList;
        var scene = new Scene(load(this.fxml));

        TextField spreadsheetFilter = (TextField) scene.lookup("#spreadsheetFilter");
        spreadsheetList = (ListView<DriveFile>) scene.lookup("#spreadsheetList");
        this.tabs = (TabPane) scene.lookup("#tabs");

        Button runScript = (Button) scene.lookup("#runScript"); //
        runScript.setOnAction(_ -> Threads.offTheFxThread(this::runScript));
        this.status = (Label) scene.lookup("#status");
        this.status.setText("Ingelogd als " + event.name());

        var lightTheme = (CheckBox) scene.lookup("#lightTheme");
        applyTheme(scene, lightTheme.isSelected());
        lightTheme.selectedProperty().addListener((_, _, isLight) -> applyTheme(scene, isLight));

        spreadsheetList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(DriveFile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        var filtered = new FilteredList<>(this.spreadsheets);
        spreadsheetFilter.textProperty().addListener((_, _, filter) -> {
            var needle = filter == null ? "" : filter.trim().toLowerCase();
            filtered.setPredicate(file -> needle.isEmpty() || file.name().toLowerCase().contains(needle));
        });
        spreadsheetList.setItems(filtered);

        spreadsheetList.getSelectionModel().selectedItemProperty().addListener((_, _, selected) -> {
            if (selected != null) {
                openFormulierTab(selected);
            }
        });

        this.stage.setScene(scene);

        Threads.offTheFxThread(this::loadSpreadsheets);
    }

    private static Parent load(Resource fxml) {
        try (var fxmlInputStream = fxml.getInputStream()) {
            return new FXMLLoader().load(fxmlInputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadSpreadsheets() {
        var files = this.googleDrive.listSpreadsheets();
        Threads.onTheFxThread(() -> this.spreadsheets.setAll(files));
    }

    private void applyTheme(Scene scene, boolean light) {
        scene.getStylesheets().removeAll(
                Objects.requireNonNull(getClass().getResource(DARK_THEME)).toExternalForm(),
                Objects.requireNonNull(getClass().getResource(LIGHT_THEME)).toExternalForm());
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(light ? LIGHT_THEME : DARK_THEME)).toExternalForm());
    }

    private void runScript() {
        String s = this.googleDrive.runFunctionMetParameter();
        Threads.onTheFxThread(() -> this.status.setText(s));
    }

    private void openFormulierTab(DriveFile file) {
        var existing = this.openTabs.get(file.id());
        if (existing != null) {
            this.tabs.getSelectionModel().select(existing);
            return;
        }

        var tab = new Tab(file.name(), new Label("Loading..."));
        this.openTabs.put(file.id(), tab);
        tab.setOnClosed(_ -> this.openTabs.remove(file.id()));
        this.tabs.getTabs().add(tab);
        this.tabs.getSelectionModel().select(tab);

        loadFormulierInto(tab, file.id());
    }

    private void loadFormulierInto(Tab tab, String fileId) {
        Threads.offTheFxThread(() -> {
            var formulier = this.googleDrive.fetchFormulier(fileId);
            Threads.onTheFxThread(() -> tab.setContent(FormulierView.build(fileId, formulier, this.googleDrive,
                    this.status, () -> loadFormulierInto(tab, fileId))));
        }, ex -> tab.setContent(new Label("Failed to load: " + ex.getMessage())));
    }

}
