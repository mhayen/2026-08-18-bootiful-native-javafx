package nl.markhayen.desktop.remote;

import nl.markhayen.desktop.model.CreateSheetsRequest;
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;

import static nl.markhayen.desktop.DesktopApplication.CLIENT_REGISTRATION_ID;

@ClientRegistrationId(CLIENT_REGISTRATION_ID)
public interface GoogleDrive {

    @GetExchange("https://www.googleapis.com/drive/v3/files")
    Map<String, Object> list();

    @PostExchange("https://sheets.googleapis.com/v4/spreadsheets")
    Map<String, Object> createSheets(@RequestBody CreateSheetsRequest request);
}
