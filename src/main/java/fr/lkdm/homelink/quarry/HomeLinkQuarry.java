package fr.lkdm.homelink.quarry;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.quarry.config.QuarryConfig;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

/** Entry point shared by the client and dedicated server. */
@Mod(HomeLinkQuarry.MOD_ID)
public final class HomeLinkQuarry {
    public static final String MOD_ID = "homelink_quarry";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HomeLinkQuarry(IEventBus modBus, ModContainer container) {
        QuarryRegistries.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, QuarryConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, QuarryConfig.CLIENT_SPEC);
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, QuarryRegistries.QUARRY_CONTROLLER.get(), (entity, side) -> entity.automation());
            // The quarry runs on HomeLink Energy only: HE enters on every face.
            event.registerBlockEntity(fr.lkdm.homecore.api.energy.EnergyApi.BLOCK, QuarryRegistries.QUARRY_CONTROLLER.get(), (entity, side) -> entity.energyPort());
        });
        modBus.addListener(fr.lkdm.homelink.quarry.network.QuarryPayloads::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(fr.lkdm.homelink.quarry.homelink.QuarryHomeCore::registerProviders));
        modBus.addListener(ModConfigEvent.Loading.class, QuarryConfig::onConfigChanged);
        modBus.addListener(ModConfigEvent.Reloading.class, QuarryConfig::onConfigChanged);
        LOGGER.info("HomeLink Quarry initialized");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
