package nl.markhayen.desktop.formulier;

import nl.markhayen.desktop.model.Data;
import nl.markhayen.desktop.model.RowData;
import nl.markhayen.desktop.model.SheetProperties;
import nl.markhayen.desktop.model.Sheets;
import nl.markhayen.desktop.model.SpreadSheet;
import nl.markhayen.desktop.model.UserEnteredValue;
import nl.markhayen.desktop.model.Values;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormulierMapperTest {

    private final FormulierMapper mapper = new FormulierMapper();

    @Test
    void readsRealSpreadsheetIntoFormulier() throws IOException {
        String json = Files.readString(Path.of("formulier-spreadsheet.json"));
        SpreadSheet spreadSheet = JsonMapper.builder().build().readValue(json, SpreadSheet.class);

        Formulier formulier = mapper.toFormulier(spreadSheet);

        assertThat(formulier.formulierNaam()).isEqualTo("formulier-menukeuzekerst");

        assertThat(formulier.navigatie()).isNotEmpty();
        Navigatie eersteStap = formulier.navigatie().getFirst();
        assertThat(eersteStap.volgorde().value()).isEqualTo(1);
        assertThat(eersteStap.volgorde().a1()).isEqualTo("navigatie!A2");
        assertThat(eersteStap.sectie().value()).isEqualTo("start");
        assertThat(eersteStap.sectie().a1()).isEqualTo("navigatie!B2");
        assertThat(eersteStap.validatie().value()).isTrue();

        assertThat(formulier.secties()).isNotEmpty();
        Secties startSectie = formulier.secties().stream()
                .filter(s -> "start".equals(s.sectie()))
                .findFirst()
                .orElseThrow();
        assertThat(startSectie.velden()).extracting(Velden::naam).contains("naam");

        assertThat(formulier.instellingen()).isNotNull();
        assertThat(formulier.instellingen().apiToken().value())
                .isEqualTo("394f07cefce22fb3915e24a753db541bb5d66045");
        assertThat(formulier.instellingen().apiToken().a1()).isEqualTo("instellingen!A2");
        assertThat(formulier.instellingen().resultaatSpreadsheetId().value()).isEqualTo("kerstdagen");

        // no "datums"/"dagdelen"/"afhankelijkheden" sheets exist in this particular spreadsheet
        assertThat(formulier.datums()).isEmpty();
        assertThat(formulier.dagdelen()).isEmpty();
        assertThat(formulier.afhankelijkheden()).isNull();
    }

    @Test
    void roundTripsThroughSpreadSheet() {
        Formulier formulier = new Formulier(
                "kerst-formulier",
                List.of(new Secties("Start", "start", "Start", List.of(
                        new Velden("veld1", "start", "Start", null, "naam", "N", "rood", 1, "korte_tekst",
                                false, true, true, "Naam", "Uw naam", null, null, null, 10)))),
                List.of(new Datums(cell("24 dec"), cell("24 december"),
                        cell(LocalDateTime.of(2026, 12, 24, 0, 0)), cell(LocalDateTime.of(2026, 12, 24, 18, 0)))),
                List.of(cell("lunch"), cell("diner")),
                new Afhankelijkheden(List.of(cell("1"), cell("2"), cell("3"))),
                new Instellingen(cell(true), cell("logo.png"), cell("Afzender"), cell("Onderwerp"),
                        cell("antwoord@example.com"), cell("kopie@example.com"), cell("token123"),
                        cell("spreadsheet-id")),
                List.of(new Navigatie(cell(1), cell("start"), cell("Start"), cell(true), cell("veld1"), cell("cond"),
                        cell(true), cell("stap1"))));

        SpreadSheet spreadSheet = mapper.toSpreadSheet(formulier);
        Formulier result = mapper.toFormulier(spreadSheet);

        // record types unaffected by A1 tracking round-trip losslessly by value
        assertThat(result.formulierNaam()).isEqualTo(formulier.formulierNaam());
        assertThat(result.secties()).isEqualTo(formulier.secties());

        Datums datums = result.datums().getFirst();
        assertThat(datums.kort().value()).isEqualTo("24 dec");
        assertThat(datums.kort().a1()).isEqualTo("datums!A2");
        assertThat(datums.lang().value()).isEqualTo("24 december");
        assertThat(datums.start().value()).isEqualTo(LocalDateTime.of(2026, 12, 24, 0, 0));
        assertThat(datums.eind().value()).isEqualTo(LocalDateTime.of(2026, 12, 24, 18, 0));

        assertThat(result.dagdelen()).extracting(Cell::value).containsExactly("lunch", "diner");
        assertThat(result.dagdelen().getFirst().a1()).isEqualTo("dagdelen!A2");

        assertThat(result.afhankelijkheden().aantalKindermenus())
                .extracting(Cell::value).containsExactly("1", "2", "3");

        Instellingen instellingen = result.instellingen();
        assertThat(instellingen.actief().value()).isTrue();
        assertThat(instellingen.logoFilename().value()).isEqualTo("logo.png");
        assertThat(instellingen.apiToken().value()).isEqualTo("token123");
        assertThat(instellingen.resultaatSpreadsheetId().value()).isEqualTo("spreadsheet-id");

        Navigatie navigatie = result.navigatie().getFirst();
        assertThat(navigatie.volgorde().value()).isEqualTo(1);
        assertThat(navigatie.sectie().value()).isEqualTo("start");
        assertThat(navigatie.validatie().value()).isTrue();
        assertThat(navigatie.stap().value()).isEqualTo("stap1");
    }

    @Test
    void parsesGoogleSheetsSerialNumberDates() {
        // examples from Google Sheets' SERIAL_NUMBER docs: day 0 is December 30th 1899
        SpreadSheet spreadSheet = datumsSpreadSheet(2.5, 33.625);

        Datums datums = mapper.toFormulier(spreadSheet).datums().getFirst();

        assertThat(datums.start().value()).isEqualTo(LocalDateTime.of(1900, 1, 1, 12, 0));
        assertThat(datums.eind().value()).isEqualTo(LocalDateTime.of(1900, 2, 1, 15, 0));
    }

    private static SpreadSheet datumsSpreadSheet(double start, double eind) {
        RowData header = new RowData(List.of(
                stringCell("kort"), stringCell("lang"), stringCell("start"), stringCell("eind")));
        RowData row = new RowData(List.of(
                stringCell(""), stringCell(""), numberCell(start), numberCell(eind)));
        Sheets sheet = new Sheets(new SheetProperties("datums", 3), List.of(new Data(List.of(header, row))));
        return new SpreadSheet(null, List.of(sheet));
    }

    private static Values stringCell(String value) {
        return new Values(new UserEnteredValue(value, null, null));
    }

    private static Values numberCell(double value) {
        return new Values(new UserEnteredValue(null, value, null));
    }

    private static <T> Cell<T> cell(T value) {
        return new Cell<>(value, null);
    }
}
