package li.cil.oc.util;

import net.neoforged.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;

/**
 * Thin wrapper around NeoForge's reflection helper.
 * <br>
 * NeoForge runs with Mojang names at runtime, so the field name passed here
 * is the Mojang name (no more SRG "field_12345_a" names).
 */
public final class OCObfuscationReflectionHelper {
    private OCObfuscationReflectionHelper() {
    }

    // Wrapping the generic calls also works around a Scala compiler crash:
    // "trying to do lub/glb of typevar ?E".
    public static <T, E> T getPrivateValue(Class<? super E> classToAccess, @Nullable E instance, String fieldName) {
        return ObfuscationReflectionHelper.getPrivateValue(classToAccess, instance, fieldName);
    }

    public static <T, E> void setPrivateValue(Class<? super T> classToAccess, T instance, E value, String fieldName) {
        ObfuscationReflectionHelper.setPrivateValue(classToAccess, instance, value, fieldName);
    }
}
