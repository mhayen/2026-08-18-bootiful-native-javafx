package nl.markhayen.desktop;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import nl.markhayen.desktop.auth.SystemBrowserOAuth2Login;
import nl.markhayen.desktop.auth.UserSignedInEvent;
import nl.markhayen.desktop.remote.GoogleDriveService;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

import static nl.markhayen.desktop.DesktopApplication.CLIENT_REGISTRATION_ID;

@Component
class StageInitializer {

    private final SystemBrowserOAuth2Login login;
    private Label greeting;
    private TextArea output;
    private TextField fileId;
    private Button call;
    private Button download;
    private final Resource fxml = new ClassPathResource("/fxml/ui.fxml");


    private final GoogleDriveService googleDrive;

    StageInitializer(SystemBrowserOAuth2Login login, //
                     GoogleDriveService googleDrive) {
        this.login = login;
        this.googleDrive = googleDrive;
    }


    @EventListener
    void on(StageReadyEvent event) throws Exception {
        var loader = new FXMLLoader();
        Parent root;
        try (var fxmlInputStream = this.fxml.getInputStream()) {
            root = loader.load(fxmlInputStream);
        }
        var scene = new Scene(root);

        this.greeting = (Label) scene.lookup("#greeting");
        this.output = (TextArea) scene.lookup("#output");
        this.fileId = (TextField) scene.lookup("#fileId");

        Button signIn = (Button) scene.lookup("#signIn"); //
        signIn.setOnAction(_ -> Threads.offTheFxThread(() -> this.login.start(CLIENT_REGISTRATION_ID)));

        this.call = (Button) scene.lookup("#call");
        this.call.setOnAction(_ -> Threads.offTheFxThread(this::callApi));
        this.download = (Button) scene.lookup("#download");
        this.download.setOnAction(_ -> Threads.offTheFxThread(() -> googleDrive.downloadFormulier(output, fileId.getText())));

        var stage = event.stage();
        stage.setTitle("JavaFX + Spring Boot + GraalVM");
        stage.setScene(scene);
        stage.setOnHidden(_ -> System.exit(0));
        stage.show();
    }

    private void callApi() {
//        googleDrive.createSheet(output);
        googleDrive.searchFiles(output);
    }


    @EventListener
    void on(UserSignedInEvent event) {
        Threads.onTheFxThread(() -> {
            this.greeting.setText("Hello, " + event.name() + ".");
            this.output.setText(claims(event.user().getClaims()));
            this.call.setDisable(false);
            this.download.setDisable(false);
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

