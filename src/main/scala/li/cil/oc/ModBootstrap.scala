package li.cil.oc

import li.cil.oc.common.gametest.BootstrapGameTests
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Scala-side entry point, called from [[OpenComputersMod]].
 *
 * For now this only proves that the bundled Scala runtime loads inside
 * NeoForge. As subsystems are ported, their setup gets wired in here.
 */
object ModBootstrap {
  final val Name = "OpenComputers"

  val log: Logger = LogManager.getLogger(Name)

  @volatile private var _initialized = false

  def initialized: Boolean = _initialized

  /** Version of the Scala library actually loaded at runtime. */
  def scalaVersion: String = scala.util.Properties.versionNumberString

  def init(modBus: IEventBus, container: ModContainer): Unit = {
    // Deliberately exercise the Scala standard library (collections, string
    // interpolation), so a missing or mismatched runtime fails right here.
    val features = List("collections", "interpolation", "closures").map(_.capitalize)
    log.info(s"$Name ${container.getModInfo.getVersion} starting on Scala $scalaVersion (${features.mkString(", ")} OK)")

    modBus.addListener(classOf[RegisterGameTestsEvent], (event: RegisterGameTestsEvent) =>
      event.register(classOf[BootstrapGameTests]))

    _initialized = true
  }
}
