package nl.markhayen.desktop.formulier;

import nl.markhayen.desktop.model.Data;
import nl.markhayen.desktop.model.RowData;
import nl.markhayen.desktop.model.SheetProperties;
import nl.markhayen.desktop.model.Sheets;
import nl.markhayen.desktop.model.SpreadSheet;
import nl.markhayen.desktop.model.SpreadSheetProperties;
import nl.markhayen.desktop.model.UserEnteredValue;
import nl.markhayen.desktop.model.Values;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Maps a Google Sheets {@link SpreadSheet} (one row per record, first row is a header of column
 * names) to and from a {@link Formulier}. Sheets are matched by title ("velden", "navigatie",
 * "instellingen", "datums", "dagdelen", "afhankelijkheden"); header cells use snake_case and are
 * translated to/from the camelCase record component names.
 * <p>
 * {@link #toSpreadSheet(Formulier)} always emits every column a target record type has, so that
 * round-tripping a {@link Formulier} through {@link #toSpreadSheet} and back through
 * {@link #toFormulier} is lossless. {@link #toFormulier(SpreadSheet)} is tolerant of missing
 * sheets/columns (as in hand-authored sheets that don't have every column yet) and simply leaves
 * the corresponding fields {@code null}.
 */
@Component
public class FormulierMapper {

    private static final List<String> VELDEN_HEADER = List.of(
            "sectie", "sectie_naam", "titel", "subsectie", "veldnaam", "naam", "afkorting", "afkorting_kleur",
            "volgorde", "soort", "alleenlezen", "actief", "verplicht", "tekst", "beschrijving", "afhankelijk",
            "keuzes", "keuzewaarden", "exportvolgorde");

    private static final List<String> NAVIGATIE_HEADER = List.of(
            "volgorde", "sectie", "titel", "validatie", "conditie_veld", "condities", "actief", "stap");

    private static final List<String> INSTELLINGEN_HEADER = List.of(
            "actief", "logo_filename", "naam_afzender", "email_onderwerp", "antwoord_email", "kopie_naar",
            "api_token", "resultaat_spreadsheet_id");

    private static final List<String> DATUMS_HEADER = List.of("kort", "lang", "start", "eind");

    private static final List<String> DAGDELEN_HEADER = List.of("dagdeel");

    private static final List<String> AFHANKELIJKHEDEN_HEADER = List.of("aantal_kindermenus");

    public Formulier toFormulier(SpreadSheet spreadSheet) {
        String formulierNaam = spreadSheet.properties() == null ? null : spreadSheet.properties().title();
        return new Formulier(
                formulierNaam,
                readSecties(spreadSheet),
                readDatums(spreadSheet),
                readDagdelen(spreadSheet),
                readAfhankelijkheden(spreadSheet),
                readInstellingen(spreadSheet),
                readNavigatie(spreadSheet));
    }

    public SpreadSheet toSpreadSheet(Formulier formulier) {
        SpreadSheetProperties properties = new SpreadSheetProperties(formulier.formulierNaam());
        List<Sheets> sheets = List.of(
                writeVelden(formulier.secties()),
                writeNavigatie(formulier.navigatie()),
                writeInstellingen(formulier.instellingen()),
                writeDatums(formulier.datums()),
                writeDagdelen(formulier.dagdelen()),
                writeAfhankelijkheden(formulier.afhankelijkheden()));
        return new SpreadSheet(properties, sheets);
    }

    // ---- read: SpreadSheet -> Formulier -------------------------------------------------------

    private List<Secties> readSecties(SpreadSheet spreadSheet) {
        Optional<Sheets> sheet = findSheet(spreadSheet, "velden");
        if (sheet.isEmpty()) {
            return List.of();
        }
        Map<String, List<Velden>> veldenBySectie = new LinkedHashMap<>();
        Map<String, String> titelBySectie = new LinkedHashMap<>();
        Map<String, String> sectieNaamBySectie = new LinkedHashMap<>();
        for (Map<String, String> row : readRows(sheet.get())) {
            String sectie = str(row, "sectie");
            veldenBySectie.computeIfAbsent(sectie, k -> new ArrayList<>()).add(toVelden(row));
            titelBySectie.putIfAbsent(sectie, str(row, "titel"));
            sectieNaamBySectie.putIfAbsent(sectie, str(row, "sectieNaam"));
        }
        List<Secties> result = new ArrayList<>();
        for (String sectie : veldenBySectie.keySet()) {
            result.add(new Secties(titelBySectie.get(sectie), sectie, sectieNaamBySectie.get(sectie),
                    veldenBySectie.get(sectie)));
        }
        return result;
    }

    private Velden toVelden(Map<String, String> row) {
        return new Velden(
                str(row, "veldnaam"), str(row, "sectie"), str(row, "sectieNaam"), str(row, "subsectie"),
                str(row, "naam"), str(row, "afkorting"), str(row, "afkortingKleur"),
                integer(row, "volgorde"), str(row, "soort"), bool(row, "alleenlezen"), bool(row, "actief"),
                bool(row, "verplicht"), str(row, "tekst"), str(row, "beschrijving"), str(row, "afhankelijk"),
                str(row, "keuzes"), str(row, "keuzewaarden"), integer(row, "exportvolgorde"));
    }

    private List<Navigatie> readNavigatie(SpreadSheet spreadSheet) {
        return findSheet(spreadSheet, "navigatie")
                .map(this::readRows)
                .orElse(List.of())
                .stream()
                .map(row -> new Navigatie(
                        integer(row, "volgorde"), str(row, "sectie"), str(row, "titel"), bool(row, "validatie"),
                        str(row, "conditieVeld"), str(row, "condities"), bool(row, "actief"), str(row, "stap")))
                .toList();
    }

    private List<Datums> readDatums(SpreadSheet spreadSheet) {
        return findSheet(spreadSheet, "datums")
                .map(this::readRows)
                .orElse(List.of())
                .stream()
                .map(row -> new Datums(str(row, "kort"), str(row, "lang"), str(row, "start"), str(row, "eind")))
                .toList();
    }

    private List<String> readDagdelen(SpreadSheet spreadSheet) {
        return findSheet(spreadSheet, "dagdelen")
                .map(this::readRows)
                .orElse(List.of())
                .stream()
                .map(row -> str(row, "dagdeel"))
                .toList();
    }

    private Afhankelijkheden readAfhankelijkheden(SpreadSheet spreadSheet) {
        Optional<Sheets> sheet = findSheet(spreadSheet, "afhankelijkheden");
        if (sheet.isEmpty()) {
            return null;
        }
        List<String> aantalKindermenus = readRows(sheet.get()).stream()
                .map(row -> str(row, "aantalKindermenus"))
                .toList();
        return new Afhankelijkheden(aantalKindermenus);
    }

    private Instellingen readInstellingen(SpreadSheet spreadSheet) {
        Optional<Sheets> sheet = findSheet(spreadSheet, "instellingen");
        if (sheet.isEmpty()) {
            return null;
        }
        List<Map<String, String>> rows = readRows(sheet.get());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, String> row = rows.getFirst();
        String resultaatSpreadsheetId = row.get("resultaatSpreadsheetId");
        if (resultaatSpreadsheetId == null) {
            // legacy sheets name this column "result_spreadsheet" instead
            resultaatSpreadsheetId = row.get("resultSpreadsheet");
        }
        return new Instellingen(
                bool(row, "actief"), str(row, "logoFilename"), str(row, "naamAfzender"), str(row, "emailOnderwerp"),
                str(row, "antwoordEmail"), str(row, "kopieNaar"), str(row, "apiToken"), resultaatSpreadsheetId);
    }

    // ---- write: Formulier -> SpreadSheet -------------------------------------------------------

    private Sheets writeVelden(List<Secties> secties) {
        List<List<Object>> rows = new ArrayList<>();
        if (secties != null) {
            for (Secties sectie : secties) {
                List<Velden> velden = sectie.velden() == null ? List.of() : sectie.velden();
                for (Velden v : velden) {
                    rows.add(Arrays.asList(
                            sectie.sectie(), sectie.sectieNaam(), sectie.titel(),
                            v.subsectie(), v.veldnaam(), v.naam(), v.afkorting(), v.afkortingKleur(),
                            v.volgorde(), v.soort(), v.alleenlezen(), v.actief(), v.verplicht(), v.tekst(),
                            v.beschrijving(), v.afhankelijk(), v.keuzes(), v.keuzewaarden(), v.exportvolgorde()));
                }
            }
        }
        return sheet("velden", 0, VELDEN_HEADER, rows);
    }

    private Sheets writeNavigatie(List<Navigatie> navigatie) {
        List<List<Object>> rows = new ArrayList<>();
        if (navigatie != null) {
            for (Navigatie n : navigatie) {
                rows.add(Arrays.asList(n.volgorde(), n.sectie(), n.titel(), n.validatie(), n.conditieVeld(),
                        n.condities(), n.actief(), n.stap()));
            }
        }
        return sheet("navigatie", 1, NAVIGATIE_HEADER, rows);
    }

    private Sheets writeInstellingen(Instellingen instellingen) {
        List<List<Object>> rows = new ArrayList<>();
        if (instellingen != null) {
            rows.add(Arrays.asList(
                    instellingen.actief(), instellingen.logoFilename(), instellingen.naamAfzender(),
                    instellingen.emailOnderwerp(), instellingen.antwoordEmail(), instellingen.kopieNaar(),
                    instellingen.apiToken(), instellingen.resultaatSpreadsheetId()));
        }
        return sheet("instellingen", 2, INSTELLINGEN_HEADER, rows);
    }

    private Sheets writeDatums(List<Datums> datums) {
        List<List<Object>> rows = new ArrayList<>();
        if (datums != null) {
            for (Datums d : datums) {
                rows.add(Arrays.asList(d.kort(), d.lang(), d.start(), d.eind()));
            }
        }
        return sheet("datums", 3, DATUMS_HEADER, rows);
    }

    private Sheets writeDagdelen(List<String> dagdelen) {
        List<List<Object>> rows = new ArrayList<>();
        if (dagdelen != null) {
            for (String d : dagdelen) {
                rows.add(Collections.singletonList(d));
            }
        }
        return sheet("dagdelen", 4, DAGDELEN_HEADER, rows);
    }

    private Sheets writeAfhankelijkheden(Afhankelijkheden afhankelijkheden) {
        List<List<Object>> rows = new ArrayList<>();
        if (afhankelijkheden != null && afhankelijkheden.aantalKindermenus() != null) {
            for (String a : afhankelijkheden.aantalKindermenus()) {
                rows.add(Collections.singletonList(a));
            }
        }
        return sheet("afhankelijkheden", 5, AFHANKELIJKHEDEN_HEADER, rows);
    }

    // ---- generic sheet <-> rows-of-columns plumbing --------------------------------------------

    private static Optional<Sheets> findSheet(SpreadSheet spreadSheet, String title) {
        if (spreadSheet.sheets() == null) {
            return Optional.empty();
        }
        return spreadSheet.sheets().stream()
                .filter(s -> s.properties() != null && title.equalsIgnoreCase(s.properties().title()))
                .findFirst();
    }

    private List<Map<String, String>> readRows(Sheets sheet) {
        List<RowData> allRows = sheet.data() == null ? List.of() : sheet.data().stream()
                .filter(Objects::nonNull)
                .flatMap(d -> d.rowData() == null ? Stream.empty() : d.rowData().stream())
                .toList();
        if (allRows.isEmpty()) {
            return List.of();
        }
        RowData headerRow = allRows.get(0);
        int columnCount = headerRow.values() == null ? 0 : headerRow.values().size();
        List<String> headers = new ArrayList<>(columnCount);
        for (int c = 0; c < columnCount; c++) {
            headers.add(snakeToCamel(cellText(headerRow, c)));
        }
        List<Map<String, String>> rows = new ArrayList<>(allRows.size() - 1);
        for (int r = 1; r < allRows.size(); r++) {
            RowData dataRow = allRows.get(r);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), cellText(dataRow, c));
            }
            rows.add(row);
        }
        return rows;
    }

    private static Sheets sheet(String title, int index, List<String> header, List<List<Object>> dataRows) {
        List<RowData> rows = new ArrayList<>(dataRows.size() + 1);
        rows.add(rowOf(header.stream().<Object>map(h -> h).toList()));
        for (List<Object> dataRow : dataRows) {
            rows.add(rowOf(dataRow));
        }
        return new Sheets(new SheetProperties(title, index), List.of(new Data(rows)));
    }

    private static RowData rowOf(List<Object> values) {
        return new RowData(values.stream().map(FormulierMapper::cell).toList());
    }

    private static Values cell(Object value) {
        return new Values(toUserEnteredValue(value));
    }

    private static UserEnteredValue toUserEnteredValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return new UserEnteredValue(null, null, b);
        }
        if (value instanceof Number n) {
            return new UserEnteredValue(null, n.doubleValue(), null);
        }
        return new UserEnteredValue(String.valueOf(value), null, null);
    }

    /**
     * Reads a cell's text from whichever of {@code stringValue}/{@code numberValue}/{@code boolValue}
     * is set on its {@code userEnteredValue} - Google Sheets stores the entered value under the field
     * matching the cell's actual type rather than always as text.
     */
    private static String cellText(RowData row, int column) {
        List<Values> values = row.values();
        if (values == null || column >= values.size()) {
            return null;
        }
        Values value = values.get(column);
        if (value == null || value.userEnteredValue() == null) {
            return null;
        }
        UserEnteredValue entered = value.userEnteredValue();
        if (entered.stringValue() != null) {
            return entered.stringValue();
        }
        if (entered.boolValue() != null) {
            return entered.boolValue() ? "TRUE" : "FALSE";
        }
        if (entered.numberValue() != null) {
            return formatNumber(entered.numberValue());
        }
        return null;
    }

    private static String formatNumber(double number) {
        if (number == Math.rint(number) && !Double.isInfinite(number)) {
            return String.valueOf((long) number);
        }
        return String.valueOf(number);
    }

    private static String snakeToCamel(String snakeCase) {
        if (snakeCase == null) {
            return null;
        }
        String[] parts = snakeCase.trim().split("_");
        StringBuilder camelCase = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                camelCase.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1).toLowerCase());
            }
        }
        return camelCase.toString();
    }

    private static String str(Map<String, String> row, String key) {
        return row.get(key);
    }

    private static Integer integer(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "TRUE", "WAAR", "1" -> Boolean.TRUE;
            case "FALSE", "ONWAAR", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
