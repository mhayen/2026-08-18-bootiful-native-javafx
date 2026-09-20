package nl.markhayen.desktop;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import nl.markhayen.desktop.formulier.Cell;
import nl.markhayen.desktop.formulier.Datums;
import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.Navigatie;
import nl.markhayen.desktop.remote.GoogleDriveService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
class FormulierView {

    private static final Logger log = LoggerFactory.getLogger(FormulierView.class);
    private static final Resource FXML = new ClassPathResource("/fxml/formulier-view.fxml");
    private static final LocalDate SERIAL_NUMBER_EPOCH = LocalDate.of(1899, 12, 30);
    HtmlGenerator htmlGenerator;

    public FormulierView(HtmlGenerator htmlGenerator) {
        this.htmlGenerator = htmlGenerator;
    }

    Node build(String spreadsheetId, Formulier formulier, GoogleDriveService googleDrive, Label status,
                       Runnable reload) {
        Parent root;
        try (var fxmlInputStream = FXML.getInputStream()) {
            root = new FXMLLoader().load(fxmlInputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, Object> pending = new LinkedHashMap<>();
        Map<String, String> original = new HashMap<>();

        var datumsItems = FXCollections.observableArrayList(formulier.datums());
        var navigatieItems = FXCollections.observableArrayList(formulier.navigatie());

        ((Label) root.lookup("#naam")).setText(formulier.formulierNaam());
        ((VBox) root.lookup("#instellingenBody")).getChildren()
                .setAll(instellingenView(formulier, pending, original));
        ((VBox) root.lookup("#datumsBody")).getChildren().setAll(datumsSection(datumsItems, pending, original));
        ((VBox) root.lookup("#navigatieBody")).getChildren()
                .setAll(navigatieSection(navigatieItems, pending, original));

        var save = (Button) root.lookup("#save");
        save.setOnAction(_ -> save(spreadsheetId, googleDrive, save, pending, datumsItems, navigatieItems, reload));
        var updateTeksten = (Button) root.lookup("#updateTeksten");
        updateTeksten.setOnAction(_ -> runUpdateTeksten(formulier.formulierNaam(), googleDrive, status));
        var generateHtml = (Button) root.lookup("#generateHtml");
        generateHtml.setOnAction(_ -> runGenerateHtml(formulier.formulierNaam(), status));

        var scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private void runGenerateHtml(String formulierNaam, Label status) {
        Threads.offTheFxThread(() -> {
            var ref = new Object() {
                String html = null;
            };
            try {
                ref.html = htmlGenerator.generateHtml(formulierNaam);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Threads.onTheFxThread(() -> {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(ref.html);
                clipboard.setContent(content);
                status.setText("HTML generated and saved");
            });
        });
    }

    private static void save(String spreadsheetId, GoogleDriveService googleDrive, Button save,
                              Map<String, Object> pending, ObservableList<Datums> datumsItems,
                              ObservableList<Navigatie> navigatieItems, Runnable reload) {
        var changes = Map.copyOf(pending);
        var newDatums = datumsItems.stream().filter(FormulierView::isNew).map(FormulierView::datumsRow).toList();
        var newNavigatie = navigatieItems.stream().filter(FormulierView::isNew).map(FormulierView::navigatieRow).toList();
        if (changes.isEmpty() && newDatums.isEmpty() && newNavigatie.isEmpty()) {
            return;
        }
        save.setDisable(true);
        Threads.offTheFxThread(() -> {
            if (!changes.isEmpty()) {
                googleDrive.updateCells(spreadsheetId, changes);
            }
            googleDrive.appendRows(spreadsheetId, "datums", newDatums);
            googleDrive.appendRows(spreadsheetId, "navigatie", newNavigatie);
            Threads.onTheFxThread(() -> {
                pending.clear();
                save.setDisable(false);
                reload.run();
            });
        }, _ -> save.setDisable(false));
    }

    private static void runUpdateTeksten(String formulierNaam, GoogleDriveService googleDrive, Label status) {
        Threads.onTheFxThread(() -> status.setText("Started update teksten"));
        String s = googleDrive.runUpdateTeksten(formulierNaam);
        Threads.onTheFxThread(() -> status.setText(s));
    }

    /**
     * Records {@code newValue} as a pending, unsaved change for {@code a1}, unless it matches the
     * value the cell had when the formulier was loaded, in which case any earlier pending edit for
     * that cell is dropped. Rows that don't exist on the sheet yet (added via "Add row", not yet
     * saved) carry no A1, so edits to them are simply left in place in the row itself and are only
     * sent to the server as part of the append batch on save.
     */
    private static void track(Map<String, Object> pending, Map<String, String> original, String a1,
                               String newValue) {
        if (a1 == null) {
            return;
        }
        if (Objects.equals(newValue, original.get(a1))) {
            pending.remove(a1);
        } else {
            pending.put(a1, newValue);
        }
    }

    private static void recordOriginal(Map<String, String> original, Cell<?> cell) {
        if (cell != null && cell.a1() != null) {
            original.putIfAbsent(cell.a1(), raw(cell.value()));
        }
    }

    private static boolean isNew(Datums d) {
        return d.kort().a1() == null && d.lang().a1() == null && d.start().a1() == null && d.eind().a1() == null;
    }

    private static boolean isNew(Navigatie n) {
        return n.volgorde().a1() == null && n.sectie().a1() == null && n.titel().a1() == null
                && n.validatie().a1() == null && n.conditieVeld().a1() == null && n.condities().a1() == null
                && n.actief().a1() == null && n.stap().a1() == null;
    }

    private static List<Object> datumsRow(Datums d) {
        return List.of(orBlank(d.kort().value()), orBlank(d.lang().value()),
                orBlank(toSerialNumberOrNull(d.start().value())), orBlank(toSerialNumberOrNull(d.eind().value())));
    }

    private static List<Object> navigatieRow(Navigatie n) {
        return List.of(orBlank(n.volgorde().value()), orBlank(n.sectie().value()), orBlank(n.titel().value()),
                orBlank(n.validatie().value()), orBlank(n.conditieVeld().value()), orBlank(n.condities().value()),
                orBlank(n.actief().value()), orBlank(n.stap().value()));
    }

    private static Object orBlank(@Nullable Object value) {
        return value == null ? "" : value;
    }

    private static @Nullable Double toSerialNumberOrNull(@Nullable LocalDateTime value) {
        if (value == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(SERIAL_NUMBER_EPOCH, value.toLocalDate());
        double fractionOfDay = value.toLocalTime().toSecondOfDay() / 86_400d;
        return days + fractionOfDay;
    }

    private static Node instellingenView(Formulier formulier, Map<String, Object> pending,
                                          Map<String, String> original) {
        var instellingen = formulier.instellingen();
        if (instellingen == null) {
            return new Label("(geen instellingen)");
        }
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        int row = 0;
        row = checkBoxRow(grid, row, "Actief", instellingen.actief(), pending, original);
        row = textRow(grid, row, "Logo", instellingen.logoFilename(), pending, original);
        row = textRow(grid, row, "Naam afzender", instellingen.naamAfzender(), pending, original);
        row = textRow(grid, row, "E-mail onderwerp", instellingen.emailOnderwerp(), pending, original);
        row = textRow(grid, row, "Antwoord e-mail", instellingen.antwoordEmail(), pending, original);
        row = textRow(grid, row, "Kopie naar", instellingen.kopieNaar(), pending, original);
        row = textRow(grid, row, "API token", instellingen.apiToken(), pending, original);
        textRow(grid, row, "Resultaat spreadsheet", instellingen.resultaatSpreadsheetId(), pending, original);
        return grid;
    }

    private static int textRow(GridPane grid, int row, String label, Cell<String> cell,
                                Map<String, Object> pending, Map<String, String> original) {
        var key = new Label(label + ":");
        key.getStyleClass().add("subtle");
        var field = new TextField(cell.value() == null ? "" : cell.value());
        recordOriginal(original, cell);
        field.textProperty().addListener((_, _, newValue) -> track(pending, original, cell.a1(), newValue));
        grid.addRow(row, key, field);
        return row + 1;
    }

    private static int checkBoxRow(GridPane grid, int row, String label, Cell<Boolean> cell,
                                    Map<String, Object> pending, Map<String, String> original) {
        var key = new Label(label + ":");
        key.getStyleClass().add("subtle");
        var box = new CheckBox();
        recordOriginal(original, cell);
        box.setSelected(Boolean.TRUE.equals(cell.value()));
        box.selectedProperty().addListener((_, _, selected) -> track(pending, original, cell.a1(), raw(selected)));
        grid.addRow(row, key, box);
        return row + 1;
    }

    private static Node datumsSection(ObservableList<Datums> items, Map<String, Object> pending,
                                       Map<String, String> original) {
        var table = new TableView<>(items);
        table.setEditable(true);
        table.getColumns().add(editableColumn("Kort", Datums::kort, Function.identity(),
                (d, c) -> new Datums(c, d.lang(), d.start(), d.eind()), items, pending, original));
        table.getColumns().add(editableColumn("Lang", Datums::lang, Function.identity(),
                (d, c) -> new Datums(d.kort(), c, d.start(), d.eind()), items, pending, original));
        table.getColumns().add(editableColumn("Start", Datums::start, FormulierView::parseDateTime,
                (d, c) -> new Datums(d.kort(), d.lang(), c, d.eind()), items, pending, original));
        table.getColumns().add(editableColumn("Eind", Datums::eind, FormulierView::parseDateTime,
                (d, c) -> new Datums(d.kort(), d.lang(), d.start(), c), items, pending, original));
        table.getColumns().add(deleteColumn(items,
                d -> List.of(d.kort(), d.lang(), d.start(), d.eind()), pending));
        table.prefHeightProperty().bind(Bindings.size(items).multiply(28).add(32));

        var addRow = new Button("Add row");
        addRow.setOnAction(_ -> items.add(new Datums(blankCell(), blankCell(), blankCell(), blankCell())));

        return new VBox(8, table, addRow);
    }

    private static Node navigatieSection(ObservableList<Navigatie> items, Map<String, Object> pending,
                                          Map<String, String> original) {
        var table = new TableView<>(items);
        table.setEditable(true);
        table.getColumns().add(editableColumn("Volgorde", Navigatie::volgorde, FormulierView::parseInteger,
                (n, c) -> new Navigatie(c, n.sectie(), n.titel(), n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Sectie", Navigatie::sectie, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), c, n.titel(), n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Titel", Navigatie::titel, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), c, n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Validatie", Navigatie::validatie, FormulierView::parseBoolean,
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), n.titel(), c, n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Conditie Veld", Navigatie::conditieVeld, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), n.titel(), n.validatie(), c,
                        n.condities(), n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Conditie", Navigatie::condities, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), n.titel(), n.validatie(), n.conditieVeld(),
                        c, n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(deleteColumn(items,
                n -> List.of(n.volgorde(), n.sectie(), n.titel(), n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), pending));
        table.prefHeightProperty().bind(Bindings.size(items).multiply(28).add(32));

        var addRow = new Button("Add row");
        addRow.setOnAction(_ -> items.add(new Navigatie(blankCell(), blankCell(), blankCell(), blankCell(),
                blankCell(), blankCell(), blankCell(), blankCell())));

        return new VBox(8, table, addRow);
    }

    private static <T> Cell<T> blankCell() {
        return new Cell<>(null, null);
    }

    /**
     * A "Delete" button column. Removes the row from {@code items} immediately; if the row already
     * exists on the sheet (any of its cells has a real A1), also queues every one of its cells to be
     * blanked out on save - rows are never structurally removed from the sheet, so a deleted row's
     * cells are simply cleared and {@link nl.markhayen.desktop.formulier.FormulierMapper} skips
     * fully-blank rows when reading, so it doesn't resurface.
     */
    private static <T> TableColumn<T, Void> deleteColumn(ObservableList<T> items,
                                                          Function<T, List<Cell<?>>> cellsOf,
                                                          Map<String, Object> pending) {
        var column = new TableColumn<T, Void>("");
        column.setCellFactory(_ -> new TableCell<>() {
            private final Button delete = new Button("🗑");

            {
                delete.getStyleClass().add("icon-button");
                delete.setTooltip(new Tooltip("Delete row"));
                delete.setOnAction(_ -> {
                    int index = getIndex();
                    var item = items.get(index);
                    for (Cell<?> cell : cellsOf.apply(item)) {
                        if (cell.a1() != null) {
                            pending.put(cell.a1(), "");
                        }
                    }
                    items.remove(index);
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : delete);
            }
        });
        return column;
    }

    /**
     * A text-editable table column bound to a {@link Cell}-valued record field. Editing a row
     * parses the typed text via {@code parse} and replaces the row in {@code items} with a copy
     * carrying the edited {@link Cell} (same A1, new value) via {@code setter}, and records the
     * raw typed text as a pending change.
     */
    private static <T, V> TableColumn<T, String> editableColumn(String title, Function<T, Cell<V>> getter,
                                                                  Function<String, V> parse,
                                                                  BiFunction<T, Cell<V>, T> setter,
                                                                  ObservableList<T> items, Map<String, Object> pending,
                                                                  Map<String, String> original) {
        for (T item : items) {
            recordOriginal(original, getter.apply(item));
        }
        var column = new TableColumn<T, String>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(raw(getter.apply(data.getValue()).value())));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        column.setOnEditCommit(event -> {
            int index = event.getTablePosition().getRow();
            T oldItem = items.get(index);
            Cell<V> cell = getter.apply(oldItem);
            String newValue = event.getNewValue();
            items.set(index, setter.apply(oldItem, new Cell<>(parse.apply(newValue), cell.a1())));
            track(pending, original, cell.a1(), newValue);
        });
        return column;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            log.warn("Could not parse integer from '{}'", value);
            return null;
        }
    }

    private static @Nullable Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "TRUE", "WAAR", "1" -> Boolean.TRUE;
            case "FALSE", "ONWAAR", "0" -> Boolean.FALSE;
            default -> {
                log.warn("Could not parse boolean from '{}'", value);
                yield null;
            }
        };
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException _) {
            try {
                return LocalDate.parse(trimmed).atStartOfDay();
            } catch (DateTimeParseException _) {
                log.warn("Could not parse date/time from '{}'", value);
                return null;
            }
        }
    }

    /**
     * The string form a value would have on the spreadsheet, e.g. {@code TRUE}/{@code FALSE} for
     * booleans - used both to seed the "original" baseline and as the text sent back on save.
     */
    private static String raw(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return Boolean.TRUE.equals(b) ? "TRUE" : "FALSE";
        }
        return value.toString();
    }

}
