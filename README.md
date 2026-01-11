# Infinity Nexus - Satisfactory Build Tool User Guide

## Introduction
Welcome to the **Satisfactory Build Tool**! This mod brings the efficient building mechanics of *Satisfactory* into Minecraft. It allows for instant structure placement, precise holographic positioning, and effective area clearing.

## The Build Tool
The **Build Tool** is your primary item for all operations.

### Modes
Cycle through modes by pressing **`M`** (Default).

#### 1. Construction Mode (Green)
*The default mode for placing structures.*
- **Selection**: Hold the tool and press **`B`** to open the structure library.
- **Placement**: Right-click to place the holographic structure.
- **Hologram Control**:
    - **Rotate**: Press **`R`** to rotate the structure 90 degrees.
    - **Fix Position**: Press **`X`** to **LOCK** the hologram in place. This allows you to walk around and inspect it, or use the arrow keys to nudge it.
    - **Nudge**: When Fixed (`X`), use **Arrow Keys**, **Page Up**, and **Page Down** to move the hologram block-by-block.
    - **Zoop**: When Fixed (`X`), press **`C`** to toggle **Zoop Mode**. Look in a direction to expand the structure in a line!

#### 2. Destruction Mode (Red)
*Efficiently dismantle what you've built.*
- **Action**: Right-click any part of a structure placed (red box marked) by this mod to instantly remove it.
- **Refund**: All materials are refunded if structure is not itens-only.
- **Safety**: Only structures tracked by the mod can be dismantled this way.

#### 3. Clear Mode (Blue)
*Clear land or flatten terrain.*
- **Select Area**:
    1.  **Shift + Right-Click** a block to set Point 1.
    2.  **Shift + Right-Click** another block to set Point 2.
- **Action**: **Shift + Right-Click** in a block inside the selected box to open the manage menu.
    - **Clear All**: Removes everything.
    - **Flatten**: Removes blocks above the lowest point, fills air below.
    - **OBS**: If you have block in your off-hand, the first layer in **Flatten** is replaced to.

## Controls & Keybindings
All keys can be reconfigured in `Options > Controls > Key Binds > Satisfactory Build Tool`.

| Default Key | Function | Description |
| :--- | :--- | :--- |
| **M** | **Cycle Mode** | Switch between Construction, Destruction, and Clear. |
| **B** | **Open Menu** | Open the library to select a structure. |
| **Z** | **Cycle Structure** | Quickly switch to the next variant in the current category. |
| **R** | **Rotate** | PROTIP: Rotates the hologram clockwise. |
| **X** | **Fix / Lock** | **CRITICAL**: Locks the hologram in world space. Unlocks advanced features below. |
| **C** | **Zoop Toggle** | **REQ: Fixed Mode**. Toggles linear placement (Zooping). |
| **Arrows** | **Nudge** | **REQ: Fixed Mode**. Move the structure North/South/East/West. |
| **PgUp/Dn** | **Nudge Y** | **REQ: Fixed Mode**. Move the structure Up/Down. |

## Advanced Workflow: How to Zoop
1.  Select a structure (e.g., Foundation).
2.  Aim where you want it.
3.  Press **`X`** to **FIX** the position. The hologram turns white/solid.
4.  Press **`C`** to enable **Zoop**.
5.  Look in the direction you want to build. The hologram will expand!
6.  Shift + Right-Click to build the row.

## Custom Structures
1.  Use **Construction Mode** workflow to select an area (Shift+Right Click two points with build tool in off-hand).
2.  The structure is automatically saved on select tow points
3.  Your build is now in the "Blueprint" tab!
4.  You can edit, delete and select custom structures in Blueprint tab!
