package nl.markhayen.desktop;

import javafx.application.Platform;
import javafx.stage.Stage;
import nl.markhayen.desktop.remote.GoogleDrive;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.service.registry.ImportHttpServices;

@ImportHttpServices({GoogleDrive.class})
@SpringBootApplication(exclude = {ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class, OAuth2ClientWebSecurityAutoConfiguration.class})
public class DesktopApplication {

    public static final String CLIENT_REGISTRATION_ID = "google-login";

    static void main(String[] args) {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_GLOBAL);
        var applicationContext = new SpringApplicationBuilder()//
                .sources(DesktopApplication.class)//
                .headless(false)//
                .run(args);
        Platform.startup(() -> applicationContext.publishEvent(new StageReadyEvent(new Stage())));
    }
}
