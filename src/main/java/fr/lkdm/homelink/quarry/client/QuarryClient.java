package fr.lkdm.homelink.quarry.client;

import fr.lkdm.homelink.quarry.HomeLinkQuarry;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import fr.lkdm.homelink.quarry.client.screen.QuarryScreen;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Client-only registrations. */
@EventBusSubscriber(modid = HomeLinkQuarry.MOD_ID, value = Dist.CLIENT)
public final class QuarryClient {
    private QuarryClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(QuarryRegistries.QUARRY_CONTROLLER.get(), QuarryHeadRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        QuarryHeadRenderer.registerModels(event);
    }

    @SubscribeEvent
    public static void renderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        QuarryAreaRenderer.onRenderLevel(event);
    }

    /** Preview choices are personal and per session. */
    @SubscribeEvent
    public static void loggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        QuarryPreview.clear();
        QuarryClientData.clear();
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(QuarryRegistries.QUARRY_MENU.get(), QuarryScreen::new);
    }
}
