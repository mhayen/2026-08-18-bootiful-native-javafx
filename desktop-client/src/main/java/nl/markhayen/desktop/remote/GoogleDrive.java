package nl.markhayen.desktop.remote;

import nl.markhayen.desktop.model.DriveFile;
import nl.markhayen.desktop.model.NewFile;
import nl.markhayen.desktop.model.SearchResponse;
import nl.markhayen.desktop.model.SpreadSheet;
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.Map;

import static nl.markhayen.desktop.DesktopApplication.CLIENT_REGISTRATION_ID;

@ClientRegistrationId(CLIENT_REGISTRATION_ID)
public interface GoogleDrive {

    @GetExchange("https://www.googleapis.com/drive/v3/files")
    Map<String, Object> list();

    @GetExchange("https://www.googleapis.com/drive/v3/files")
    SearchResponse search(@RequestParam("q") String searchRequest);

    @PostExchange("https://sheets.googleapis.com/v4/spreadsheets")
    Map<String, Object> createSheets(@RequestBody SpreadSheet request);

    @GetExchange("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}?includeGridData=true")
    SpreadSheet downloadSpreadSheet(@PathVariable String spreadsheetId);

    @GetExchange("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}?includeGridData=true")
    String downloadSpreadSheetAsString(@PathVariable String spreadsheetId);

    @PostExchange("https://www.googleapis.com/drive/v3/files/{fileId}/copy")
    DriveFile copyFile(@PathVariable String fileId, @RequestBody NewFile newFile);

    @PostExchange("https://script.googleapis.com/v1/scripts/{deploymentId}:run")
    Map<String, Object> runScript(@PathVariable String deploymentId, @RequestBody Map<String, Object> request);

    @PutExchange("https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}")
    Map<String, Object> updateDataInSpreadSheet(@PathVariable String spreadsheetId,
                                                @PathVariable String range,
                                                @RequestParam("valueInputOption") String valueInputOption,
                                                @RequestBody Map<String, Object> request);
}
