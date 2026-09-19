package nl.markhayen.desktop.formulier;

import nl.markhayen.desktop.model.SpreadSheet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Navigatie eersteStap = formulier.navigatie().get(0);
        assertThat(eersteStap.volgorde()).isEqualTo(1);
        assertThat(eersteStap.sectie()).isEqualTo("start");
        assertThat(eersteStap.validatie()).isTrue();

        assertThat(formulier.secties()).isNotEmpty();
        Secties startSectie = formulier.secties().stream()
                .filter(s -> "start".equals(s.sectie()))
                .findFirst()
                .orElseThrow();
        assertThat(startSectie.velden()).extracting(Velden::naam).contains("naam");

        assertThat(formulier.instellingen()).isNotNull();
        assertThat(formulier.instellingen().apiToken()).isEqualTo("394f07cefce22fb3915e24a753db541bb5d66045");
        assertThat(formulier.instellingen().resultaatSpreadsheetId()).isEqualTo("kerstdagen");

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
                List.of(new Datums("24 dec", "24 december", "2026-12-24", "2026-12-24")),
                List.of("lunch", "diner"),
                new Afhankelijkheden(List.of("1", "2", "3")),
                new Instellingen(true, "logo.png", "Afzender", "Onderwerp", "antwoord@example.com",
                        "kopie@example.com", "token123", "spreadsheet-id"),
                List.of(new Navigatie(1, "start", "Start", true, "veld1", "cond", true, "stap1")));

        SpreadSheet spreadSheet = mapper.toSpreadSheet(formulier);
        Formulier result = mapper.toFormulier(spreadSheet);

        assertThat(result).isEqualTo(formulier);
    }
}
