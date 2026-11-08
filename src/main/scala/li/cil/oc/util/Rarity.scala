package li.cil.oc.util

import net.minecraft.world.item.{Rarity => MCRarity}

object Rarity {
  private val lookup = Array(MCRarity.COMMON, MCRarity.UNCOMMON, MCRarity.RARE, MCRarity.EPIC)

  def byTier(tier: Int) = lookup(tier max 0 min (lookup.length - 1))
}
