package com.ruskserver.moveearth_addtional.client.upscale;

import com.mojang.blaze3d.platform.GlStateManager;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

/**
 * Reads the state {@link GlStateManager} already caches instead of asking the driver.
 *
 * <p>Every {@code glGet}/{@code glIsEnabled} is a round trip to the driver, which with threaded
 * drivers means waiting for its command queue to drain. Minecraft keeps its own copy of the state it
 * sets through {@code RenderSystem}, and code that saves and restores through the same cache stays
 * consistent with it. The cache's fields are private, so they are resolved once by reflection; if
 * that ever fails, every accessor falls back to the driver query it replaces.
 */
public final class GlStateCache {
    private static final Handles HANDLES = Handles.create();

    private GlStateCache() { }

    public static boolean blend() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.enabled.getBoolean(h.blendMode); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glIsEnabled(GL11.GL_BLEND);
    }

    public static boolean depthTest() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.enabled.getBoolean(h.depthMode); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    }

    public static boolean depthMask() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.depthMask.getBoolean(h.depth); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    }

    public static boolean cull() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.enabled.getBoolean(h.cullMode); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glIsEnabled(GL11.GL_CULL_FACE);
    }

    public static boolean scissor() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.enabled.getBoolean(h.scissorMode); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    }

    /** Fills {@code out} with the RGBA write mask, 1 for enabled. */
    public static void colorMask(byte[] out) {
        Handles h = HANDLES;
        if (h != null) {
            try {
                out[0] = (byte) (h.red.getBoolean(h.colorMask) ? 1 : 0);
                out[1] = (byte) (h.green.getBoolean(h.colorMask) ? 1 : 0);
                out[2] = (byte) (h.blue.getBoolean(h.colorMask) ? 1 : 0);
                out[3] = (byte) (h.alpha.getBoolean(h.colorMask) ? 1 : 0);
                return;
            } catch (IllegalAccessException ignored) {
                // fall through to the driver
            }
        }
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            mask.get(out);
        }
    }

    /** The 2D texture bound on texture unit {@code unit} (0-based). */
    public static int textureBinding(int unit) {
        Handles h = HANDLES;
        if (h != null && unit >= 0 && unit < h.textures.length) {
            try { return h.binding.getInt(h.textures[unit]); } catch (IllegalAccessException ignored) { }
        }
        int active = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(active);
        return binding;
    }

    public static int stencilFunc() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.funcFunc.getInt(h.stencilFunc); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
    }

    public static int stencilRef() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.funcRef.getInt(h.stencilFunc); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glGetInteger(GL11.GL_STENCIL_REF);
    }

    public static int stencilValueMask() {
        Handles h = HANDLES;
        if (h != null) {
            try { return h.funcMask.getInt(h.stencilFunc); } catch (IllegalAccessException ignored) { }
        }
        return GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
    }

    /** Resolved field handles; {@code null} as a whole when any of them could not be resolved. */
    private record Handles(Object blendMode, Object depth, Object depthMode, Object cullMode, Object scissorMode,
                           Object colorMask, Object stencilFunc, Object[] textures,
                           Field enabled, Field depthMask, Field red, Field green, Field blue, Field alpha,
                           Field binding, Field funcFunc, Field funcRef, Field funcMask) {
        static Handles create() {
            try {
                Object blend = staticField("BLEND");
                Object depth = staticField("DEPTH");
                Object cull = staticField("CULL");
                Object scissor = staticField("SCISSOR");
                Object colorMask = staticField("COLOR_MASK");
                Object stencil = staticField("STENCIL");
                Object[] textures = (Object[]) staticField("TEXTURES");
                Object blendMode = field(blend, "mode").get(blend);
                Object depthMode = field(depth, "mode").get(depth);
                Object cullMode = field(cull, "enable").get(cull);
                Object scissorMode = field(scissor, "mode").get(scissor);
                Object stencilFunc = field(stencil, "func").get(stencil);
                Field enabled = field(blendMode, "enabled");
                if (textures.length == 0) throw new IllegalStateException("no texture units cached");
                return new Handles(blendMode, depth, depthMode, cullMode, scissorMode, colorMask, stencilFunc,
                        textures, enabled, field(depth, "mask"),
                        field(colorMask, "red"), field(colorMask, "green"), field(colorMask, "blue"),
                        field(colorMask, "alpha"), field(textures[0], "binding"),
                        field(stencilFunc, "func"), field(stencilFunc, "ref"), field(stencilFunc, "mask"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                Moveearth_addtional.LOGGER.warn("GL state cache unavailable; falling back to driver queries", error);
                return null;
            }
        }

        private static Object staticField(String name) throws ReflectiveOperationException {
            Field field = GlStateManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        }

        private static Field field(Object owner, String name) throws ReflectiveOperationException {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
