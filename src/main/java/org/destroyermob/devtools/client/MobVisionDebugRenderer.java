package org.destroyermob.devtools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.destroyermob.devtools.DevTools;

@EventBusSubscriber(modid = DevTools.MOD_ID, value = Dist.CLIENT)
public final class MobVisionDebugRenderer {
    private static final double NEARBY_MOB_RANGE = 48.0D;
    private static final int ARC_SEGMENTS = 36;
    private static final int RING_SEGMENTS = 48;
    private static final float NORMAL_RED = 1.0F;
    private static final float NORMAL_GREEN = 0.55F;
    private static final float NORMAL_BLUE = 0.08F;
    private static final float SNEAKING_RED = 0.1F;
    private static final float SNEAKING_GREEN = 0.85F;
    private static final float SNEAKING_BLUE = 1.0F;
    private static boolean enabled;

    private MobVisionDebugRenderer() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(DevTools.MOD_ID)
                .then(Commands.literal("mob_vision_debug")
                        .executes(context -> setEnabled(!enabled))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setEnabled(BoolArgumentType.getBool(context, "enabled"))))));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Optional<VisionSettings> settingsResult = MobsCombatConfigBridge.read();
        if (settingsResult.isEmpty()) {
            return;
        }
        VisionSettings settings = settingsResult.get();
        boolean sneaking = minecraft.player.isShiftKeyDown()
                && !minecraft.player.isCreative()
                && !minecraft.player.isSpectator();
        List<Mob> hostiles = minecraft.level.getEntitiesOfClass(
                Mob.class,
                minecraft.player.getBoundingBox().inflate(NEARBY_MOB_RANGE),
                MobVisionDebugRenderer::isHostile
        );
        if (hostiles.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Mob mob : hostiles) {
            drawMobVision(poseStack, lines, mob, settings, sneaking);
        }
        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static int setEnabled(boolean newValue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (newValue && !ModList.get().isLoaded("mobscombat")) {
            enabled = false;
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable("commands.devtools.mob_vision_debug.missing_mobscombat"));
            }
            return 0;
        }

        enabled = newValue;
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable(
                    "commands.devtools.mob_vision_debug." + (enabled ? "enabled" : "disabled")
            ));
            if (enabled) {
                minecraft.player.sendSystemMessage(Component.translatable("commands.devtools.mob_vision_debug.legend"));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void drawMobVision(
            PoseStack poseStack,
            VertexConsumer lines,
            Mob mob,
            VisionSettings settings,
            boolean sneaking
    ) {
        Vec3 origin = mob.position().add(0.0D, 0.08D, 0.0D);
        double headYaw = Math.toRadians(mob.getYHeadRot());
        Vec3 forward = new Vec3(-Math.sin(headYaw), 0.0D, Math.cos(headYaw));
        double heading = Math.atan2(forward.z, forward.x);
        double searchRange = Math.max(1.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        float red = sneaking ? SNEAKING_RED : NORMAL_RED;
        float green = sneaking ? SNEAKING_GREEN : NORMAL_GREEN;
        float blue = sneaking ? SNEAKING_BLUE : NORMAL_BLUE;
        float coneDegrees = sneaking ? settings.sneakingConeDegrees() : settings.normalConeDegrees();
        float closeRange = sneaking ? settings.sneakingCloseRange() : settings.normalCloseRange();
        drawSector(
                poseStack,
                lines,
                origin,
                heading,
                searchRange,
                coneDegrees,
                red,
                green,
                blue,
                0.9F
        );
        drawRing(
                poseStack,
                lines,
                origin.add(0.0D, 0.05D, 0.0D),
                closeRange,
                red,
                green,
                blue,
                0.95F
        );

        Vec3 forwardEnd = origin.add(forward.normalize().scale(Math.min(2.0D, searchRange)));
        drawLine(poseStack, lines, origin, forwardEnd, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawSector(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 origin,
            double heading,
            double radius,
            float coneDegrees,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        double halfAngle = Math.toRadians(coneDegrees * 0.5D);
        double startAngle = heading - halfAngle;
        double endAngle = heading + halfAngle;
        Vec3 start = pointOnCircle(origin, radius, startAngle);
        Vec3 end = pointOnCircle(origin, radius, endAngle);
        drawLine(poseStack, lines, origin, start, red, green, blue, alpha);
        drawLine(poseStack, lines, origin, end, red, green, blue, alpha);

        Vec3 previous = start;
        for (int segment = 1; segment <= ARC_SEGMENTS; segment++) {
            double progress = segment / (double) ARC_SEGMENTS;
            Vec3 current = pointOnCircle(origin, radius, startAngle + (endAngle - startAngle) * progress);
            drawLine(poseStack, lines, previous, current, red, green, blue, alpha);
            previous = current;
        }
    }

    private static void drawRing(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 origin,
            float radius,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        if (radius <= 0.0F) {
            return;
        }
        Vec3 previous = pointOnCircle(origin, radius, 0.0D);
        for (int segment = 1; segment <= RING_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0D * segment / RING_SEGMENTS;
            Vec3 current = pointOnCircle(origin, radius, angle);
            drawLine(poseStack, lines, previous, current, red, green, blue, alpha);
            previous = current;
        }
    }

    private static Vec3 pointOnCircle(Vec3 origin, double radius, double angle) {
        return origin.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
    }

    private static void drawLine(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 start,
            Vec3 end,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        Vec3 normal = end.subtract(start).normalize();
        lines.addVertex(poseStack.last(), (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(poseStack.last(), (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static boolean isHostile(Mob mob) {
        return mob.isAlive() && (mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER);
    }

    private record VisionSettings(
            float normalConeDegrees,
            float sneakingConeDegrees,
            float normalCloseRange,
            float sneakingCloseRange
    ) {
    }

    private static final class MobsCombatConfigBridge {
        private static final String CONFIG_CLASS = "org.destroyermob.mobscombat.config.CombatConfig";
        private static Method normalCone;
        private static Method sneakingCone;
        private static Method normalCloseRange;
        private static Method sneakingCloseRange;
        private static boolean resolved;
        private static boolean resolutionFailed;

        private MobsCombatConfigBridge() {
        }

        private static Optional<VisionSettings> read() {
            if (!ModList.get().isLoaded("mobscombat") || resolutionFailed) {
                return Optional.empty();
            }
            try {
                resolve();
                return Optional.of(new VisionSettings(
                        invokeFloat(normalCone),
                        invokeFloat(sneakingCone),
                        invokeFloat(normalCloseRange),
                        invokeFloat(sneakingCloseRange)
                ));
            } catch (ReflectiveOperationException | LinkageError exception) {
                resolutionFailed = true;
                DevTools.LOGGER.error("Could not read Mobs Combat vision settings for the debug overlay", exception);
                return Optional.empty();
            }
        }

        private static void resolve() throws ReflectiveOperationException {
            if (resolved) {
                return;
            }
            Class<?> config = Class.forName(CONFIG_CLASS);
            normalCone = config.getMethod("hostileVisionConeDegrees");
            sneakingCone = config.getMethod("sneakingVisionConeDegrees");
            normalCloseRange = config.getMethod("closeRangeAwarenessBlocks");
            sneakingCloseRange = config.getMethod("sneakingCloseRangeAwarenessBlocks");
            resolved = true;
        }

        private static float invokeFloat(Method method) throws ReflectiveOperationException {
            return ((Number) method.invoke(null)).floatValue();
        }
    }
}
