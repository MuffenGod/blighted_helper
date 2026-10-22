package com.blighteddreams.helper.magic;

import com.blighteddreams.helper.HelperStats;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.fml.ModList;

/**
 * Hazen N Stuff / Hazen Touve Lib adds the Cosmic, Shadow and Radiance schools to Iron's Spells. This resolves their
 * spell-power attributes; every lookup is guarded so the helper still works when the addon is absent.
 */
public final class HelperHazenCompat {
    private HelperHazenCompat() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("hazentouvelib");
    }

    public static Holder<Attribute> power(HelperStats.School school) {
        if (!isAvailable()) return null;
        try {
            return switch (school) {
                case COSMIC -> net.hazen.hazentouvelib.Registries.HLAttributeRegistry.COSMIC_SPELL_POWER;
                case SHADOW -> net.hazen.hazentouvelib.Registries.HLAttributeRegistry.SHADOW_SPELL_POWER;
                case RADIANCE -> net.hazen.hazentouvelib.Registries.HLAttributeRegistry.RADIANCE_SPELL_POWER;
                default -> null;
            };
        } catch (Throwable t) {
            return null;
        }
    }
}
