package com.blighteddreams.helper.spike;

import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Better Combat glue for the spike. BC plays its swing animations on OTHER players from a packet the attacker's client
 * sends and the server rebroadcasts (Packets.AttackAnimation, handled by ServerNetwork.handleAttackAnimation). A fake player
 * has no client, so the spike builds that packet itself and feeds it to the same handler.
 */
final class SpikeBetterCombat {
    private SpikeBetterCombat() {}

    static String swing(SpikePlayer p, int combo) {
        ItemStack st = p.getMainHandItem();
        WeaponAttributes wa = WeaponRegistry.getAttributes(st);
        if (wa == null || wa.attacks() == null || wa.attacks().length == 0) {
            return "main hand has no Better Combat weapon attributes: " + SpikeBodies.stack(st);
        }
        WeaponAttributes.Attack atk = wa.attacks()[Math.floorMod(combo, wa.attacks().length)];
        AnimatedHand hand = wa.isTwoHanded() ? AnimatedHand.TWO_HANDED : AnimatedHand.MAIN_HAND;
        double speed = p.getAttributeValue(Attributes.ATTACK_SPEED);
        float length = (float) (20.0 / Math.max(0.1, speed));
        float upswing = (float) atk.upswing();
        int upswingTicks = Math.round(length * upswing);
        Packets.AttackAnimation pkt = new Packets.AttackAnimation(p.getId(), hand, atk.animation(), length, upswing, (float) wa.attackRange(), upswingTicks, Packets.SwingParticles.EMPTY);
        ServerNetwork.handleAttackAnimation(pkt, p.server, p);
        return "sent BC attack animation '" + atk.animation() + "' (combo " + combo + ", length " + length + " ticks) as her";
    }
}
