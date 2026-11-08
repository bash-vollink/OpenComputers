package li.cil.oc.api.event;

import li.cil.oc.api.component.RackMountable;
import li.cil.oc.api.internal.Rack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired to allow rendering a custom overlay for {@link li.cil.oc.api.component.RackMountable}s.
 * <br>
 * When this event is fired, the GL state is set up such that the origin is
 * the top left corner of the mountable the event was fired for. It's the
 * event handler's responsibility to not render outside the are of the
 * mountable (unless that's explicitly what they're going for, of course).
 */
public abstract class RackMountableRenderEvent extends Event {
    /**
     * The rack that house the mountable this event is fired for.
     */
    public final Rack rack;

    /**
     * The index of the mountable in the rack the event is fired for.
     */
    public final int mountable;

    /**
     * Some additional data made available by the mountable. May be <tt>null</tt>.
     *
     * @see RackMountable#getData()
     */
    public final CompoundTag data;

    public RackMountableRenderEvent(Rack rack, int mountable, CompoundTag data) {
        this.rack = rack;
        this.mountable = mountable;
        this.data = data;
    }

    /**
     * Fired when the static rack model is rendered.
     * <br>
     * Code here runs inside a <tt>ISimpleBlockRenderingHandler</tt>, so functionality
     * is limited to what's possible in there. This is primarily meant to allow setting
     * a custom override texture (<tt>renderer.setOverrideBlockTexture</tt>) for the
     * mountables front.
     * <br>
     * The bounds will be set up before this call, so you may adjust those, if you wish.
     */
    public static class Block extends RackMountableRenderEvent implements ICancellableEvent {
        /**
         * The front-facing side, i.e. where the mountable is visible on the rack.
         */
        public final Direction side;

        /**
         * Texture to use for the front of the mountable.
         */
        private TextureAtlasSprite frontTextureOverride;

        public Block(final Rack rack, final int mountable, final CompoundTag data, final Direction side) {
            super(rack, mountable, data);
            this.side = side;
        }

        /**
         * The texture currently set to use for the front of the mountable, or <tt>null</tt>.
         */
        public TextureAtlasSprite getFrontTextureOverride() {
            return frontTextureOverride;
        }

        /**
         * Set the texture to use for the front of the mountable.
         *
         * @param texture the texture to use.
         */
        public void setFrontTextureOverride(final TextureAtlasSprite texture) {
            frontTextureOverride = texture;
        }
    }

    /**
     * Fired when the dynamic rack model is rendered.
     * <br>
     * Code here runs inside a <tt>BlockEntityRenderer</tt>. This is primarily meant
     * to allow rendering custom overlays, such as LEDs. {@link #poseStack} has been
     * set up such that rendering a one by one quad starting at the origin will fill
     * the full front face of the rack (i.e. rotation and translation have already
     * been applied).
     * <br>
     * For something glowing (like LEDs), pass full brightness as the light value,
     * e.g. via {@link #renderOverlay(ResourceLocation, float, float, int)} with
     * {@code LightTexture.FULL_BRIGHT}.
     * <br>
     * Use the {@link #renderOverlay(ResourceLocation)} to render a slice from a
     * texture in the vertical area occupied by the mountable.
     */
    public static class TileEntity extends RackMountableRenderEvent {
        /**
         * The vertical low and high texture coordinates for the mountable's slot.
         * <br>
         * This is purely for convenience; they're computed as <tt>(2/16)+i*(3/16)</tt>.
         */
        public final float v0, v1;

        /**
         * Pose, buffers, light and overlay of the current block entity render pass.
         */
        public final PoseStack poseStack;
        public final MultiBufferSource buffers;
        public final int light, overlay;

        public TileEntity(final Rack rack, final int mountable, final CompoundTag data, final float v0, final float v1,
                          final PoseStack poseStack, final MultiBufferSource buffers, final int light, final int overlay) {
            super(rack, mountable, data);
            this.v0 = v0;
            this.v1 = v1;
            this.poseStack = poseStack;
            this.buffers = buffers;
            this.light = light;
            this.overlay = overlay;
        }

        /**
         * Utility method for rendering a texture as the front-side overlay.
         *
         * @param texture the texture to use to render the overlay.
         */
        public void renderOverlay(final ResourceLocation texture) {
            renderOverlay(texture, 0, 1, light);
        }

        /**
         * Utility method for rendering a texture as the front-side overlay
         * over a specified horizontal area.
         *
         * @param texture the texture to use to render the overlay.
         * @param u0      the lower end of the vertical area to render at.
         * @param u1      the upper end of the vertical area to render at.
         * @param light   the packed light value to render with.
         */
        public void renderOverlay(final ResourceLocation texture, final float u0, final float u1, final int light) {
            quad(buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), u0, u1, u0, u1, v0, v1, light);
        }

        /**
         * Utility method for rendering an atlas texture as the front-side overlay.
         *
         * @param texture the location of the texture in the block atlas.
         */
        public void renderOverlayFromAtlas(final ResourceLocation texture) {
            renderOverlayFromAtlas(texture, 0, 1, light);
        }

        /**
         * Utility method for rendering an atlas texture as the front-side overlay
         * over a specified horizontal area.
         *
         * @param texture the location of the texture in the block atlas.
         * @param u0      the lower end of the vertical area to render at.
         * @param u1      the upper end of the vertical area to render at.
         * @param light   the packed light value to render with.
         */
        public void renderOverlayFromAtlas(final ResourceLocation texture, final float u0, final float u1, final int light) {
            final TextureAtlasSprite icon = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            final VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            quad(buffer, u0, u1, icon.getU(u0), icon.getU(u1), icon.getV(v0), icon.getV(v1), light);
        }

        private void quad(final VertexConsumer buffer, final float x0, final float x1,
                          final float tu0, final float tu1, final float tv0, final float tv1, final int light) {
            final PoseStack.Pose pose = poseStack.last();
            vertex(buffer, pose, x0, v1, tu0, tv1, light);
            vertex(buffer, pose, x1, v1, tu1, tv1, light);
            vertex(buffer, pose, x1, v0, tu1, tv0, light);
            vertex(buffer, pose, x0, v0, tu0, tv0, light);
        }

        private void vertex(final VertexConsumer buffer, final PoseStack.Pose pose, final float x, final float y,
                            final float u, final float v, final int light) {
            buffer.addVertex(pose, x, y, 0)
                    .setColor(0xFFFFFFFF)
                    .setUv(u, v)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, 0, 0, -1);
        }
    }
}
