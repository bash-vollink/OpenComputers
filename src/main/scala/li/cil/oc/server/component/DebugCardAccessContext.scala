package li.cil.oc.server.component

import li.cil.oc.Settings
import net.minecraft.nbt.CompoundTag

// Lives outside of DebugCard so that Settings can use it without pulling in
// the whole component. DebugCard re-exports it as DebugCard.AccessContext.

object DebugCardAccessContext {
  def remove(nbt: CompoundTag): Unit = {
    nbt.remove(Settings.namespace + "player")
    nbt.remove(Settings.namespace + "accessNonce")
  }

  def load(nbt: CompoundTag): Option[DebugCardAccessContext] = {
    if (nbt.contains(Settings.namespace + "player"))
      Some(DebugCardAccessContext(
        nbt.getString(Settings.namespace + "player"),
        nbt.getString(Settings.namespace + "accessNonce")
      ))
    else
      None
  }
}

case class DebugCardAccessContext(player: String, nonce: String) {
  def save(nbt: CompoundTag): Unit = {
    nbt.putString(Settings.namespace + "player", player)
    nbt.putString(Settings.namespace + "accessNonce", nonce)
  }
}
