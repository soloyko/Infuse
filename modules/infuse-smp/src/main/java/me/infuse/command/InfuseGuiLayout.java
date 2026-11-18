package me.infuse.command;

/** Public access to the menu layout for tests. */
public final class InfuseGuiLayout {
    private InfuseGuiLayout() {}

    public static int[] layout(int n) {
        return InfuseGui.layout(n);
    }
}
