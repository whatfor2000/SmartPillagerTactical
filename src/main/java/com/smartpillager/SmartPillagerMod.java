package com.smartpillager;

import com.smartpillager.config.ModConfig;
import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(SmartPillagerMod.MOD_ID)
public class SmartPillagerMod {
    public static final String MOD_ID = "smartpillager";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<SmartPillagerEntity>> SMART_PILLAGER =
            ENTITIES.register("smart_pillager",
                    () -> EntityType.Builder.<SmartPillagerEntity>of(SmartPillagerEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("smart_pillager"));

    public SmartPillagerMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ENTITIES.register(modEventBus);

        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        SmartPillagerMod.LOGGER.info("Smart Pillager Tactical mod initializing...");
    }

    @SubscribeEvent
    public void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SMART_PILLAGER.get(), SmartPillagerEntity.createAttributes().build());
    }
}
