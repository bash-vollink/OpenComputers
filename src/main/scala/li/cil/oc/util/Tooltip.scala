package li.cil.oc.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Style

import scala.jdk.CollectionConverters._

object Tooltip {
  private val maxWidth = 220

  /**
   * Whether extended tooltips are currently shown, and the name of the key
   * that shows them. Set by the client proxy; defaults are for servers.
   */
  var showExtendedTooltips: () => Boolean = () => false
  var extendedTooltipKeyName: () => String = () => "Shift"

  // Only touched on the client: tooltips are never built on a dedicated server.
  private def font = Option(Minecraft.getInstance).map(_.font).orNull

  private def wrap(line: String): Seq[String] =
    font.getSplitter.splitLines(line, maxWidth, Style.EMPTY).asScala.map(_.getString.trim() + " ").toSeq

  def get(name: String, args: Any*): java.util.List[String] = {
    if (!Localization.canLocalize(Settings.namespace + "tooltip." + name)) return Seq.empty[String].asJava
    val tooltip = Localization.localizeImmediately("tooltip." + name).
      format(args.map(_.toString): _*)
    if (font == null) return tooltip.linesIterator.toList.asJava // Some mods request tooltips before the font is available.
    val isSubTooltip = name.contains(".")
    val shouldShorten = (isSubTooltip || font.width(tooltip) > maxWidth) && !showExtendedTooltips()
    if (shouldShorten) {
      if (isSubTooltip) Seq.empty[String].asJava
      else Seq(Localization.localizeImmediately("tooltip.toolong", extendedTooltipKeyName())).asJava
    }
    else tooltip.linesIterator.flatMap(wrap).toList.asJava
  }

  def extended(name: String, args: Any*): java.util.List[String] =
    if (showExtendedTooltips() && font != null) {
      Localization.localizeImmediately("tooltip." + name).
        format(args.map(_.toString): _*).
        linesIterator.flatMap(wrap).toList.asJava
    }
    else Seq.empty[String].asJava
}
