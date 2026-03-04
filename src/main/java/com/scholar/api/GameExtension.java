package com.scholar.api;

import javafx.scene.layout.Region;

/**
 * GameExtension — contract every ScholarGrid game plugin JAR must implement.
 *
 * The host app:
 *   1. Loads the JAR via ServiceLoader or reflection.
 *   2. Calls getGameUI() to embed the returned Region in the game tab.
 *   3. Calls startGame() when the tab is shown.
 *   4. Calls stopGame() when the user leaves the tab or closes the app.
 *
 * IMPORTANT for implementors:
 *   • The constructor should only build the UI shell (no network calls).
 *   • startGame() is the real entry point — put initialisation logic there.
 *   • stopGame() MUST cancel all timers / background threads.
 *   • All UI updates must be dispatched via Platform.runLater() if they
 *     originate from a non-FX thread.
 */
public interface GameExtension {

    /** Human-readable game name shown in the game browser. */
    String getGameName();

    /** Developer / studio name. */
    String getDeveloperName();

    /** Semantic version string, e.g. "1.0.0". */
    String getVersion();

    /**
     * Returns the root UI node for this game.
     * Called once; the host embeds the result into a Tab or Pane.
     * Must never return null.
     */
    Region getGameUI();

    /**
     * Called by the host when the game tab becomes active.
     * Implementations should show the main menu / start screen here.
     */
    void startGame();

    /**
     * Called by the host when the user leaves the tab or the app shuts down.
     * Implementations MUST stop all AnimationTimers, ScheduledExecutors,
     * and HTTP polling here to prevent resource leaks.
     */
    void stopGame();
}