package li.cil.oc.util

import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape

import scala.language.implicitConversions

object ExtendedBlock {

  implicit def extendedBlock(block: Block): ExtendedBlock = new ExtendedBlock(block)

  // Since 1.13 most per-position block queries live on BlockState; these keep
  // the old position-based helpers working.
  class ExtendedBlock(val block: Block) {
    private def state(position: BlockPosition): BlockState = position.world.get.getBlockState(position.toBlockPos)

    def isAir(position: BlockPosition): Boolean = state(position).isAir

    def isReplaceable(position: BlockPosition): Boolean = state(position).canBeReplaced()

    def getBlockHardness(position: BlockPosition): Float = state(position).getDestroySpeed(position.world.get, position.toBlockPos)

    /** World-space bounds of the block's outline shape, or null if it has none. */
    def getSelectedBoundingBoxFromPool(position: BlockPosition): AABB =
      worldBounds(state(position).getShape(position.world.get, position.toBlockPos), position)

    /** World-space bounds of the block's collision shape, or null if it has none. */
    def getCollisionBoundingBoxFromPool(position: BlockPosition): AABB =
      worldBounds(state(position).getCollisionShape(position.world.get, position.toBlockPos), position)

    def getComparatorInputOverride(position: BlockPosition, side: Direction): Int =
      state(position).getAnalogOutputSignal(position.world.get, position.toBlockPos)

    private def worldBounds(shape: VoxelShape, position: BlockPosition): AABB =
      if (shape.isEmpty) null else shape.bounds.move(position.toBlockPos)
  }

}
