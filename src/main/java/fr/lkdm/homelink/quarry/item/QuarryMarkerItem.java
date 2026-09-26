package fr.lkdm.homelink.quarry.item;

import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Area selection tool: right-click a block for Corner A, sneak + right-click for Corner B,
 * then right-click a Quarry Controller to apply both corners.
 */
public class QuarryMarkerItem extends Item {
    public QuarryMarkerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        boolean second = player != null && player.isSecondaryUseActive();
        GlobalPos corner = GlobalPos.of(context.getLevel().dimension(), context.getClickedPos().immutable());
        stack.set(second ? QuarryRegistries.CORNER_B.get() : QuarryRegistries.CORNER_A.get(), corner);
        if (player != null) {
            BlockPos pos = corner.pos();
            player.displayClientMessage(Component.translatable(second ? "message.homelink_quarry.corner_b" : "message.homelink_quarry.corner_a",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(QuarryRegistries.CORNER_A.get()) && stack.has(QuarryRegistries.CORNER_B.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(corner(stack.get(QuarryRegistries.CORNER_A.get()), "tooltip.homelink_quarry.marker.corner_a"));
        tooltip.add(corner(stack.get(QuarryRegistries.CORNER_B.get()), "tooltip.homelink_quarry.marker.corner_b"));
        tooltip.add(Component.translatable("tooltip.homelink_quarry.marker.usage").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component corner(GlobalPos corner, String key) {
        if (corner == null) return Component.translatable(key, Component.translatable("tooltip.homelink_quarry.marker.unset"))
                .withStyle(ChatFormatting.GRAY);
        BlockPos pos = corner.pos();
        return Component.translatable(key, pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.GRAY);
    }
}
