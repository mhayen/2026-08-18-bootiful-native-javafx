package nl.markhayen.desktop.formulier;

/**
 * A value read from a spreadsheet together with the A1 notation of the cell it came from, so it
 * can be written back to that exact cell later.
 */
public record Cell<T>(T value, String a1) {
}
