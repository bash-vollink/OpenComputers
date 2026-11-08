package li.cil.oc;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point.
 * <p>
 * NeoForge's {@code javafml} loader only knows how to construct Java classes,
 * so this thin class exists purely to hand control to the Scala side of the
 * mod. Keep logic out of here.
 */
@Mod(OpenComputersMod.MOD_ID)
public final class OpenComputersMod {
    public static final String MOD_ID = "opencomputers";

    public OpenComputersMod(final IEventBus modBus, final ModContainer container) {
        ModBootstrap.init(modBus, container);
    }
}
