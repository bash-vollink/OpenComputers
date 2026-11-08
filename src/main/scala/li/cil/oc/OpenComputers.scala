package li.cil.oc

import java.io.File

import li.cil.oc.common.gametest.BootstrapGameTests
import li.cil.oc.util.ThreadPoolFactory
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Main mod object. [[OpenComputersMod]] (the Java `@Mod` class) calls
 * [[OpenComputers.init]] when NeoForge constructs the mod.
 */
object OpenComputers {
  final val ID = "opencomputers"

  final val Name = "OpenComputers"

  final val McVersion = "1.21.1-neoforge"

  /** The mod version, as declared in neoforge.mods.toml. */
  @volatile var Version: String = "0.0.0"

  val log: Logger = LogManager.getLogger(Name)

  @volatile private var _initialized = false

  def initialized: Boolean = _initialized

  /** Version of the Scala library actually loaded at runtime. */
  def scalaVersion: String = scala.util.Properties.versionNumberString

  def init(modBus: IEventBus, container: ModContainer): Unit = {
    Version = container.getModInfo.getVersion.toString
    log.info(s"$Name $Version starting on Scala $scalaVersion")

    Settings.load(new File(FMLPaths.CONFIGDIR.get.toFile, "opencomputers" + File.separator + "settings.conf"))
    api.API.config = Settings.get.config

    modBus.addListener(classOf[FMLCommonSetupEvent], (_: FMLCommonSetupEvent) => log.info("Done with common setup."))
    modBus.addListener(classOf[RegisterGameTestsEvent], (event: RegisterGameTestsEvent) =>
      event.register(classOf[BootstrapGameTests]))

    NeoForge.EVENT_BUS.addListener(classOf[ServerStartingEvent], (e: ServerStartingEvent) => serverStart(e))
    NeoForge.EVENT_BUS.addListener(classOf[ServerStoppedEvent], (_: ServerStoppedEvent) => serverStop())

    _initialized = true
  }

  private def serverStart(e: ServerStartingEvent): Unit = {
    // TODO(port): register commands (RegisterCommandsEvent) once server/command is ported.
    ThreadPoolFactory.safePools.foreach(_.newThreadPool())

    if (Settings.get.internetAccessConfigured()) {
      if (Settings.get.internetFilteringRulesInvalid()) {
        log.warn("####################################################")
        log.warn("#                                                  #")
        log.warn("#  Could not parse Internet Card filtering rules!  #")
        log.warn("#  Review the server log and adjust the filtering  #")
        log.warn("#  list to ensure it is appropriately configured.  #")
        log.warn("# (config/opencomputers/settings.conf => filteringRules) #")
        log.warn("# Internet access has been automatically disabled. #")
        log.warn("#                                                  #")
        log.warn("####################################################")
      } else if (!Settings.get.internetFilteringRulesObserved && e.getServer.isDedicatedServer) {
        log.warn("####################################################")
        log.warn("#                                                  #")
        log.warn("#    It appears that you're running a dedicated    #")
        log.warn("#  server with OpenComputers installed! Make sure  #")
        log.warn("#  to review the Internet Card address filtering   #")
        log.warn("#  list to ensure it is appropriately configured.  #")
        log.warn("# (config/opencomputers/settings.conf => filteringRules) #")
        log.warn("#                                                  #")
        log.warn("####################################################")
      } else {
        log.info(f"Successfully applied ${Settings.get.internetFilteringRules.length} Internet Card filtering rules.")
      }
    }
  }

  private def serverStop(): Unit = {
    ThreadPoolFactory.safePools.foreach(_.waitForCompletion())
    // TODO(port): StaticSimpleEnvironment.onServerStopped() equivalent for the SimpleComponent driver.
  }
}
