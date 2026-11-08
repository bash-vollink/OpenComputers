package li.cil.oc.api.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.function.Consumer;

/**
 * Access to the NBT data OpenComputers keeps on item stacks.
 * <br>
 * Since Minecraft 1.20.5 item stacks no longer carry a mutable tag compound.
 * Arbitrary NBT lives in the {@link DataComponents#CUSTOM_DATA} component,
 * which is immutable. The getters here therefore return <em>copies</em>:
 * changes to them are only kept if they are written back with the matching
 * setter, or done inside one of the {@code update} methods.
 */
public final class StackData {
    /**
     * Key of the child compound used for component data by default.
     */
    public static final String DATA_KEY = "oc:data";

    /**
     * Returns a copy of the stack's custom data, empty if it has none.
     */
    public static CompoundTag get(final ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /**
     * Returns whether the stack has any custom data.
     */
    public static boolean has(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    /**
     * Replaces the stack's custom data. An empty tag removes the component.
     */
    public static void set(final ItemStack stack, final CompoundTag tag) {
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    /**
     * Modifies the stack's custom data in place.
     */
    public static void update(final ItemStack stack, final Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, modifier);
    }

    /**
     * Returns a copy of the {@link #DATA_KEY} child compound.
     */
    public static CompoundTag getData(final ItemStack stack) {
        return get(stack).getCompound(DATA_KEY);
    }

    /**
     * Replaces the {@link #DATA_KEY} child compound.
     */
    public static void setData(final ItemStack stack, final CompoundTag data) {
        update(stack, tag -> tag.put(DATA_KEY, data.copy()));
    }

    /**
     * Modifies the {@link #DATA_KEY} child compound in place.
     */
    public static void updateData(final ItemStack stack, final Consumer<CompoundTag> modifier) {
        update(stack, tag -> {
            final CompoundTag data = tag.getCompound(DATA_KEY);
            modifier.accept(data);
            tag.put(DATA_KEY, data);
        });
    }

    /**
     * Serializes a whole item stack (item, count and components).
     * An empty stack is written as an empty compound.
     */
    public static CompoundTag save(final ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        final Tag tag = stack.save(registries());
        return tag instanceof CompoundTag ? (CompoundTag) tag : new CompoundTag();
    }

    /**
     * Deserializes an item stack written by {@link #save(ItemStack)}.
     */
    public static ItemStack load(final CompoundTag tag) {
        return ItemStack.parseOptional(registries(), tag);
    }

    /**
     * Registry access for (de)serializing stacks: the running server's if
     * there is one, otherwise only the built-in (static) registries.
     */
    // TODO(port): use the client level's registry access when on a remote client.
    public static HolderLookup.Provider registries() {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private StackData() {
    }
}
