package com.smartpillager.client;

import com.smartpillager.SmartPillagerMod;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SmartPillagerMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientRendererRegistry {

    public static final ModelLayerLocation SMART_PILLAGER_LAYER =
            new ModelLayerLocation(new ResourceLocation(SmartPillagerMod.MOD_ID, "smart_pillager"), "main");

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                SmartPillagerMod.SMART_PILLAGER.get(),
                SmartPillagerRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SMART_PILLAGER_LAYER, IllagerModel::createBodyLayer);
    }

    public static class SmartPillagerRenderer extends IllagerRenderer<com.smartpillager.entity.SmartPillagerEntity> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation("textures/entity/illager/pillager.png");

        public SmartPillagerRenderer(EntityRendererProvider.Context context) {
            super(context, new IllagerModel<>(context.bakeLayer(SMART_PILLAGER_LAYER)), 0.5f);
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(com.smartpillager.entity.SmartPillagerEntity entity) {
            return TEXTURE;
        }
    }
}
