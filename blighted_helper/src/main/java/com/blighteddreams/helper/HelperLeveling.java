package com.blighteddreams.helper;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Locale;

/**
 * Progression and leveling system for Emily companion.
 * - Starts at Level 1 with 10 Max Health and 0.75 Scale (75% size).
 * - Gains experience by slaying monsters and bosses (bosses grant high bonus XP).
 * - Every level increases Max Health and Attack Damage.
 * - Every 5 levels (5, 10, 15, 20, 25, 30, 35, 40, 45, 50) triggers a growth spurt: she gains extra health and grows
 *   visibly in size, reaching Level 5 with exactly 15 Health, full adult size (1.0) at Level 25, and full power
 *   (60.0 HP at Level 50).
 */
public final class HelperLeveling {
    public static final int MAX_LEVEL = 50;

    private HelperLeveling() {}

    /**
     * Experience required to reach the next level.
     */
    public static int getXpForNextLevel(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return 0;
        return 75 + (currentLevel - 1) * 35 + ((currentLevel / 5) * 60);
    }

    /**
     * Max health progression:
     * Level 1: 10.0 HP (5 hearts)
     * Level 2: 11.0 HP
     * Level 3: 12.0 HP
     * Level 4: 13.0 HP
     * Level 5: 15.0 HP (+2 milestone extra health! Exactly 15 HP)
     * Level 10: 21.0 HP (+2 milestone)
     * Level 15: 27.0 HP (+2 milestone)
     * Level 20: 33.0 HP (+2 milestone)
     * Level 25: 39.0 HP (+2 milestone)
     * Level 26-30: 40.0 HP (old full health)
     * Level 31-50: +1.0 HP per level, reaching 60.0 HP at Level 50
     */
    public static float getMaxHealth(int level) {
        if (level <= 1) return 10.0F;
        if (level <= 30) {
            int milestones = level / 5;
            float hp = 10.0F + (level - 1) + milestones;
            return Math.min(40.0F, hp);
        }
        return Math.min(60.0F, 40.0F + (level - 30));
    }

    /**
     * Size scale progression:
     * Levels 1-4: 0.75 (3/4 size start)
     * Levels 5-9: 0.80
     * Levels 10-14: 0.85
     * Levels 15-19: 0.90
     * Levels 20-24: 0.95
     * Levels 25+: 1.00 (full adult size)
     */
    public static float getScale(int level) {
        if (level <= 1) return 0.75F;
        if (level >= 25) return 1.00F;
        if (level < 5) return 0.75F;
        if (level < 10) return 0.80F;
        if (level < 15) return 0.85F;
        if (level < 20) return 0.90F;
        return 0.95F;
    }

    /**
     * Attack damage progression:
     * Level 1: 1.5 ATK
     * Level 5: 2.1 ATK
     * Level 10: 2.9 ATK (near baseline)
     * Level 20: 4.5 ATK
     * Level 30: 6.0 ATK
     * Level 31-50: +0.2 ATK per level, reaching 10.0 ATK at Level 50
     */
    public static float getAttackDamage(int level) {
        if (level <= 1) return 1.5F;
        if (level <= 30) return 1.5F + (level - 1) * 0.155F;
        return Math.min(10.0F, 6.0F + (level - 30) * 0.2F);
    }

    /**
     * Checks if the target is considered a boss entity (vanilla or modded).
     */
    public static boolean isBoss(LivingEntity entity) {
        if (entity == null) return false;
        if (entity.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)) return true;
        if (entity.getMaxHealth() >= 100.0F) return true;
        var tag = entity.getPersistentData();
        if (tag.contains("apoth.boss") || tag.contains("boss")) return true;
        return entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }

    /**
     * XP reward granted by slaying an enemy. Bosses give vastly more XP.
     */
    public static int getKillXpReward(LivingEntity victim) {
        if (victim == null) return 0;
        if (isBoss(victim)) {
            return Math.max(300, (int) (victim.getMaxHealth() * 1.5F));
        }
        return Math.max(15, (int) (victim.getMaxHealth() * 0.75F));
    }

    /**
     * Awards kill experience to Emily. Prevents duplicate XP awards for the same victim.
     */
    public static void awardKillXp(HelperEntity helper, LivingEntity victim) {
        if (helper == null || victim == null || helper.level().isClientSide) return;
        if (victim.getPersistentData().getBoolean("HelperXpAwarded")) return;
        victim.getPersistentData().putBoolean("HelperXpAwarded", true);

        int xp = getKillXpReward(victim);
        if (xp <= 0) return;

        addExperience(helper, xp, isBoss(victim));
    }

    /**
     * Grants experience to Emily and triggers level up handling when thresholds are crossed.
     */
    public static void addExperience(HelperEntity helper, int amount, boolean fromBoss) {
        if (helper == null || helper.level().isClientSide || helper.getLevel() >= MAX_LEVEL) return;

        int curXp = helper.getXp() + amount;
        int curLvl = helper.getLevel();
        boolean leveledUp = false;
        boolean hitMilestone = false;

        while (curLvl < MAX_LEVEL) {
            int needed = getXpForNextLevel(curLvl);
            if (curXp >= needed) {
                curXp -= needed;
                curLvl++;
                leveledUp = true;
                if (curLvl % 5 == 0) {
                    hitMilestone = true;
                }
            } else {
                break;
            }
        }

        if (curLvl >= MAX_LEVEL) {
            curXp = 0;
        }

        helper.setXp(curXp);

        if (leveledUp) {
            int oldLvl = helper.getLevel();
            helper.setLevel(curLvl);
            onLevelUp(helper, oldLvl, curLvl, hitMilestone);
        }
    }

    /**
     * Handles stat recalculation, particle fanfare, sounds, and companion dialogue on level up.
     */
    public static void onLevelUp(HelperEntity helper, int oldLevel, int newLevel, boolean hitMilestone) {
        float oldMaxHp = (float) helper.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        helper.applyLevelAttributes();
        float newMaxHp = (float) helper.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        float hpGain = Math.max(0.0F, newMaxHp - oldMaxHp);
        if (hpGain > 0.0F) {
            helper.heal(hpGain);
        }

        // Keep elemental spell tier scaled with level
        if (helper.getVariant() == HelperVariant.DEMON) {
            int minSpellLvl = 1 + (newLevel / 10);
            if (helper.getFireSpellLevel() < minSpellLvl) {
                helper.setFireSpellLevel(minSpellLvl);
            }
        } else if (helper.getVariant() == HelperVariant.ANGEL) {
            int minSpellLvl = 1 + (newLevel / 10);
            if (helper.getHolySpellLevel() < minSpellLvl) {
                helper.setHolySpellLevel(minSpellLvl);
            }
        }

        ServerPlayer owner = helper.getOwner();
        if (owner != null) {
            owner.getPersistentData().putInt("BlightedHelperLevel", newLevel);
            owner.getPersistentData().putInt("BlightedHelperXp", helper.getXp());
        }

        if (helper.level() instanceof ServerLevel sl) {
            double x = helper.getX();
            double y = helper.getY(0.5);
            double z = helper.getZ();

            if (hitMilestone) {
                // Growth spurt fanfare!
                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 35, 0.4, 0.6, 0.4, 0.15);
                sl.sendParticles(ParticleTypes.FLASH, x, y + 0.3, z, 2, 0.1, 0.1, 0.1, 0.0);
                sl.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.3, 0.5, 0.3, 0.08);
                sl.playSound(null, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
                sl.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.9F);
            } else {
                // Regular level up
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 16, 0.3, 0.4, 0.3, 0.05);
                sl.sendParticles(ParticleTypes.ENCHANT, x, y, z, 25, 0.3, 0.5, 0.3, 0.1);
                sl.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.85F, 1.2F);
            }
        }

        if (owner != null) {
            HelperManager.saveHelperState(owner, helper);
            String name = helper.getName().getString();
            float hp = helper.getMaxHealth();
            float atk = (float) helper.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

            if (hitMilestone) {
                owner.displayClientMessage(Component.literal("§6§l★ GROWTH SPURT! ★ §e" + name + " reached Level " + newLevel + "! §7(❤ " + Math.round(hp) + " HP, ⚔ " + String.format(Locale.ROOT, "%.1f", atk) + " ATK)"), true);
                String quote = getMilestoneQuote(newLevel);
                owner.displayClientMessage(Component.literal("§e<" + name + "> §f" + quote), false);
            } else {
                owner.displayClientMessage(Component.literal("§a§l✦ Level Up! ✦ §e" + name + " reached Level " + newLevel + " §7(❤ " + Math.round(hp) + " HP, ⚔ " + String.format(Locale.ROOT, "%.1f", atk) + " ATK)"), true);
                if (helper.getRandom().nextFloat() < 0.4F) {
                    String[] regularQuotes = new String[]{
                            "Level up! That felt good!",
                            "Getting stronger every battle!",
                            "We're growing unstoppable together!",
                            "Those monsters never stood a chance!"
                    };
                    String q = regularQuotes[helper.getRandom().nextInt(regularQuotes.length)];
                    owner.displayClientMessage(Component.literal("§e<" + name + "> §f" + q), false);
                }
            }
        }
    }

    private static String getMilestoneQuote(int level) {
        return switch (level) {
            case 5 -> "I feel so much stronger! Look, I'm getting bigger too!";
            case 10 -> "Halfway to full size! Keep those monsters coming, partner!";
            case 15 -> "We make an awesome team! I'll always have your back!";
            case 20 -> "Feel that power? I'm almost as tall as you now!";
            case 25 -> "Nothing in this realm can stand in our path!";
            case 30 -> "I've reached my full size, but my power keeps growing. Keep pushing me, partner!";
            case 35 -> "Every battle is sharpening me. I can feel the dragonfire getting hotter!";
            case 40 -> "Forty levels. Most mortals would have broken by now. You made me unstoppable.";
            case 45 -> "There is almost nothing left that can threaten us. Almost. I'm still watching your back.";
            case 50 -> "Level fifty. I've become everything I was meant to be, and it's all because of you, partner.";
            default -> "Growing bigger and stronger! Watch out, monsters!";
        };
    }
}
