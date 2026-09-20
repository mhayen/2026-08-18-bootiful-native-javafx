package nl.markhayen.desktop;

import javafx.application.Platform;

import java.util.function.Consumer;

public class Threads {

    static void offTheFxThread(Runnable runnable) {
        offTheFxThread(runnable, Throwable::printStackTrace);
    }

    static void offTheFxThread(Runnable runnable, Consumer<Throwable> onFailure) {
        Thread.ofVirtual() //
                .name("javafx-worker") //
                .start(() -> {
                    try {
                        runnable.run();
                    } catch (Exception ex) {
                        onTheFxThread(() -> onFailure.accept(ex));
                    }
                });
    }

    public static void onTheFxThread(Runnable runnable) {
        Platform.runLater(runnable);
    }

}
