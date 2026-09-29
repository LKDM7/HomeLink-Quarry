package fr.lkdm.homelink.quarry.verification;

import net.neoforged.fml.common.Mod;

/** Development-only checks; this source set is excluded from the shipped mod. */
@Mod(QuarryValidation.MOD_ID)
public final class QuarryValidation {
    public static final String MOD_ID = "quarry_validation";

    public QuarryValidation(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(fr.lkdm.homecore.api.item.ItemApi.BLOCK,
                        net.minecraft.world.level.block.entity.BlockEntityType.DROPPER,
                        (entity, side) -> fr.lkdm.homecore.api.item.ItemApi.of(
                                new net.neoforged.neoforge.items.wrapper.InvWrapper(entity), fr.lkdm.homecore.api.item.ItemPortType.INPUT)));
    }
}
