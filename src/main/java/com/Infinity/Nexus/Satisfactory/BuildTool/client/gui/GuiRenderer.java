package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ColorUtils;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ModConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utilitário para renderização de componentes da interface
 */
public class GuiRenderer {
    
    private static final int TAB_HEIGHT = 25;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int PADDING = 10;
    private static final int GRID_ITEM_SIZE = 80;
    private static final int GRID_SPACING = 10;
    private static final int INFO_PANEL_WIDTH = 200;
    private static final ResourceLocation DEFAULT_ICON = ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", "textures/gui/icons/factory_default.png");
    
    private final TextureManager textureManager;
    
    public GuiRenderer() {
        this.textureManager = Minecraft.getInstance().getTextureManager();
    }
    
    /**
     * Renderiza um item de estrutura na grade
     */
    public void renderStructureItem(GuiGraphics guiGraphics, StructureLoader.StructureData structure, 
                                  int x, int y, int mouseX, int mouseY, boolean isSelected) {
        // Fundo do item
        int bgColor = isSelected ? ModConstants.Colors.DARK_GRAY : ColorUtils.darken(ModConstants.Colors.GRAY, 0.2f);
        guiGraphics.fill(x, y, x + GRID_ITEM_SIZE, y + GRID_ITEM_SIZE, bgColor);
        
        // Borda
        int borderColor = isSelected ? ModConstants.Colors.WHITE : ModConstants.Colors.LIGHT_GRAY;
        guiGraphics.renderOutline(x, y, GRID_ITEM_SIZE, GRID_ITEM_SIZE, borderColor);
        
        // Ícone da estrutura
        ResourceLocation icon = getResourceLocation(structure.getIconPath());
        if (icon != null) {
            guiGraphics.blit(icon, x + 8, y + 8, 0, 0, 48, 48, 48, 48);
        } else {
            // Ícone padrão
            guiGraphics.blit(DEFAULT_ICON, x + 8, y + 8, 0, 0, 48, 48, 48, 48);
        }
        
        // Nome da estrutura (truncado se necessário)
        String displayName = truncateString(structure.getName(), 12);
        guiGraphics.drawString(Minecraft.getInstance().font, displayName, 
                             x + 4, y + GRID_ITEM_SIZE - 12, 0xFFFFFF);
        
        // Tooltip ao passar o mouse
        if (mouseX >= x && mouseX <= x + GRID_ITEM_SIZE && 
            mouseY >= y && mouseY <= y + GRID_ITEM_SIZE) {
            renderTooltip(guiGraphics, structure, mouseX, mouseY);
        }
    }
    
    /**
     * Renderiza o painel de informações da estrutura selecionada
     */
    public void renderInfoPanel(GuiGraphics guiGraphics, StructureLoader.StructureData structure, 
                              int screenWidth, int screenHeight) {
        int panelX = screenWidth - INFO_PANEL_WIDTH;
        int panelY = PADDING + TAB_HEIGHT + PADDING;
        int panelHeight = screenHeight - panelY - PADDING;
        
        // Fundo do painel
        guiGraphics.fill(panelX, panelY, screenWidth - PADDING, panelY + panelHeight, ColorUtils.rgbaToInt(0f, 0f, 0f, 0.67f));
        guiGraphics.renderOutline(panelX, panelY, INFO_PANEL_WIDTH - PADDING, panelHeight, ModConstants.Colors.WHITE);
        
        if (structure == null) {
            // Mensagem quando nenhuma estrutura está selecionada
            guiGraphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.translatable("gui.satisfactory_build_tool.info.no_selection"),
                    panelX + (INFO_PANEL_WIDTH - PADDING) / 2,
                    panelY + 50,
                    0xAAAAAA);
            return;
        }
        
        // Título (nome da estrutura)
        guiGraphics.drawCenteredString(Minecraft.getInstance().font,
                structure.getName(),
                panelX + (INFO_PANEL_WIDTH - PADDING) / 2,
                panelY + 10,
                0xFFFFFF);
        
        // Separador
        guiGraphics.fill(panelX + 10, panelY + 25, panelX + INFO_PANEL_WIDTH - 20, panelY + 26, ModConstants.Colors.WHITE);
        
        // Informações técnicas
        int startY = panelY + 35;
        int lineHeight = 12;
        
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("gui.satisfactory_build_tool.info.dimensions").getString() + ": " +
                structure.getWidth() + "x" + structure.getLength() + "x" + structure.getHeight(),
                panelX + 10, startY, 0xAAAAAA);
        
        startY += lineHeight;
        
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("gui.satisfactory_build_tool.info.blocks").getString() + ": " +
                (structure.getWidth() * structure.getHeight() * structure.getLength()),
                panelX + 10, startY, 0xAAAAAA);
        
        startY += lineHeight;
        
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("gui.satisfactory_build_tool.info.category").getString() + ": " +
                structure.getCategory(),
                panelX + 10, startY, 0xAAAAAA);
        
        startY += lineHeight * 2;
        
        // Descrição
        String description = structure.getDescription();
        if (description != null && !description.isEmpty()) {
            guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("gui.satisfactory_build_tool.info.description").getString() + ":",
                    panelX + 10, startY, 0xFFFFFF);
            
            startY += lineHeight;
            
            // Quebra a descrição em linhas manualmente
            String[] words = description.split(" ");
            StringBuilder currentLine = new StringBuilder();
            List<String> wrappedLines = new ArrayList<>();
            
            for (String word : words) {
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                if (Minecraft.getInstance().font.width(testLine) > INFO_PANEL_WIDTH - 30) {
                    if (currentLine.length() > 0) {
                        wrappedLines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        wrappedLines.add(word);
                    }
                } else {
                    currentLine = new StringBuilder(testLine);
                }
            }
            
            if (currentLine.length() > 0) {
                wrappedLines.add(currentLine.toString());
            }
            
            for (String line : wrappedLines) {
                if (startY < panelY + panelHeight - 20) {
                    guiGraphics.drawString(Minecraft.getInstance().font, line, 
                                         panelX + 10, startY, 0xCCCCCC);
                    startY += lineHeight;
                }
            }
        }
    }
    
    /**
     * Renderiza tooltip para estrutura
     */
    private void renderTooltip(GuiGraphics guiGraphics, StructureLoader.StructureData structure, 
                             int mouseX, int mouseY) {
        List<Component> tooltip = List.of(
                Component.literal(structure.getName()),
                Component.literal(structure.getDescription())
        );
        
        guiGraphics.renderTooltip(Minecraft.getInstance().font, tooltip, 
                                Optional.empty(), mouseX, mouseY);
    }
    
    /**
     * Trunca string se for muito longa
     */
    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Converte string para ResourceLocation
     */
    private ResourceLocation getResourceLocation(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        try {
            if (path.contains(":")) {
                return ResourceLocation.parse(path);
            } else {
                return ResourceLocation.fromNamespaceAndPath("satisfactory_build_tool", path);
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    // Getters para constantes
    public static int getTabHeight() { return TAB_HEIGHT; }
    public static int getSidebarWidth() { return SIDEBAR_WIDTH; }
    public static int getPadding() { return PADDING; }
    public static int getGridItemSize() { return GRID_ITEM_SIZE; }
    public static int getGridSpacing() { return GRID_SPACING; }
    public static int getInfoPanelWidth() { return INFO_PANEL_WIDTH; }
}