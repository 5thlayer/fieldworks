// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.harvester.DrillParts;
import io.github._5thlayer.fieldworks.harvester.DrillParts.Box;
import io.github._5thlayer.fieldworks.harvester.DrillParts.Kind;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Harvester's drill heads and drill legs (GLOSSARY.md), which its block models leave out: the parts
 * {@link DrillParts} lays out, each lit by the cell it stands in. Static. A placed drill head block draws nothing of its
 * own, so this draws it, and draws the head in a bottom block whose source lies directly under the Harvester.
 */
public class HarvesterRenderer implements BlockEntityRenderer<HarvesterBlockEntity, HarvesterRenderer.RenderState> {

    // The housing wears steel, the bit the casing's gearbox and the legs copper pipe.
    private static final Map<Kind, Identifier> SPRITES = new EnumMap<>(Map.of(
            Kind.BODY, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "block/harvester/drill_body"),
            Kind.BIT, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "block/harvester/gearbox"),
            Kind.LEG, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "block/harvester/drill_leg")));

    /** A part, lit by {@code light}. */
    record Lit(Box box, int light) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(HarvesterBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.parts = List.of();
        var level = entity.getLevel();
        if (level == null) {
            return;
        }
        var pos = entity.getBlockPos();
        state.parts = DrillParts.of(entity.drillHeads()).stream()
                .map(box -> new Lit(box, LevelRenderer.getLightCoords(level, pos.offset(box.dx(), box.dy(), box.dz())))).toList();
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.parts.isEmpty()) {
            return;
        }
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        var sprites = new EnumMap<Kind, TextureAtlasSprite>(Kind.class);
        SPRITES.forEach((kind, id) -> sprites.put(kind, atlas.getSprite(id)));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
            for (var lit : state.parts) {
                box(lit.box(), new Face(sprites.get(lit.box().kind()), pose, consumer, lit.light()));
            }
        });
    }

    // Six faces, anticlockwise seen from outside, each a block of its texture's own coordinates in the cell.
    private static void box(Box box, Face face) {
        double x0 = box.dx() + box.x0(), x1 = box.dx() + box.x1();
        double y0 = box.dy() + box.y0(), y1 = box.dy() + box.y1();
        double z0 = box.dz() + box.z0(), z1 = box.dz() + box.z1();
        double u0 = box.x0(), u1 = box.x1(), w0 = box.z0(), w1 = box.z1(), v0 = 1 - box.y1(), v1 = 1 - box.y0();
        face.quad(0, -1, 0, new double[][] {{x0, y0, z1, u0, w1}, {x0, y0, z0, u0, w0}, {x1, y0, z0, u1, w0}, {x1, y0, z1, u1, w1}});
        face.quad(0, 1, 0, new double[][] {{x0, y1, z0, u0, w0}, {x0, y1, z1, u0, w1}, {x1, y1, z1, u1, w1}, {x1, y1, z0, u1, w0}});
        // Seen from outside, u runs against x on the north face and against z on the east.
        face.quad(0, 0, -1, new double[][] {{x1, y1, z0, 1 - u1, v0}, {x1, y0, z0, 1 - u1, v1}, {x0, y0, z0, 1 - u0, v1}, {x0, y1, z0, 1 - u0, v0}});
        face.quad(0, 0, 1, new double[][] {{x0, y1, z1, u0, v0}, {x0, y0, z1, u0, v1}, {x1, y0, z1, u1, v1}, {x1, y1, z1, u1, v0}});
        face.quad(-1, 0, 0, new double[][] {{x0, y1, z0, w0, v0}, {x0, y0, z0, w0, v1}, {x0, y0, z1, w1, v1}, {x0, y1, z1, w1, v0}});
        face.quad(1, 0, 0, new double[][] {{x1, y1, z1, 1 - w1, v0}, {x1, y0, z1, 1 - w1, v1}, {x1, y0, z0, 1 - w0, v1}, {x1, y1, z0, 1 - w0, v0}});
    }

    private record Face(TextureAtlasSprite sprite, PoseStack.Pose pose, VertexConsumer consumer, int light) {

        // Corners as x, y, z, u, v, with the texture's own coordinates in blocks.
        void quad(float nx, float ny, float nz, double[][] corners) {
            for (var corner : corners) {
                consumer.addVertex(pose.pose(), (float) corner[0], (float) corner[1], (float) corner[2])
                        .setColor(0xFFFFFFFF)
                        .setUv(sprite.getU((float) corner[3]), sprite.getV((float) corner[4]))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    // Its heads and legs hang below the Harvester, out of the block it is drawn at, so it is drawn while they are in sight.
    @Override
    public AABB getRenderBoundingBox(HarvesterBlockEntity entity) {
        int deepest = 0;
        for (int depth : entity.drillHeads()) {
            deepest = Math.max(deepest, DrillParts.headBelow(depth));
        }
        BlockPos pos = entity.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY() - deepest, pos.getZ() - 1, pos.getX() + 2, pos.getY() + 1, pos.getZ() + 2);
    }

    public static class RenderState extends BlockEntityRenderState {
        private List<Lit> parts = List.of();
    }
}
