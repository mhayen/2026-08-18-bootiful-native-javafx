package nl.markhayen.desktop;

import nl.markhayen.desktop.model.CreateSheetsRequest;
import nl.markhayen.desktop.model.Data;
import nl.markhayen.desktop.model.RowData;
import nl.markhayen.desktop.model.SheetProperties;
import nl.markhayen.desktop.model.Sheets;
import nl.markhayen.desktop.model.SpreadSheetProperties;
import nl.markhayen.desktop.model.UserEnteredValue;
import nl.markhayen.desktop.model.Values;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
class StageInitializer {

    private final SystemBrowserOAuth2Login login;
    private Label greeting;
    private TextArea output;
    private Button call;
    private final Resource fxml = new ClassPathResource("/fxml/ui.fxml");

    static final String CLIENT_REGISTRATION_ID = "google-login";

    private final GoogleDrive googleDrive;

    StageInitializer(SystemBrowserOAuth2Login login, //
                     GoogleDrive googleDrive //
    ) {
        this.login = login;
        this.googleDrive = googleDrive;
    }


    @EventListener
    void on(StageReadyEvent event) throws Exception {
        var loader = new FXMLLoader();
        var root = (Parent) null;
        try (var fxmlInputStream = this.fxml.getInputStream()) {
            root = loader.load(fxmlInputStream);
        }
        var scene = new Scene(root);

        this.greeting = (Label) scene.lookup("#greeting");
        this.output = (TextArea) scene.lookup("#output");

        Button signIn = (Button) scene.lookup("#signIn"); //
        signIn.setOnAction(_ -> Threads.offTheFxThread(() -> this.login.start(CLIENT_REGISTRATION_ID)));

        this.call = (Button) scene.lookup("#call");
        this.call.setOnAction(_ -> Threads.offTheFxThread(this::createSheet));

        var stage = event.stage();
        stage.setTitle("JavaFX + Spring Boot + GraalVM");
        stage.setScene(scene);
        stage.setOnHidden(_ -> System.exit(0));
        stage.show();
    }

    private void listFiles() {
        try {
            var list = this.googleDrive.list();
            String kind = list.get("kind").toString();
            Threads.onTheFxThread(() -> this.output.setText("kind: " + kind));
        } catch (Exception ex) {
            IO.println(ex.getMessage());
        }
    }

    private void createSheet() {
        try {
            SpreadSheetProperties properties = new SpreadSheetProperties("My first spread sheet");
            SheetProperties sheetProperties = new SheetProperties("sheet 1", 1);
            new Sheets(sheetProperties, List.of(new Data(List.of(new RowData(List.of(new Values(new UserEnteredValue("My first value"))))))));
            var r = new CreateSheetsRequest(properties, List.of());

            var list = this.googleDrive.createSheets(r);
            String spreadsheetId = list.get("spreadsheetId").toString();
            Threads.onTheFxThread(() -> this.output.setText("spreadsheetId: " + spreadsheetId));
        } catch (Exception ex) {
            IO.println(ex.getMessage());
        }
    }

    @EventListener
    void on(UserSignedInEvent event) {
        Threads.onTheFxThread(() -> {
            this.greeting.setText("Hello, " + event.name() + ".");
            this.output.setText(claims(event.user().getClaims()));
            this.call.setDisable(false);
        });
    }

    private String claims(Map<String, Object> claims) {
        var claimsString = new StringBuilder();
        var template = "%s: %s" + System.lineSeparator();
        for (var entry : claims.entrySet())
            claimsString.append(template.formatted(entry.getKey(), entry.getValue()));
        return claimsString.toString();
    }

}

