package li.cil.oc.util

import net.minecraft.world.item.ItemStack
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.DyedItemColor

/**
  * Item colors live in the vanilla DYED_COLOR component since 1.20.5.
  *
  * @author asie, Vexatos
  */
object ItemColorizer {
  /**
    * Return whether the specified armor ItemStack has a color.
    */
  def hasColor(stack: ItemStack): Boolean = stack.has(DataComponents.DYED_COLOR)

  /**
    * Return the color for the specified armor ItemStack.
    */
  def getColor(stack: ItemStack): Int = Option(stack.get(DataComponents.DYED_COLOR)).fold(-1)(_.rgb)

  def removeColor(stack: ItemStack): Unit = stack.remove(DataComponents.DYED_COLOR)

  def setColor(stack: ItemStack, color: Int): Unit = stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true))
}
