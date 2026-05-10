package com.smartpillager.event;

import com.smartpillager.SmartPillagerMod;
import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SmartPillagerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SpawnHandler {

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                SmartPillagerMod.SMART_PILLAGER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SmartPillagerEntity::checkSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }
}
