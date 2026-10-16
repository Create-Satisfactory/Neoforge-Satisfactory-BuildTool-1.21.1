# Satisfactory Build Tool — User & Modpack Developer Guide

Welcome to the **Satisfactory Build Tool**! This mod brings the holographic building workflow, FICSIT UI, and progression mechanics of *Satisfactory* into Minecraft (NeoForge 1.21.1) with deep integration for **Create**, **Create: Copycats+**, and **FTB Quests**.

---

## 1. The Build Tool & Modes

The **Build Tool** is your primary item for construction, deconstruction, and terrain preparation.  
Cycle between active modes at any time by pressing **`M`** (default).

### 1. Construction Mode (Green)
*The default mode for selecting and placing structures.*

* **Open FICSIT Builder Menu**: **Right-Click** while holding the Build Tool to open the Builder UI (`Builder`, `Customizer`, and `Blueprints` tabs).
* **Place Structure**: **Shift + Right-Click** to materialize the holographic structure in the world using an animated bottom-to-top FICSIT construction wave.
* **Hologram Validity Tinting**:
  * **Cyan-Blue**: Valid placement and sufficient resources.
  * **Amber-Cyan**: Hologram is locked in place (`X`) for inspection or nudging.
  * **Red-Orange**: Placement is obstructed (`Encroaching other's Clearance!`) or you are missing required items (`Cannot afford!`).
* **Hologram Controls**:
  * **Rotate (`R`)**: Rotates the hologram 90° clockwise (automatically rotating internal Create shafts, belts, and Copycat orientations).
  * **Quick-Switch Variant (`Z`)**: Cycles through related structures in the same subcategory (e.g., cycling between 1m, 2m, and 4m Foundations).
  * **Lock / Fix Hologram (`X`)**: Locks the hologram at its current world coordinates so you can walk around and inspect the alignment.
  * **Nudge (`Arrow Keys` / `Page Up` / `Page Down`)**: While locked (`X`), nudges the hologram 1 block at a time along the X, Y, or Z axes.
  * **Zoop (`C`)**: While locked (`X`), toggles linear **Zoop Mode**. Look in any direction to extend a line of structures up to the configured Zoop limit.

### 2. Destruction Mode (Red)
*Cleanly dismantle placed structures as a single multiblock unit.*

* **Dismantle Structure**: Aim at any block belonging to a placed structure (highlighted by a red wireframe box) and **Right-Click** to deconstruct the entire building at once.
* **Full FICSIT Refund**: Refunds the exact required FICSIT items (or constituent blocks if `onlyItems` is `false`) directly into your inventory.
* **Container & Copycat Safety**: Any items inside standard storage containers are transferred to the player, while internal **Create: Copycats+** camo blocks are stripped cleanly so they cannot be duplicated on dismantle.
* **Hide Bounding Box**: **Ctrl + Left-Click** a structure in Destruction Mode to hide its client-side red wireframe box without destroying the building.

### 3. Clear Mode (Blue)
*Clear obstructions or flatten terrain for factory floors.*

1. **Select Area**:
  * **Shift + Right-Click** a block to set **Point 1**.
  * **Shift + Right-Click** a second block to set **Point 2** (or **Left-Click** in the air to reset your selection).
2. **Execute Action**: **Shift + Right-Click** (or **Left-Click**) any block inside the highlighted selection box to open the Clear Mode prompt:
  * **Clear All**: Removes all non-protected blocks inside the volume and teleports drops to you.
  * **Flatten**: Clears blocks above the lowest Y-layer. If you hold a block item in your **Off-Hand**, the bottom layer is automatically filled/replaced using that block from your inventory.

---

## 2. FICSIT Structure Protection (Indestructibility)

Every structure placed by the Build Tool is tracked server-side in `satisfactory_build_tool_structures.dat`:

* **Mining & Creative Protection**: Blocks belonging to a placed structure cannot be mined in Survival or broken via Left-Click in Creative Mode.
* **Anti-Wrench Protection**: Players cannot **Shift + Right-Click** with a Create Wrench to harvest free Mixers, Basins, Shafts, or Copycats out of a placed FICSIT machine.
* **Explosion & Clear Immunity**: Placed structures are immune to Creeper/TNT blasts and are skipped by **Clear Mode**.
* **Removal**: Placed structures can **only** be removed using the Build Tool in **Destruction Mode**.

---

## 3. Controls & Keybindings

All keybinds can be customized under `Options > Controls > Key Binds > Satisfactory Build Tool`.

### In-Game Controls
| Default Input | Function | Description |
| :--- | :--- | :--- |
| **Right-Click** | **Build Menu / Dismantle** | Opens the Builder UI (Construction Mode) or dismantles a structure (Destruction Mode). |
| **Shift + Right-Click** | **Build / Select Area** | Places the active hologram (Construction Mode) or sets area points (Clear Mode). |
| **`M`** | **Cycle Mode** | Switches between `Construction`, `Destruction`, `Clear`, and `None`. |
| **`R`** | **Rotate Hologram** | Rotates the preview 90° clockwise. |
| **`X`** | **Lock Hologram** | Locks/unlocks the hologram in world space; enables Nudging and Zooping. |
| **`C`** | **Toggle Zoop** | *(Requires Lock Mode `X`)* Toggles linear multi-placement along your look direction. |
| **`Z`** | **Quick Switch** | Cycles to the next structure variant in the same GUI subcategory. |
| **`Arrow Keys`** | **Nudge Horizontal** | *(Requires Lock Mode `X`)* Moves the locked hologram North, South, East, or West. |
| **`Page Up` / `Down`** | **Nudge Vertical** | *(Requires Lock Mode `X`)* Moves the locked hologram Up or Down. |

### Builder UI Shortcuts
| Input | Function | Description |
| :--- | :--- | :--- |
| **`LMB` (Left-Click)** | **Select** | Immediately equips the clicked structure (or Color/Material swatch) and closes the menu. |
| **`Tab`** | **Cycle Tabs** | Switches between `Builder`, `Customizer`, and `Blueprints`. |
| **`Space Bar`** | **Search** | Focuses the `Search for Building...` input bar. |
| **`Esc`** | **Close** | Closes the Builder UI. |

---

## 4. Saving Custom Player Blueprints

1. Equip the **Build Tool** in your **Off-Hand** while in **Construction Mode**.
2. **Shift + Right-Click** two opposite corners of your build to set Point 1 and Point 2.
3. The structure is automatically saved as a Create-compatible `.nbt` schematic and `.json` metadata file in `config/InfinityNexus/structures/client/` (preserving BlockEntity NBT such as Copycat materials and Basin filters).
4. Open the Build Menu (`Right-Click` in Main Hand) and switch to the **Blueprints** tab to **Equip**, **Edit** (rename, change category path, or set an icon), or **Delete** your saved blueprint.

---

## 5. Modpack Developer Guide: Structure JSONs & `.nbt` Schematics

Structures are split into two parts:
1. **Create `.nbt` Schematics**: Store the 3D block layout and BlockEntity NBT (created with a Create Schematic & Quill or Vanilla Structure Block).
2. **Metadata `.json` Files**: Store the FICSIT name, category, icon, power consumption, tier/milestone requirements, and item costs.

### Where to Place Structure Files
* **Datapack / Mod JAR (Recommended for Modpacks)**:  
  `data/satisfactory_build_tool/structures/<name>.json` (and `.nbt` files in `structures/` or `schematics/`).
* **Server Config Folder**:  
  `config/InfinityNexus/structures/server/<name>.json` (and `.nbt` files in the same folder or `server/schematics/`).

### Multi-Material Structure JSON Format
Use the `"schematics"` array with `"MaterialName:filename.nbt"` entries to map different `.nbt` files to your Customizer materials (configured in `satisfactory_build_tool-common.toml`). The first entry in the array is used as the default fallback:

```json
{
  "name": "Foundation 4m",
  "author": "FICSIT",
  "description": "Standard 4m factory floor foundation.",
  "category": "Foundations",
  "guiPath": "Foundations > Foundations",
  "iconPath": "minecraft:stone",
  "schematics": [
    "Stone:foundation_4m_stone.nbt",
    "Concrete:foundation_4m_concrete.nbt",
    "Iron:foundation_4m_iron.nbt"
  ],
  "tier": 1,
  "subtier": "Base Building",
  "energyConsumption": 0,
  "onlyItems": true,
  "requiredItems": [
    {
      "itemId": "satisfactory:concrete",
      "count": 5
    },
    {
      "itemId": "satisfactory:iron_plate",
      "count": 2
    }
  ]
}
```

- iconPath: Accepts either any registered Item/Block ID (e.g., "create:mechanical_mixer") or a .png texture resource location (e.g., "satisfactory_build_tool:textures/gui/icons/constructor.png").
- guiPath: Uses "Category > Subcategory" format (e.g., "Production > Smelters"). All structures sharing the same guiPath can be cycled in-game with Z.
- onlyItems: When true, building consumes (and dismantling refunds) the exact list in requiredItems rather than requiring the raw blocks inside the .nbt schematic.
### 6. Modpack Developer Guide: Unlocking Buildings & Materials
Progression is shared across the entire FTB Team. Whenever a building or material is unlocked, it is automatically synced to every online member of the team.
You can wire up unlocks using any of the three methods below:

#### Method A: Via "subtier" and "tier" in the Building .json
- Unlock by Milestone Title or Quest ID: Set "subtier" in your structure .json to match either the exact Title of an FTB Quest (e.g., "subtier": "Part Assembly") or its FTB Quest Hex ID (e.g., "subtier": "4B9F2A10"). As soon as any teammate completes that quest, the structure unlocks for the whole team.
- Unlock by Chapter Tier: If "subtier" is left blank ("") and "tier" is greater than 0 (e.g., "tier": 2), the structure unlocks as soon as any quest inside an FTB Quests Chapter containing "Tier 2" in its title is unlocked or completed.
#### Method B: Via FTB Quest Tags (In-Game Editor — No JSON Edits Needed)In /ftbquests editing_mode, right-click any Quest -> Edit -> add one or more Tags:
FTB Quest TagEffect When Quest Completes
`unlock:<building_name>` Unlocks that building for the team (case-insensitive, spaces or underscores). Example: unlock:constructor or unlock:foundation_4m
`unlock_material:<Material>` Unlocks a Customizer material in Customizer > Materials. Example: unlock_material:Concrete
`<custom_subtier_tag>` Unlocks every building whose .json has "subtier": "<custom_subtier_tag>".
#### Method C: Via Commands or FTB Quest Command Rewards
Use the /structure progression commands (requires OP permission level 2, or an FTB Quest Command Reward with Run as Server enabled):
```
# Unlock a specific structure for the player's entire FTB Team
/structure progression unlock <player> "<Structure Name>"

# Set the team's unlocked Tier level
/structure progression setTier <player> <tier_number>

# Complete a specific subtier/milestone name for the team
/structure progression completeSubtier <player> "<Subtier Name>"

# Unlock a Customizer material for the team
/structure progression material unlock <player> <MaterialName>

# List all configured Customizer materials
/structure progression material list

# Force-sync unlocked structures to the player's team
/structure progression sync <player>

# Hot-reload all server/datapack structure JSONs & .nbt schematics and re-sync online players
/structure progression reload
```

# TODO:
* Implement Copycat NBT recoloring and Primary + Secondary dual-tone swatches in `PlaceStructure`, `StructurePreview`, and `BuildToolScreen`
* Add per-material recipe cost overrides to `StructureLoader` so each material variant in `"schematics"` can define its own item cost
* Add a FICSIT "New Buildings Unlocked" HUD notification banner when an FTB Quest unlocks new schematics