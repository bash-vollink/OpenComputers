package li.cil.oc.common.gametest;

import li.cil.oc.OpenComputers;
import li.cil.oc.OpenComputersMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sanity checks for the mod bootstrap. Run with {@code ./gradlew runGameTestServer}.
 * <p>
 * Tests use the {@code opencomputers:empty} template, a single block of air.
 */
@GameTestHolder(OpenComputersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    private BootstrapGameTests() {
    }

    @GameTest(template = "empty")
    public static void scalaRuntimeLoaded(final GameTestHelper helper) {
        helper.assertTrue(OpenComputers.initialized(), "Scala bootstrap did not run");
        final String version = OpenComputers.scalaVersion();
        helper.assertTrue(version.startsWith("2.13."), "Unexpected Scala runtime version: " + version);
        helper.succeed();
    }
}
