package com.Infinity.Nexus.Satisfactory.BuildTool.client.gui;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureFileManager;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureLoader;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.ModConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;

public class BlueprintEditorScreen extends Screen {
    private final StructureLoader.StructureData structure;
    private final Runnable onSaveCallback;
    private final String originalFileName;
    
    private EditBox nameField;
    private EditBox authorField;
    private EditBox descriptionField;
    private EditBox guiPathField;
    private EditBox iconPathField;
    
    private Button saveButton;
    private Button cancelButton;
    
    public BlueprintEditorScreen(StructureLoader.StructureData structure, Runnable onSaveCallback) {
        super(Component.translatable("gui.satisfactory_build_tool.editor.title"));
        this.structure = structure;
        this.onSaveCallback = onSaveCallback;
        this.originalFileName = structure.getName().toLowerCase().replace(" ", "_");
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int fieldWidth = 250;  // Mais largo
        int fieldHeight = 20;
        int buttonHeight = 20;
        int buttonWidth = 85;
        int fieldX = (centerX - fieldWidth / 2) + buttonWidth / 2;

        // Name field
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.satisfactory_build_tool.editor.name"), b -> {})
                  .bounds(fieldX - 90, centerY - 100, buttonWidth, buttonHeight)
                  .build()
        );
        nameField = new EditBox(this.font, fieldX, centerY - 100, fieldWidth, fieldHeight, Component.translatable("gui.satisfactory_build_tool.editor.name"));
        nameField.setMaxLength(64); // Limite adequado para nomes
        nameField.setValue(structure.getName());
        this.addRenderableWidget(nameField);
        
        // Author field
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.satisfactory_build_tool.editor.author"), b -> {})
                  .bounds(fieldX - 90, centerY - 70, buttonWidth, buttonHeight)
                  .build()
        );
        authorField = new EditBox(this.font, fieldX, centerY - 70, fieldWidth, fieldHeight, Component.translatable("gui.satisfactory_build_tool.editor.author"));
        authorField.setMaxLength(64); // Limite adequado para autores
        authorField.setValue(structure.getAuthor());
        this.addRenderableWidget(authorField);
        
        // Description field
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.satisfactory_build_tool.editor.description"), b -> {})
                  .bounds(fieldX - 90, centerY - 40, buttonWidth, buttonHeight)
                  .build()
        );
        descriptionField = new EditBox(this.font, fieldX, centerY - 40, fieldWidth, fieldHeight, Component.translatable("gui.satisfactory_build_tool.editor.description"));
        descriptionField.setMaxLength(256); // Limite maior para descrições
        descriptionField.setValue(structure.getDescription());
        this.addRenderableWidget(descriptionField);
        
        // GUI Path field
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.satisfactory_build_tool.editor.gui_path"), b -> {})
                  .bounds(fieldX - 90, centerY - 10, buttonWidth, buttonHeight)
                  .build()
        );
        guiPathField = new EditBox(this.font, fieldX, centerY - 10, fieldWidth, fieldHeight, Component.translatable("gui.satisfactory_build_tool.editor.gui_path"));
        guiPathField.setMaxLength(128); // Limite adequado para paths
        guiPathField.setValue(structure.getGuiPath() != null ? structure.getGuiPath() : "");
        this.addRenderableWidget(guiPathField);
        
        // Icon Path field (pre-filled with default value)
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.satisfactory_build_tool.editor.icon_path"), b -> {})
                  .bounds(fieldX - 90, centerY + 20, buttonWidth, buttonHeight)
                  .build()
        );
        iconPathField = new EditBox(this.font, fieldX, centerY + 20, fieldWidth, fieldHeight, Component.translatable("gui.satisfactory_build_tool.editor.icon_path"));
        iconPathField.setMaxLength(128); // Limite adequado para paths de ícones
        iconPathField.setValue(structure.getIconPath() != null ? structure.getIconPath() : "minecraft:textures/item/paper.png");
        this.addRenderableWidget(iconPathField);
        
        // Save button
        saveButton = Button.builder(Component.translatable("gui.satisfactory_build_tool.info.save"), b -> saveChanges())
                          .bounds(centerX - buttonWidth - 5, centerY + 60, buttonWidth, buttonHeight)
                          .build();
        this.addRenderableWidget(saveButton);
        
        // Cancel button
        cancelButton = Button.builder(Component.translatable("gui.satisfactory_build_tool.close_button"), b -> this.onClose())
                           .bounds(centerX + 5, centerY + 60, buttonWidth, buttonHeight)
                           .build();
        this.addRenderableWidget(cancelButton);
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Primeiro renderiza o fundo escuro (sem efeito de desfoque da tela pai)
        graphics.fill(0, 0, this.width, this.height, 0xCC000000);

        // Depois o painel principal
        int panelWidth = 400;
        int panelHeight = 280;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF2D2D2D);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, ModConstants.Colors.WHITE);

        // Título
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 15, 0xFFFFFF);

        // Por último, renderiza apenas os widgets desta tela (botões e campos de texto)
        // em vez de chamar super.render() que renderizaria o fundo borrado da tela pai
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }
    
    private void saveChanges() {
        // Atualiza os valores da estrutura com os dados dos campos
        try {
            // Usar reflexão para criar uma cópia da estrutura com valores atualizados
            StructureLoader.StructureData updatedStructure = createUpdatedStructure();
            
            if (updatedStructure != null) {
                StructureFileManager fileManager = new StructureFileManager();
                String newName = nameField.getValue();
                String newNameFormatted = newName.toLowerCase().replace(" ", "_");
                
                // Verificar se o nome mudou
                boolean nameChanged = !originalFileName.equals(newNameFormatted);
                
                if (nameChanged) {
                    // Renomear o arquivo existente
                    boolean renamed = fileManager.renameStructureFile(originalFileName, newName, fileManager.getClientStructuresDir());
                    if (!renamed) {
                        SatisfactoryBuildTool.LOGGER.error("Failed to rename structure file");
                        // TODO: Mostrar mensagem de erro
                        return;
                    }
                }
                
                // Salvar a estrutura atualizada usando o novo nome
                boolean saved = fileManager.saveStructureToFile(updatedStructure, fileManager.getClientStructuresDir(), newNameFormatted);
                
                if (saved) {
                    // Recarregar as estruturas do cliente
                    StructureLoader.loadClientStructures();
                    
                    // Executar callback se existir
                    if (onSaveCallback != null) {
                        onSaveCallback.run();
                    }
                    
                    this.onClose();
                } else {
                    // TODO: Mostrar mensagem de erro
                    SatisfactoryBuildTool.LOGGER.error("Failed to save structure file");
                }
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save structure changes", e);
            // TODO: Mostrar mensagem de erro para o usuário
        }
    }
    
    private StructureLoader.StructureData createUpdatedStructure() {
        try {
            // Criar nova instância usando reflexão
            StructureLoader.StructureData newInstance = StructureLoader.StructureData.class.getDeclaredConstructor().newInstance();
            
            // Copiar todos os campos usando reflexão
            Field[] fields = StructureLoader.StructureData.class.getDeclaredFields();
            
            for (Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();
                
                // Atualizar campos modificados
                switch (fieldName) {
                    case "name" -> field.set(newInstance, nameField.getValue());
                    case "author" -> field.set(newInstance, authorField.getValue());
                    case "description" -> field.set(newInstance, descriptionField.getValue());
                    case "guiPath" -> field.set(newInstance, guiPathField.getValue().isEmpty() ? null : guiPathField.getValue());
                    case "iconPath" -> field.set(newInstance, iconPathField.getValue().isEmpty() ? null : iconPathField.getValue());
                    default -> {
                        // Copiar valor original para outros campos
                        Object originalValue = field.get(structure);
                        field.set(newInstance, originalValue);
                    }
                }
            }
            
            return newInstance;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to create updated structure", e);
            return null;
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}