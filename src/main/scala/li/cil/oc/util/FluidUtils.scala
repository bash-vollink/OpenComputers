package li.cil.oc.util

import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.LiquidBlock
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem

object FluidUtils {
  /**
   * Retrieves an actual fluid handler implementation for a specified world coordinate.
   * <br>
   * This performs special handling for in-world liquids.
   */
  def fluidHandlerAt(position: BlockPosition, side: Direction): Option[IFluidHandler] = position.world match {
    case Some(world) if world.blockExists(position) =>
      Option(world.getCapability(Capabilities.FluidHandler.BLOCK, position.toBlockPos, side)).
        orElse(Option(new GenericBlockWrapper(position)))
    case _ => None
  }

  def fluidHandlerOf(stack: ItemStack): IFluidHandlerItem =
    if (stack == null || stack.isEmpty) null
    else stack.getCapability(Capabilities.FluidHandler.ITEM)

  /**
   * Transfers some fluid between two fluid handlers.
   * <br>
   * This will try to extract up the specified amount of fluid from any handler,
   * then insert it into the specified sink handler. If the insertion fails, the
   * fluid will remain in the source handler.
   * <br>
   * This returns the amount of fluid transferred.
   */
  def transferBetweenFluidHandlers(source: IFluidHandler, sink: IFluidHandler, limit: Int = FluidType.BUCKET_VOLUME, sourceTank: Int = -1): Int = {
    var stackToDrain = FluidStack.EMPTY
    if (sourceTank >= 0 && sourceTank < source.getTanks) {
      stackToDrain = source.getFluidInTank(sourceTank).copy()
      if (!stackToDrain.isEmpty) {
        stackToDrain.setAmount(math.min(stackToDrain.getAmount, limit))
      }
    }

    val drained =
      if (!stackToDrain.isEmpty) source.drain(stackToDrain, FluidAction.SIMULATE)
      else source.drain(limit, FluidAction.SIMULATE)
    if (drained.isEmpty) {
      return 0
    }
    val filledAmount = sink.fill(drained, FluidAction.SIMULATE)
    if (filledAmount <= 0) {
      return 0
    }
    if (!stackToDrain.isEmpty) {
      sink.fill(source.drain(drained.copyWithAmount(filledAmount), FluidAction.EXECUTE), FluidAction.EXECUTE)
    } else {
      sink.fill(source.drain(filledAmount, FluidAction.EXECUTE), FluidAction.EXECUTE)
    }
  }

  /**
   * Utility method for calling <tt>transferBetweenFluidHandlers</tt> on handlers
   * in the world.
   * <br>
   * This uses the <tt>fluidHandlerAt</tt> method, and therefore handles special
   * cases such as fluid blocks.
   */
  def transferBetweenFluidHandlersAt(sourcePos: BlockPosition, sourceSide: Direction, sinkPos: BlockPosition, sinkSide: Direction, limit: Int = FluidType.BUCKET_VOLUME, sourceTank: Int = -1): Int =
    fluidHandlerAt(sourcePos, sourceSide).fold(0)(source =>
      fluidHandlerAt(sinkPos, sinkSide).fold(0)(sink =>
        transferBetweenFluidHandlers(source, sink, limit, sourceTank)))

  // ----------------------------------------------------------------------- //

  /**
   * Treats a block position as a one-bucket tank: a fluid source block can be
   * drained, and a full bucket can be placed into air or a replaceable block.
   */
  private class GenericBlockWrapper(position: BlockPosition) extends IFluidHandler {
    private def world = position.world.get

    private def pos = position.toBlockPos

    override def getTanks: Int = 1

    override def getFluidInTank(tank: Int): FluidStack = {
      val fluidState = world.getFluidState(pos)
      if (tank == 0 && fluidState.isSource) new FluidStack(fluidState.getType, FluidType.BUCKET_VOLUME)
      else FluidStack.EMPTY
    }

    override def getTankCapacity(tank: Int): Int = FluidType.BUCKET_VOLUME

    override def isFluidValid(tank: Int, stack: FluidStack): Boolean = true

    override def fill(resource: FluidStack, action: FluidAction): Int = {
      if (resource.isEmpty || resource.getAmount < FluidType.BUCKET_VOLUME) return 0
      val fluid = resource.getFluid
      val state = world.getBlockState(pos)
      val canPlace = world.getFluidState(pos).isEmpty &&
        (state.isAir || state.canBeReplaced(fluid)) &&
        fluid.getFluidType.canBePlacedInLevel(world, pos, resource)
      if (!canPlace) return 0
      if (action.execute()) {
        if (!state.isAir && !world.containsAnyLiquid(position.bounds))
          world.breakBlock(position)
        world.setBlock(pos, fluid.defaultFluidState.createLegacyBlock, 11)
        // This fake neighbor update is required to get stills to start flowing.
        world.notifyBlockOfNeighborChange(position, world.getBlock(position))
      }
      FluidType.BUCKET_VOLUME
    }

    override def drain(resource: FluidStack, action: FluidAction): FluidStack = {
      val current = getFluidInTank(0)
      if (current.isEmpty || resource.isEmpty || !FluidStack.isSameFluid(current, resource) || resource.getAmount < current.getAmount) FluidStack.EMPTY
      else drain(current.getAmount, action)
    }

    override def drain(maxDrain: Int, action: FluidAction): FluidStack = {
      val current = getFluidInTank(0)
      if (current.isEmpty || current.getAmount > maxDrain) return FluidStack.EMPTY
      val state = world.getBlockState(pos)
      state.getBlock match {
        case pickup: BucketPickup =>
          if (action.execute()) pickup.pickupBlock(null, world, pos, state)
          current
        case _: LiquidBlock =>
          if (action.execute()) world.removeBlock(pos, false)
          current
        case _ => FluidStack.EMPTY
      }
    }
  }

}
