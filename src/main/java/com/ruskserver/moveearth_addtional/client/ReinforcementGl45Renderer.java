package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** OpenGL 4.5 fast path for state-merged exposed face rectangles. */
final class ReinforcementGl45Renderer {
    private static final int MAX_INSTANCES = 8192 * 6;
    private static final int FLOATS_PER_INSTANCE = 10;
    private static final int INSTANCE_STRIDE = FLOATS_PER_INSTANCE * Float.BYTES;
    private static final int FACE_COUNT = 6;
    private static final int BUFFER_SLOTS = 3;
    private static final long SLOT_BYTES = (long) MAX_INSTANCES * INSTANCE_STRIDE;
    private static final float E = 0.003F;
    private static final float[] CUBE_VERTICES = cubeVertices();
    private static final String VERTEX_SHADER = """
            #version 450 core
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec3 InstancePosition;
            layout(location = 2) in vec3 InstanceScale;
            layout(location = 3) in vec4 InstanceColor;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            out vec4 vertexColor;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position * InstanceScale + InstancePosition, 1.0);
                vertexColor = InstanceColor;
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 450 core
            in vec4 vertexColor;
            layout(location = 0) out vec4 fragColor;
            void main() { fragColor = vertexColor; }
            """;

    private static State state = State.UNINITIALIZED;
    private static int vao;
    private static int cubeBuffer;
    private static int instanceBuffer;
    private static int program;
    private static int modelViewUniform;
    private static int projectionUniform;
    private static ByteBuffer mappedInstances;
    private static final FloatBuffer[] mappedSlots = new FloatBuffer[BUFFER_SLOTS];
    private static final long[] fences = new long[BUFFER_SLOTS];
    private static final int[] faceStarts = new int[FACE_COUNT];
    private static final int[] faceCounts = new int[FACE_COUNT];
    private static final int[] faceCursors = new int[FACE_COUNT];
    private static int slot;

    private ReinforcementGl45Renderer() { }

    static boolean render(PoseStack poseStack, float[] positions, float[] scales,
                          float[] colors, int[] faces, int count) {
        if (count <= 0) return true;
        if (!ensureInitialized()) return false;
        int activeSlot = slot;
        long fence = fences[activeSlot];
        if (fence != 0L) {
            int wait = GL32C.glClientWaitSync(fence, 0, 0L);
            // Never turn transient GPU pressure into thousands of compatibility draw submissions.
            // Keeping the game frame responsive is preferable to drawing this optional overlay frame.
            if (wait == GL32C.GL_TIMEOUT_EXPIRED) return true;
            if (wait == GL32C.GL_WAIT_FAILED) return fail("OpenGL 4.5 instance fence failed", null);
            GL32C.glDeleteSync(fence);
            fences[activeSlot] = 0L;
        }

        long byteOffset = activeSlot * SLOT_BYTES;
        FloatBuffer target = mappedSlots[activeSlot];
        java.util.Arrays.fill(faceCounts, 0);
        for (int index = 0; index < count; index++) faceCounts[faces[index]]++;
        int written = 0;
        for (int face = 0; face < FACE_COUNT; face++) {
            faceStarts[face] = written;
            faceCursors[face] = written;
            written += faceCounts[face];
        }
        for (int index = 0; index < count; index++) {
            int destination = faceCursors[faces[index]]++ * FLOATS_PER_INSTANCE;
            int positionIndex = index * 3;
            int colorIndex = index * 4;
            target.put(destination, positions[positionIndex]);
            target.put(destination + 1, positions[positionIndex + 1]);
            target.put(destination + 2, positions[positionIndex + 2]);
            target.put(destination + 3, scales[positionIndex]);
            target.put(destination + 4, scales[positionIndex + 1]);
            target.put(destination + 5, scales[positionIndex + 2]);
            target.put(destination + 6, colors[colorIndex]);
            target.put(destination + 7, colors[colorIndex + 1]);
            target.put(destination + 8, colors[colorIndex + 2]);
            target.put(destination + 9, colors[colorIndex + 3]);
        }

        try {
            // RenderSystem keeps its own GL state cache. Using it here avoids synchronous glGet* calls
            // and leaves that cache consistent for Minecraft and other renderers.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            GL20C.glUseProgram(program);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer matrix = stack.mallocFloat(16);
                new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose()).get(matrix);
                GL20C.glUniformMatrix4fv(modelViewUniform, false, matrix);
                matrix.clear();
                RenderSystem.getProjectionMatrix().get(matrix);
                GL20C.glUniformMatrix4fv(projectionUniform, false, matrix);
            }
            GL30C.glBindVertexArray(vao);
            GL42C.glMemoryBarrier(GL44C.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
            for (int face = 0; face < FACE_COUNT; face++) {
                if (faceCounts[face] == 0) continue;
                long faceOffset = byteOffset + (long) faceStarts[face] * INSTANCE_STRIDE;
                GL45C.glVertexArrayVertexBuffer(vao, 1, instanceBuffer, faceOffset, INSTANCE_STRIDE);
                GL31C.glDrawArraysInstanced(GL11C.GL_TRIANGLES, face * 6, 6, faceCounts[face]);
            }
            fences[activeSlot] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            slot = (activeSlot + 1) % BUFFER_SLOTS;
            return true;
        } catch (Throwable error) {
            return fail("OpenGL 4.5 reinforcement renderer failed; using compatibility renderer", error);
        } finally {
            GL20C.glUseProgram(0);
            GL30C.glBindVertexArray(0);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static boolean ensureInitialized() {
        if (state != State.UNINITIALIZED) return state == State.READY;
        RenderSystem.assertOnRenderThread();
        try {
            if (Boolean.getBoolean("moveearth.reinforcement.forceCompatibilityRenderer")) {
                state = State.UNSUPPORTED;
                Moveearth_addtional.LOGGER.info("Reinforcement renderer: compatibility path forced by system property");
                return false;
            }
            if (!GL.getCapabilities().OpenGL45) {
                state = State.UNSUPPORTED;
                Moveearth_addtional.LOGGER.info("Reinforcement renderer: compatibility path (OpenGL 4.5 unavailable)");
                return false;
            }
            int vertexShader = compile(GL20C.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertexShader);
            GL20C.glAttachShader(program, fragmentShader);
            GL20C.glLinkProgram(program);
            GL20C.glDeleteShader(vertexShader);
            GL20C.glDeleteShader(fragmentShader);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException(GL20C.glGetProgramInfoLog(program));
            }
            modelViewUniform = GL20C.glGetUniformLocation(program, "ModelViewMat");
            projectionUniform = GL20C.glGetUniformLocation(program, "ProjMat");

            vao = GL45C.glCreateVertexArrays();
            cubeBuffer = GL45C.glCreateBuffers();
            GL45C.glNamedBufferStorage(cubeBuffer, CUBE_VERTICES, 0);
            instanceBuffer = GL45C.glCreateBuffers();
            int storageFlags = GL44C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT
                    | GL44C.GL_MAP_COHERENT_BIT | GL44C.GL_DYNAMIC_STORAGE_BIT;
            GL45C.glNamedBufferStorage(instanceBuffer, SLOT_BYTES * BUFFER_SLOTS, storageFlags);
            mappedInstances = GL45C.glMapNamedBufferRange(instanceBuffer, 0L,
                    SLOT_BYTES * BUFFER_SLOTS, storageFlags);
            if (mappedInstances == null) throw new IllegalStateException("Could not map instance buffer");
            for (int index = 0; index < BUFFER_SLOTS; index++) {
                int offset = Math.toIntExact(index * SLOT_BYTES);
                mappedSlots[index] = mappedInstances.duplicate().order(ByteOrder.nativeOrder())
                        .position(offset).limit(Math.toIntExact(offset + SLOT_BYTES))
                        .slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
            }

            GL45C.glVertexArrayVertexBuffer(vao, 0, cubeBuffer, 0L, 3 * Float.BYTES);
            GL45C.glEnableVertexArrayAttrib(vao, 0);
            GL45C.glVertexArrayAttribFormat(vao, 0, 3, GL11C.GL_FLOAT, false, 0);
            GL45C.glVertexArrayAttribBinding(vao, 0, 0);
            GL45C.glVertexArrayVertexBuffer(vao, 1, instanceBuffer, 0L, INSTANCE_STRIDE);
            GL45C.glEnableVertexArrayAttrib(vao, 1);
            GL45C.glVertexArrayAttribFormat(vao, 1, 3, GL11C.GL_FLOAT, false, 0);
            GL45C.glVertexArrayAttribBinding(vao, 1, 1);
            GL45C.glEnableVertexArrayAttrib(vao, 2);
            GL45C.glVertexArrayAttribFormat(vao, 2, 3, GL11C.GL_FLOAT, false, 3 * Float.BYTES);
            GL45C.glVertexArrayAttribBinding(vao, 2, 1);
            GL45C.glEnableVertexArrayAttrib(vao, 3);
            GL45C.glVertexArrayAttribFormat(vao, 3, 4, GL11C.GL_FLOAT, false, 6 * Float.BYTES);
            GL45C.glVertexArrayAttribBinding(vao, 3, 1);
            GL45C.glVertexArrayBindingDivisor(vao, 1, 1);
            state = State.READY;
            Moveearth_addtional.LOGGER.info("Reinforcement renderer: OpenGL 4.5 instanced path enabled");
            return true;
        } catch (Throwable error) {
            return fail("Could not initialize OpenGL 4.5 reinforcement renderer; using compatibility renderer", error);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private static boolean fail(String message, Throwable error) {
        if (state != State.FAILED) {
            if (error == null) Moveearth_addtional.LOGGER.warn(message);
            else Moveearth_addtional.LOGGER.warn(message, error);
        }
        state = State.FAILED;
        return false;
    }

    private static float[] cubeVertices() {
        float lo = -E, hi = 1.0F + E;
        return new float[]{
                0, 0, lo, 1, 1, lo, 1, 0, lo, 0, 0, lo, 0, 1, lo, 1, 1, lo,
                0, 0, hi, 1, 0, hi, 1, 1, hi, 0, 0, hi, 1, 1, hi, 0, 1, hi,
                lo, 0, 0, lo, 0, 1, lo, 1, 1, lo, 0, 0, lo, 1, 1, lo, 1, 0,
                hi, 0, 0, hi, 1, 0, hi, 1, 1, hi, 0, 0, hi, 1, 1, hi, 0, 1,
                0, lo, 0, 1, lo, 0, 1, lo, 1, 0, lo, 0, 1, lo, 1, 0, lo, 1,
                0, hi, 0, 0, hi, 1, 1, hi, 1, 0, hi, 0, 1, hi, 1, 1, hi, 0
        };
    }

    private enum State { UNINITIALIZED, READY, UNSUPPORTED, FAILED }
}
