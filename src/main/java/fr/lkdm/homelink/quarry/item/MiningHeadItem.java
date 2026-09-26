package fr.lkdm.homelink.quarry.item;

import fr.lkdm.homelink.quarry.quarry.MiningHeadTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Permanent drilling module installed in a Quarry Controller. It never wears out. */
public class MiningHeadItem extends Item {
    private final MiningHeadTier tier;

    public MiningHeadItem(MiningHeadTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public MiningHeadTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.homelink_quarry.mining_head.speed", tier.secondsPerBlock())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.homelink_quarry.mining_head.permanent").withStyle(ChatFormatting.GRAY));
    }
}
