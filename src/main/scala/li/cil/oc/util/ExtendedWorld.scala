package li.cil.oc.util

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor

import scala.language.implicitConversions

object ExtendedWorld {

  implicit def extendedBlockAccess(world: BlockGetter): ExtendedBlockAccess = new ExtendedBlockAccess(world)

  implicit def extendedWorld(world: Level): ExtendedWorld = new ExtendedWorld(world)

  class ExtendedBlockAccess(val world: BlockGetter) {
    def getBlock(position: BlockPosition): Block = world.getBlockState(position.toBlockPos).getBlock

    def getBlockMapColor(position: BlockPosition): MapColor = getBlockMetadata(position).getMapColor(world, position.toBlockPos)

    // "Metadata" is the block state since 1.13; the name is kept for callers.
    def getBlockMetadata(position: BlockPosition): BlockState = world.getBlockState(position.toBlockPos)

    def getBlockEntity(position: BlockPosition): BlockEntity = world.getBlockEntity(position.toBlockPos)

    def getBlockEntity(host: EnvironmentHost): BlockEntity = getBlockEntity(BlockPosition(host))

    def isEmptyBlock(position: BlockPosition): Boolean = world.getBlockState(position.toBlockPos).isAir

    /** Packed sky/block light, as used for rendering (sky << 20 | block << 4). */
    def getLightBrightnessForSkyBlocks(position: BlockPosition, minBrightness: Int): Int = world match {
      case lit: BlockAndTintGetter =>
        val pos = position.toBlockPos
        val sky = lit.getBrightness(LightLayer.SKY, pos)
        val block = math.max(lit.getBrightness(LightLayer.BLOCK, pos), minBrightness)
        (sky << 20) | (block << 4)
      case _ => minBrightness << 4
    }
  }

  class ExtendedWorld(override val world: Level) extends ExtendedBlockAccess(world) {
    def blockExists(position: BlockPosition): Boolean = world.isLoaded(position.toBlockPos)

    def breakBlock(position: BlockPosition, drops: Boolean = true): Boolean = world.destroyBlock(position.toBlockPos, drops)

    def destroyBlockInWorldPartially(entityId: Int, position: BlockPosition, progress: Int): Unit = world.destroyBlockProgress(entityId, position.toBlockPos, progress)

    /** Puts out a fire on the given side of a block, like punching it. */
    def extinguishFire(player: Player, position: BlockPosition, side: Direction): Boolean = {
      val firePos = position.offset(side).toBlockPos
      if (world.getBlockState(firePos).is(BlockTags.FIRE)) {
        world.levelEvent(player, 1009, firePos, 0)
        world.removeBlock(firePos, false)
      }
      else false
    }

    def getBlockHardness(position: BlockPosition): Float = getBlockMetadata(position).getDestroySpeed(world, position.toBlockPos)

    /**
     * Mining level required for a block, as in 1.12 (0 = wood/gold, 1 = stone,
     * 2 = iron, 3 = diamond). Derived from the vanilla needs_*_tool tags.
     */
    def getBlockHarvestLevel(position: BlockPosition): Int = {
      val state = getBlockMetadata(position)
      if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) 3
      else if (state.is(BlockTags.NEEDS_IRON_TOOL)) 2
      else if (state.is(BlockTags.NEEDS_STONE_TOOL)) 1
      else 0
    }

    /** Tool class for a block ("pickaxe", "axe", "shovel", "hoe"), or null. */
    def getBlockHarvestTool(position: BlockPosition): String = {
      val state = getBlockMetadata(position)
      if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) "pickaxe"
      else if (state.is(BlockTags.MINEABLE_WITH_AXE)) "axe"
      else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) "shovel"
      else if (state.is(BlockTags.MINEABLE_WITH_HOE)) "hoe"
      else null
    }

    def computeRedstoneSignal(position: BlockPosition, side: Direction): Int = math.max(isBlockProvidingPowerTo(position.offset(side), side), getIndirectPowerLevelTo(position.offset(side), side))

    def isBlockProvidingPowerTo(position: BlockPosition, side: Direction): Int = world.getDirectSignal(position.toBlockPos, side)

    def getIndirectPowerLevelTo(position: BlockPosition, side: Direction): Int = world.getSignal(position.toBlockPos, side)

    def sendBlockUpdated(pos: BlockPos): Unit = world.sendBlockUpdated(pos, world.getBlockState(pos), world.getBlockState(pos), 3)

    def sendBlockUpdated(position: BlockPosition): Unit = sendBlockUpdated(position, world.getBlockState(position.toBlockPos), world.getBlockState(position.toBlockPos))

    def sendBlockUpdated(position: BlockPosition, oldState: BlockState, newState: BlockState, flags: Int = 3): Unit = world.sendBlockUpdated(position.toBlockPos, oldState, newState, flags)

    def notifyBlockOfNeighborChange(position: BlockPosition, block: Block): Unit = world.neighborChanged(position.toBlockPos, block, position.toBlockPos)

    // Observers are notified as part of neighbor updates since 1.13.
    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, updateObservers: Boolean): Unit = world.updateNeighborsAt(position.toBlockPos, block)

    def notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, side: Direction): Unit = world.updateNeighborsAtExceptFromFacing(position.toBlockPos, block, side)

    def playAuxSFX(id: Int, position: BlockPosition, data: Int): Unit = world.levelEvent(id, position.toBlockPos, data)

    def setBlock(position: BlockPosition, block: Block): Boolean = world.setBlockAndUpdate(position.toBlockPos, block.defaultBlockState)

    // Metadata no longer exists; this places the block's default state.
    def setBlock(position: BlockPosition, block: Block, metadata: Int, flag: Int): Boolean = world.setBlock(position.toBlockPos, block.defaultBlockState, flag)

    def setBlockToAir(position: BlockPosition): Boolean = world.removeBlock(position.toBlockPos, false)

    def isSideSolid(position: BlockPosition, side: Direction): Boolean = world.getBlockState(position.toBlockPos).isFaceSturdy(world, position.toBlockPos, side)

    def isLoaded(position: BlockPosition): Boolean = world.isLoaded(position.toBlockPos)
  }

}
