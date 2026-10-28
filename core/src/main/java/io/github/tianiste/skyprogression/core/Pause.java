package io.github.tianiste.skyprogression.core;

/**
 * Points in long computations (each scoring of upgrade candidates, each method of a comparison, each HOTM plan) where the
 * calling thread may wait. The relay sets a hook there that holds back pages it builds ahead while a visitor's page is
 * being built, so on its single CPU the page someone asked for goes first. No hook (the mod): nothing happens.
 */
public final class Pause {
    private Pause() {}
    public static volatile Runnable hook;
    static void point() {
        Runnable h = hook;
        if (h != null) h.run();
    }
}
