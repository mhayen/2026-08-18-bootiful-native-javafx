package nl.markhayen.desktop;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import nl.markhayen.desktop.auth.SystemBrowserOAuth2Login;
import nl.markhayen.desktop.auth.UserSignedInEvent;
import nl.markhayen.desktop.formulier.Datums;
import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.Navigatie;
import nl.markhayen.desktop.model.DriveFile;
import nl.markhayen.desktop.remote.GoogleDriveService;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static nl.markhayen.desktop.DesktopApplication.CLIENT_REGISTRATION_ID;

@Component
class StageInitializer {

    private static final String DARK_THEME = "/theme-dark.css";
    private static final String LIGHT_THEME = "/theme-light.css";

    private final SystemBrowserOAuth2Login login;
    private final GoogleDriveService googleDrive;
    private final Resource fxml = new ClassPathResource("/fxml/ui.fxml");

    private final ObservableList<DriveFile> spreadsheets = FXCollections.observableArrayList();
    private final Map<String, Tab> openTabs = new HashMap<>();

    private Label greeting;
    private TextField spreadsheetFilter;
    private ListView<DriveFile> spreadsheetList;
    private TabPane tabs;
    private Label status;

    StageInitializer(SystemBrowserOAuth2Login login, //
                     GoogleDriveService googleDrive) {
        this.login = login;
        this.googleDrive = googleDrive;
    }


    @EventListener
    @SuppressWarnings("unchecked")
    void on(StageReadyEvent event) throws Exception {
        var loader = new FXMLLoader();
        Parent root;
        try (var fxmlInputStream = this.fxml.getInputStream()) {
            root = loader.load(fxmlInputStream);
        }
        var scene = new Scene(root);

        this.greeting = (Label) scene.lookup("#greeting");
        this.spreadsheetFilter = (TextField) scene.lookup("#spreadsheetFilter");
        this.spreadsheetList = (ListView<DriveFile>) scene.lookup("#spreadsheetList");
        this.tabs = (TabPane) scene.lookup("#tabs");

        Button signIn = (Button) scene.lookup("#signIn"); //
        signIn.setOnAction(_ -> Threads.offTheFxThread(() -> this.login.start(CLIENT_REGISTRATION_ID)));
        Button runScript = (Button) scene.lookup("#runScript"); //
        runScript.setOnAction(_ -> Threads.offTheFxThread(this::runScript));
        status = (Label) scene.lookup("#status");
        status.setText("Gestart");

        var lightTheme = (CheckBox) scene.lookup("#lightTheme");
        applyTheme(scene, lightTheme.isSelected());
        lightTheme.selectedProperty().addListener((_, _, isLight) -> applyTheme(scene, isLight));

        this.spreadsheetList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(DriveFile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        var filtered = new FilteredList<>(this.spreadsheets);
        this.spreadsheetFilter.textProperty().addListener((_, _, filter) -> {
            var needle = filter == null ? "" : filter.trim().toLowerCase();
            filtered.setPredicate(file -> needle.isEmpty() || file.name().toLowerCase().contains(needle));
        });
        this.spreadsheetList.setItems(filtered);

        this.spreadsheetList.getSelectionModel().selectedItemProperty().addListener((_, _, selected) -> {
            if (selected != null) {
                openFormulierTab(selected);
            }
        });

        var stage = event.stage();
        stage.setTitle("Formulier beheer");
        stage.setScene(scene);
        stage.setOnHidden(_ -> System.exit(0));
        stage.show();
    }


    @EventListener
    void on(UserSignedInEvent event) {
        Threads.onTheFxThread(() -> {
            this.greeting.setText("Hello, " + event.name() + ".");
            this.spreadsheetFilter.setDisable(false);
            this.spreadsheetList.setDisable(false);
        });
        Threads.offTheFxThread(this::loadSpreadsheets);
    }

    private void loadSpreadsheets() {
        var files = this.googleDrive.listSpreadsheets();
        Threads.onTheFxThread(() -> this.spreadsheets.setAll(files));
    }

    private void applyTheme(Scene scene, boolean light) {
        scene.getStylesheets().removeAll(
                getClass().getResource(DARK_THEME).toExternalForm(),
                getClass().getResource(LIGHT_THEME).toExternalForm());
        scene.getStylesheets().add(getClass().getResource(light ? LIGHT_THEME : DARK_THEME).toExternalForm());
    }

    private void runScript() {
        String s = this.googleDrive.runScript();
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

        Threads.offTheFxThread(() -> {
            var formulier = this.googleDrive.fetchFormulier(file.id());
            Threads.onTheFxThread(() -> tab.setContent(formulierView(formulier)));
        }, ex -> tab.setContent(new Label("Failed to load: " + ex.getMessage())));
    }

    private Node formulierView(Formulier formulier) {
        var naam = new Label(formulier.formulierNaam());
        naam.getStyleClass().add("greeting");

        var content = new VBox(16,
                naam,
                section("Instellingen", instellingenView(formulier)),
                section("Datums", datumsTable(formulier.datums())),
                section("Dagdelen", new Label(String.join(", ", formulier.dagdelen()))),
                section("Afhankelijkheden", new Label(formulier.afhankelijkheden() == null ? ""
                        : String.join(", ", formulier.afhankelijkheden().aantalKindermenus()))),
                section("Navigatie", navigatieTable(formulier.navigatie())));
        content.setPadding(new javafx.geometry.Insets(16));

        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private Node section(String title, Node body) {
        var heading = new Label(title);
        heading.getStyleClass().add("section-title");
        return new VBox(8, heading, body);
    }

    private Node instellingenView(Formulier formulier) {
        var instellingen = formulier.instellingen();
        if (instellingen == null) {
            return new Label("(geen instellingen)");
        }
        var rows = List.of(
                Map.entry("Actief", str(instellingen.actief())),
                Map.entry("Logo", str(instellingen.logoFilename())),
                Map.entry("Naam afzender", str(instellingen.naamAfzender())),
                Map.entry("E-mail onderwerp", str(instellingen.emailOnderwerp())),
                Map.entry("Antwoord e-mail", str(instellingen.antwoordEmail())),
                Map.entry("Kopie naar", str(instellingen.kopieNaar())),
                Map.entry("API token", str(instellingen.apiToken())),
                Map.entry("Resultaat spreadsheet", str(instellingen.resultaatSpreadsheetId())));

        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        for (int i = 0; i < rows.size(); i++) {
            var entry = rows.get(i);
            var key = new Label(entry.getKey() + ":");
            key.getStyleClass().add("subtle");
            grid.addRow(i, key, new Label(entry.getValue()));
        }
        return grid;
    }

    private Node datumsTable(List<Datums> datums) {
        if (datums.isEmpty()) {
            return new Label("(geen datums)");
        }
        var table = new TableView<Datums>();
        table.getColumns().add(column("Kort", d -> str(d.kort())));
        table.getColumns().add(column("Lang", d -> str(d.lang())));
        table.getColumns().add(column("Start", d -> str(d.start())));
        table.getColumns().add(column("Eind", d -> str(d.eind())));
        table.getItems().addAll(datums);
        table.setPrefHeight(rowHeight(datums.size()));
        return table;
    }

    private Node navigatieTable(List<Navigatie> navigatie) {
        if (navigatie.isEmpty()) {
            return new Label("(geen navigatie)");
        }
        var table = new TableView<Navigatie>();
        table.getColumns().add(column("Volgorde", n -> str(n.volgorde())));
        table.getColumns().add(column("Sectie", n -> str(n.sectie())));
        table.getColumns().add(column("Titel", n -> str(n.titel())));
        table.getColumns().add(column("Validatie", n -> str(n.validatie())));
        table.getColumns().add(column("Stap", n -> str(n.stap())));
        table.getItems().addAll(navigatie);
        table.setPrefHeight(rowHeight(navigatie.size()));
        return table;
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> accessor) {
        var column = new TableColumn<T, String>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(accessor.apply(data.getValue())));
        return column;
    }

    private static double rowHeight(int rowCount) {
        return 32d + rowCount * 28;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

}
