package com.blighteddreams.helper.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Contextual Micro-RAG knowledge engine for Emily.
 * Rather than dumping the entire modpack manual into every prompt, dynamically selects the most relevant
 * tactical lore, mechanics, and survival rules based on the player's query and current in-game state.
 */
public final class HelperKnowledge {
    private static final Map<String, KnowledgeTopic> TOPICS = new LinkedHashMap<>();

    private record KnowledgeTopic(String title, List<String> keywords, String summary) {}

    static {
        register("FIRST_AID",
                List.of("health", "heal", "bandage", "bleed", "bleeding", "splint", "morphine", "limb", "hurt", "dying", "medkit", "pain", "broken", "fracture"),
                "FIRST AID & MEDICINE: Player body parts have separate health pools. Head or torso reaching 0 is fatal! "
                        + "Bleeding will drain health until bandaged or treated with a medkit. Fractured limbs cripple movement until splinted. "
                        + "Morphine numbs pain and restores temporary mobility. Regular food does NOT immediately restore broken bones.");

        register("TEMPERATURE",
                List.of("cold", "freeze", "freezing", "hypothermia", "heat", "hot", "hyperthermia", "sweat", "temperature", "burn", "warm", "campfire", "thermal", "ice"),
                "TEMPERATURE & COLD SWEAT: Extreme biomes cause deadly hypothermia or heatstroke. "
                        + "Stay warm with campfires, wool thermals, hearths, heating coats, and warm soups. "
                        + "Cool down with water, cooling coats, ice, and chilled juices. The Nether requires Cooling II; The End requires Heating III.");

        register("APOTHEOSIS",
                List.of("gem", "gems", "socket", "affix", "affixes", "reforge", "reforging", "salvage", "dust", "rarity", "mythic", "ancient", "apotheosis", "stats"),
                "APOTHEOSIS RPG GEAR: Equipment rolls with random affixes and gem sockets. "
                        + "Use Reforging Tables with gem dust and rarity materials to re-roll affixes. "
                        + "Gem Cutting Tables combine and polish socketable gems for massive attribute bonuses. "
                        + "Salvaging Tables break down unwanted magical gear into essential gem dust.");

        register("CATACLYSM_BOSSES",
                List.of("boss", "bosses", "sin", "sins", "monstrosity", "sloth", "ignis", "wrath", "scylla", "lust", "leviathan", "harbinger", "cataclysm", "dungeon"),
                "THE SEVEN SINS (CATACLYSM BOSSES): "
                        + "1. Netherite Monstrosity (Sin of Sloth): Massive armored beast. Dodge its telegraphed hammer slams; hit its unarmored back during recovery. "
                        + "2. Ignis (Sin of Wrath): Lethal flaming knight in the Nether fortress. Fire resistance potions and rapid dodging are mandatory. "
                        + "3. Scylla / The Leviathan (Sin of Lust / Envy): Terrors of the sunken ocean abyss. Bring water breathing and depth strider. "
                        + "Never fight a Sin unprepared without Apotheosis-forged gear and first aid supplies.");

        register("COMBAT_AND_WEAPONS",
                List.of("sword", "weapon", "combat", "attack", "shield", "parry", "combo", "better combat", "simply swords", "dagger", "claymore", "spear", "glaive"),
                "COMBAT MASTERY: Blighted Dreams uses Better Combat and Simply Swords. Attacks chain into fluid combos. "
                        + "Different weapon classes offer distinct advantages: daggers strike swiftly, claymores cleave wide arcs, spears grant reach. "
                        + "Timing dodges and landing combo finishers yields critical hits. Never button-mash against armored foes.");

        register("CURIOS_AND_RINGS",
                List.of("curio", "curios", "ring", "rings", "amulet", "belt", "necklace", "charm", "accessory", "accessories"),
                "CURIOS & ACCESSORIES: Curio slots hold rings, amulets, and relics that grant persistent passive protections, "
                        + "speed bonuses, extra reach, and damage amplification. Seek out unique dungeon curios like the Ring of Sins.");

        register("EXPLORATION_AND_RUINS",
                List.of("dungeon", "dungeons", "loot", "chest", "ruin", "ruins", "crypt", "draugr", "tower", "lost", "where", "explore", "underground"),
                "DUNGEONS & EXPEDITIONS: The realm is peppered with multi-floor roguelike dungeons and Draugr crypts. "
                        + "Danger increases dramatically on lower floors, but so does the tier of Apotheosis loot and ancient chests. "
                        + "Always keep an exit route marked with torches.");
    }

    private static void register(String id, List<String> keywords, String summary) {
        TOPICS.put(id, new KnowledgeTopic(id, keywords, summary));
    }

    private HelperKnowledge() {}

    /**
     * Builds a tailored knowledge string for the prompt based on query keywords and in-game state cues.
     */
    public static String buildRelevantKnowledge(String query, String state, String fallbackBaseKnowledge) {
        String combined = ((query == null ? "" : query) + " " + (state == null ? "" : state)).toLowerCase(Locale.ROOT);
        List<String> matchedSummaries = new ArrayList<>();

        for (KnowledgeTopic topic : TOPICS.values()) {
            for (String kw : topic.keywords()) {
                if (combined.contains(kw)) {
                    matchedSummaries.add(topic.summary());
                    break;
                }
            }
        }

        if (matchedSummaries.isEmpty()) {
            return fallbackBaseKnowledge != null && !fallbackBaseKnowledge.isBlank()
                    ? fallbackBaseKnowledge.strip()
                    : "Survive the blighted realm, conquer dungeons, upgrade Apotheosis gear with gems, and prepare for the Seven Sins.";
        }

        StringBuilder sb = new StringBuilder();
        for (String summary : matchedSummaries) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(summary);
        }
        return sb.toString();
    }
}
