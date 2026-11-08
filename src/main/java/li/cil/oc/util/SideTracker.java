package li.cil.oc.util;

import net.neoforged.fml.util.thread.EffectiveSide;

public final class SideTracker {
    public static boolean isServer() {
        return EffectiveSide.get().isServer();
    }

    public static boolean isClient() {
        return !isServer();
    }
}
