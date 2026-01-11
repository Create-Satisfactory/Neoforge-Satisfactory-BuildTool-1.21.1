package com.Infinity.Nexus.Satisfactory.BuildTool.progression;

import java.util.*;

public class PlayerProgression {
    private UUID player;
    private int tier;
    private Set<String> completedSubtiers;
    private Set<String> unlockedStructures;
    private Set<String> unlockedMaterials;
    private Map<String, Integer> upgrades;

    public PlayerProgression(UUID player) {
        this.player = player;
        this.tier = 0;
        this.completedSubtiers = new HashSet<>();
        this.unlockedStructures = new HashSet<>();
        this.unlockedMaterials = new HashSet<>();
        // Unlock "Stone" and "Template Foundation" by default
        this.unlockedMaterials.add("Stone");
        this.unlockedStructures.add("Template Foundation");
        this.upgrades = new HashMap<>();
    }

    public UUID getPlayer() {
        return player;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public Set<String> getCompletedSubtiers() {
        return completedSubtiers;
    }

    public void addCompletedSubtier(String subtier) {
        this.completedSubtiers.add(subtier);
    }
    
    public boolean hasCompletedSubtier(String subtier) {
        return this.completedSubtiers.contains(subtier);
    }

    public Set<String> getUnlockedStructures() {
        return unlockedStructures;
    }

    public void unlockStructure(String structureId) {
        this.unlockedStructures.add(structureId);
    }
    
    public boolean isStructureUnlocked(String structureId) {
        return this.unlockedStructures.contains(structureId);
    }

    public Map<String, Integer> getUpgrades() {
        return upgrades;
    }

    public void setUpgrade(String upgrade, int level) {
        this.upgrades.put(upgrade, level);
    }
    
    public int getUpgradeLevel(String upgrade) {
        return this.upgrades.getOrDefault(upgrade, 0);
    }
    
    public Set<String> getUnlockedMaterials() {
        return unlockedMaterials;
    }
    
    public void unlockMaterial(String materialName) {
        this.unlockedMaterials.add(materialName);
    }
    
    public boolean isMaterialUnlocked(String materialName) {
        return this.unlockedMaterials.contains(materialName);
    }
    
    // Ensure all collections are initialized (for backward compatibility with old save files)
    public void ensureDefaults() {
        if (this.completedSubtiers == null) {
            this.completedSubtiers = new HashSet<>();
        }
        if (this.unlockedStructures == null) {
            this.unlockedStructures = new HashSet<>();
        }
        if (this.unlockedMaterials == null) {
            this.unlockedMaterials = new HashSet<>();
            this.unlockedMaterials.add("Stone"); // Default unlock
        }
        if (!this.unlockedStructures.contains("Template Foundation")) {
            this.unlockedStructures.add("Template Foundation");
        }
        if (this.upgrades == null) {
            this.upgrades = new HashMap<>();
        }
    }
}
