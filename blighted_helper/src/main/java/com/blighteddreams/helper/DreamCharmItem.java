package com.blighteddreams.helper;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Dream Charm: Attune, summon, and dismiss your companion.
 * - Right-click: Summons companion if absent; unsummons (dismisses) her if present.
 * - Shift + Right-click: Opens Companion Sanctuary screen to choose, swap, or rename companions.
 */
public class DreamCharmItem extends Item {
    public DreamCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp && HelperConfig.enabled()) {
            boolean chosen = sp.getPersistentData().getBoolean("HasChosenStarterHelper");
            HelperEntity h = HelperManager.find(sp);

            // Shift-right click or first time ever choosing: open the Sanctuary selection screen
            if (player.isSecondaryUseActive() || (!chosen && h == null)) {
                HelperManager.openStarterScreenForPlayer(sp);
                player.getCooldowns().addCooldown(this, 15);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }

            // Normal right-click: Toggle Summon / Unsummon
            if (h != null) {
                // She is currently summoned -> Unsummon her back into the charm
                HelperManager.unsummon(sp);
                player.getCooldowns().addCooldown(this, 20);
            } else {
                // She is not summoned -> Summon her from the charm
                HelperManager.summon(sp);
                player.getCooldowns().addCooldown(this, 20);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§7Right-click to §aSummon§7 or §cUnsummon§7 your companion."));
        tooltipComponents.add(Component.literal("§eShift + Right-click§7 to choose companion & view sanctuary."));
        tooltipComponents.add(Component.literal("§8Each companion keeps her own separate level, inventory & gear."));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
