package nl.markhayen.desktop.formulier;

import nl.markhayen.desktop.model.Data;
import nl.markhayen.desktop.model.RowData;
import nl.markhayen.desktop.model.SheetProperties;
import nl.markhayen.desktop.model.Sheets;
import nl.markhayen.desktop.model.SpreadSheet;
import nl.markhayen.desktop.model.SpreadSheetProperties;
import nl.markhayen.desktop.model.UserEnteredValue;
import nl.markhayen.desktop.model.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Maps a Google Sheets {@link SpreadSheet} (one row per record, first row is a header of column
 * names) to and from a {@link Formulier}. Sheets are matched by title ("velden", "navigatie",
 * "instellingen", "datums", "dagdelen", "afhankelijkheden"); header cells use snake_case and are
 * translated to/from the camelCase record component names.
 * <p>
 * Every editable scalar field is read into a {@link Cell}, which carries the A1 notation of the
 * cell it came from (e.g. {@code "instellingen!B2"}) alongside its value, so a later edit can be
 * written back to exactly that cell.
 * <p>
 * {@link #toSpreadSheet(Formulier)} always emits every column a target record type has, so that
 * round-tripping a {@link Formulier} through {@link #toSpreadSheet} and back through
 * {@link #toFormulier} is lossless. {@link #toFormulier(SpreadSheet)} is tolerant of missing
 * sheets/columns (as in hand-authored sheets that don't have every column yet) and simply leaves
 * the corresponding fields {@code null}.
 */
@Component
public class FormulierMapper {
    private static final Logger log = LoggerFactory.getLogger(FormulierMapper.class);
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

    /**
     * Google Sheets' SERIAL_NUMBER epoch: day 0 is December 30th 1899.
     */
    private static final LocalDate SERIAL_NUMBER_EPOCH = LocalDate.of(1899, 12, 30);
    private static final double SECONDS_PER_DAY = 24 * 60 * 60d;

    public Formulier toFormulier(SpreadSheet spreadSheet) {
        String formulierNaam = spreadSheet.properties() == null ? null : spreadSheet.properties().title();
        return new Formulier(
                formulierNaam,
                readSecties(spreadSheet),
                readDatums(spreadSheet),
                readInstellingen(spreadSheet),
                readNavigatie(spreadSheet));
    }

    public SpreadSheet toSpreadSheet(Formulier formulier) {
        SpreadSheetProperties properties = new SpreadSheetProperties(formulier.formulierNaam());
        List<Sheets> sheets = List.of(
                writeVelden(formulier.secties()),
                writeNavigatie(formulier.navigatie()),
                writeInstellingen(formulier.instellingen()),
                writeDatums(formulier.datums()));
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
        for (Map<String, CellValue> row : readRows(sheet.get())) {
            String sectie = str(row, "sectie");
            veldenBySectie.computeIfAbsent(sectie, _ -> new ArrayList<>()).add(toVelden(row));
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

    private Velden toVelden(Map<String, CellValue> row) {
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
                .filter(FormulierMapper::hasAnyValue)
                .map(row -> new Navigatie(
                        cell(row, "volgorde", FormulierMapper::toInteger), cell(row, "sectie"), cell(row, "titel"),
                        cell(row, "validatie", FormulierMapper::toBoolean), cell(row, "conditieVeld"),
                        cell(row, "condities"), cell(row, "actief", FormulierMapper::toBoolean), cell(row, "stap")))
                .toList();
    }

    private List<Datums> readDatums(SpreadSheet spreadSheet) {
        return findSheet(spreadSheet, "datums")
                .map(this::readRows)
                .orElse(List.of())
                .stream()
                .filter(FormulierMapper::hasAnyValue)
                .map(row -> new Datums(cell(row, "kort"), cell(row, "lang"),
                        cell(row, "start", FormulierMapper::toDateTime), cell(row, "eind", FormulierMapper::toDateTime)))
                .toList();
    }

    private Instellingen readInstellingen(SpreadSheet spreadSheet) {
        Optional<Sheets> sheet = findSheet(spreadSheet, "instellingen");
        if (sheet.isEmpty()) {
            return null;
        }
        List<Map<String, CellValue>> rows = readRows(sheet.get());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, CellValue> row = rows.getFirst();
        CellValue resultaatSpreadsheetId = row.get("resultaatSpreadsheetId");
        if (resultaatSpreadsheetId == null) {
            // legacy sheets name this column "result_spreadsheet" instead
            resultaatSpreadsheetId = row.get("resultSpreadsheet");
        }
        return new Instellingen(
                cell(row, "actief", FormulierMapper::toBoolean), cell(row, "logoFilename"),
                cell(row, "naamAfzender"), cell(row, "emailOnderwerp"), cell(row, "antwoordEmail"),
                cell(row, "kopieNaar"), cell(row, "apiToken"), toCell(resultaatSpreadsheetId));
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
                rows.add(Arrays.asList(value(n.volgorde()), value(n.sectie()), value(n.titel()),
                        value(n.validatie()), value(n.conditieVeld()), value(n.condities()), value(n.actief()),
                        value(n.stap())));
            }
        }
        return sheet("navigatie", 1, NAVIGATIE_HEADER, rows);
    }

    private Sheets writeInstellingen(Instellingen instellingen) {
        List<List<Object>> rows = new ArrayList<>();
        if (instellingen != null) {
            rows.add(Arrays.asList(
                    value(instellingen.actief()), value(instellingen.logoFilename()),
                    value(instellingen.naamAfzender()), value(instellingen.emailOnderwerp()),
                    value(instellingen.antwoordEmail()), value(instellingen.kopieNaar()),
                    value(instellingen.apiToken()), value(instellingen.resultaatSpreadsheetId())));
        }
        return sheet("instellingen", 2, INSTELLINGEN_HEADER, rows);
    }

    private Sheets writeDatums(List<Datums> datums) {
        List<List<Object>> rows = new ArrayList<>();
        if (datums != null) {
            for (Datums d : datums) {
                rows.add(Arrays.asList(value(d.kort()), value(d.lang()), value(d.start()), value(d.eind())));
            }
        }
        return sheet("datums", 3, DATUMS_HEADER, rows);
    }

    private static <T> T value(Cell<T> cell) {
        return cell == null ? null : cell.value();
    }

    // ---- generic sheet <-> rows-of-columns plumbing --------------------------------------------

    /**
     * A cell's text value together with the A1 notation of the cell it was read from.
     */
    private record CellValue(String text, String a1) {
    }

    private static Cell<String> cell(Map<String, CellValue> row, String key) {
        return cell(row, key, s -> s);
    }

    private static <T> Cell<T> cell(Map<String, CellValue> row, String key, Function<String, T> parse) {
        CellValue cellValue = row.get(key);
        return toCell(cellValue, parse);
    }

    private static Cell<String> toCell(CellValue cellValue) {
        return toCell(cellValue, s -> s);
    }

    private static <T> Cell<T> toCell(CellValue cellValue, Function<String, T> parse) {
        if (cellValue == null) {
            return new Cell<>(null, null);
        }
        return new Cell<>(parse.apply(cellValue.text()), cellValue.a1());
    }

    /**
     * A row that's been cleared (e.g. via a per-cell delete that blanks every cell instead of
     * removing the row) should be treated as absent rather than resurfacing as an all-null record.
     */
    private static boolean hasAnyValue(Map<String, CellValue> row) {
        return row.values().stream().anyMatch(cv -> cv != null && cv.text() != null && !cv.text().isBlank());
    }

    private static Optional<Sheets> findSheet(SpreadSheet spreadSheet, String title) {
        if (spreadSheet.sheets() == null) {
            return Optional.empty();
        }
        return spreadSheet.sheets().stream()
                .filter(s -> s.properties() != null && title.equalsIgnoreCase(s.properties().title()))
                .findFirst();
    }

    private List<Map<String, CellValue>> readRows(Sheets sheet) {
        List<RowData> allRows = sheet.data() == null ? List.of() : sheet.data().stream()
                .filter(Objects::nonNull)
                .flatMap(d -> d.rowData() == null ? Stream.empty() : d.rowData().stream())
                .toList();
        if (allRows.isEmpty()) {
            return List.of();
        }
        RowData headerRow = allRows.getFirst();
        int columnCount = headerRow.values() == null ? 0 : headerRow.values().size();
        List<String> headers = new ArrayList<>(columnCount);
        for (int c = 0; c < columnCount; c++) {
            headers.add(snakeToCamel(cellText(headerRow, c)));
        }
        String sheetTitle = sheet.properties() == null ? null : sheet.properties().title();
        List<Map<String, CellValue>> rows = new ArrayList<>(allRows.size() - 1);
        for (int r = 1; r < allRows.size(); r++) {
            RowData dataRow = allRows.get(r);
            int sheetRowNumber = r + 1;
            Map<String, CellValue> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String a1 = sheetTitle == null ? null : sheetTitle + "!" + columnLetter(c) + sheetRowNumber;
                row.put(headers.get(c), new CellValue(cellText(dataRow, c), a1));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Converts a zero-based column index to its spreadsheet letter (0 -> A, 25 -> Z, 26 -> AA, ...).
     */
    private static String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        int n = index;
        do {
            letters.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return letters.toString();
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
        return new RowData(values.stream().map(FormulierMapper::cellOf).toList());
    }

    private static Values cellOf(Object value) {
        return new Values(toUserEnteredValue(value));
    }

    private static UserEnteredValue toUserEnteredValue(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> new UserEnteredValue(null, null, b);
            case LocalDateTime dateTime -> new UserEnteredValue(null, toSerialNumber(dateTime), null);
            case Number n -> new UserEnteredValue(null, n.doubleValue(), null);
            default -> new UserEnteredValue(String.valueOf(value), null, null);
        };
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

    private static String str(Map<String, CellValue> row, String key) {
        CellValue cellValue = row.get(key);
        return cellValue == null ? null : cellValue.text();
    }

    private static Integer integer(Map<String, CellValue> row, String key) {
        return toInteger(str(row, key));
    }

    private static Integer toInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Parses a Google Sheets SERIAL_NUMBER value - a double whose whole part counts days since
     * December 30th 1899 and whose fractional part counts the time of day - falling back to a full
     * {@code LocalDateTime} or a bare date (taken as midnight) for hand-authored, unformatted cells
     * that hold text instead of a real date-formatted cell.
     */
    private static LocalDateTime toDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return fromSerialNumber(Double.parseDouble(trimmed));
        } catch (NumberFormatException _) {
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException _) {
                try {
                    return LocalDate.parse(trimmed).atStartOfDay();
                } catch (DateTimeParseException _) {
                    log.warn("Could not parse date from '{}'", value);
                    return null;
                }
            }
        }
    }

    private static LocalDateTime fromSerialNumber(double serialNumber) {
        long days = (long) Math.floor(serialNumber);
        long secondsOfDay = Math.round((serialNumber - days) * SECONDS_PER_DAY);
        return SERIAL_NUMBER_EPOCH.plusDays(days).atStartOfDay().plusSeconds(secondsOfDay);
    }

    private static double toSerialNumber(LocalDateTime dateTime) {
        long days = ChronoUnit.DAYS.between(SERIAL_NUMBER_EPOCH, dateTime.toLocalDate());
        double fractionOfDay = dateTime.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY;
        return days + fractionOfDay;
    }

    private static Boolean bool(Map<String, CellValue> row, String key) {
        return toBoolean(str(row, key));
    }

    private static Boolean toBoolean(String value) {
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
