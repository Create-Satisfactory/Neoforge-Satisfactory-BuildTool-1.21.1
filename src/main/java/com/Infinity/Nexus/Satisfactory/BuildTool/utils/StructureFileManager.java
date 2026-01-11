package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.util.Optional;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utilitário para operações de carregamento/salvamento de arquivos de estrutura
 */
public class StructureFileManager {
    
    private static final Path SERVER_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/server");
    private static final Path CLIENT_STRUCTURES_DIR = Paths.get("config/InfinityNexus/structures/client");
    
    private final Gson gson;
    
    public StructureFileManager() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }
    
    /**
     * Garante que um diretório exista, criando-o se necessário
     */
    public boolean ensureDirectoryExists(Path directory, String purpose) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                SatisfactoryBuildTool.LOGGER.info("Created {} directory: {}", purpose, directory.toString());
                return true;
            }
            return true;
        } catch (IOException e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to create {} directory: {}", purpose, directory.toString(), e);
            return false;
        }
    }
    
    /**
     * Lista todos os arquivos JSON em um diretório
     */
    public File[] getJsonFiles(Path directory) {
        File dir = directory.toFile();
        return dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
    }
    
    /**
     * Carrega uma estrutura de um arquivo JSON
     */
    public StructureLoader.StructureData loadStructureFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            StructureLoader.StructureData structure = gson.fromJson(reader, StructureLoader.StructureData.class);
            
            if (structure != null && structure.isValid()) {
                // Pré-processa os estados dos blocos para melhor performance
                structure.preprocessBlockStates();
                return structure;
            } else {
                SatisfactoryBuildTool.LOGGER.error("Invalid structure in file: {}", file.getName());
                return null;
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Error reading structure file: {}", file.getName(), e);
            return null;
        }
    }
    
    /**
     * Salva uma estrutura em um arquivo JSON
     */
    public boolean saveStructureToFile(StructureLoader.StructureData structure, Path directory) {
        return saveStructureToFile(structure, directory, structure.getName().toLowerCase());
    }
    
    /**
     * Salva uma estrutura em um arquivo JSON com nome específico
     */
    public boolean saveStructureToFile(StructureLoader.StructureData structure, Path directory, String fileNameWithoutExtension) {
        try {
            ensureDirectoryExists(directory, "save");
            
            Path filePath = directory.resolve(fileNameWithoutExtension + ".json");
            String json = gson.toJson(structure);
            
            Files.write(filePath, json.getBytes());
            SatisfactoryBuildTool.LOGGER.info("Saved structure to: {}", filePath.toString());
            return true;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to save structure: {}", structure.getName(), e);
            return false;
        }
    }
    
    /**
     * Remove um arquivo de estrutura
     */
    public boolean deleteStructureFile(String structureName, Path directory) {
        try {
            Path filePath = directory.resolve(structureName.toLowerCase() + ".json");
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                StructureLoader.loadClientStructures();
                SatisfactoryBuildTool.LOGGER.info("Deleted structure file: {}", filePath.toString());
                return true;
            }
            
            // Tenta com a convenção de nomenclatura (minúsculas + underscores)
            Path underscorePath = directory.resolve(structureName.toLowerCase().replace(" ", "_") + ".json");
            if (Files.exists(underscorePath)) {
                Files.delete(underscorePath);
                StructureLoader.loadClientStructures();
                SatisfactoryBuildTool.LOGGER.info("Deleted structure file (underscore): {}", underscorePath.toString());
                return true;
            }
            
            return false;
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to delete structure file: {}", structureName, e);
            return false;
        }
    }

    /**
     * Remove um arquivo de estrutura baseado nos dados da estrutura
     */
    public boolean deleteStructureFile(StructureLoader.StructureData structure, Path directory) {
        if (structure == null) return false;
        
        String structureName = structure.getName();
        String fileName = structure.getFileName();
        
        try {
            // 1. Tenta pelo nome de arquivo rastreado
            if (fileName != null && !fileName.isEmpty()) {
                Path filePath = directory.resolve(fileName);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    StructureLoader.loadClientStructures();
                    SatisfactoryBuildTool.LOGGER.info("Deleted structure file (tracked): {}", filePath.toString());
                    return true;
                }
            }
            
            // 2. Fallback para deleção por nome (já faz reloads)
            return deleteStructureFile(structureName, directory);
            
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to delete structure: {}", structureName, e);
            return false;
        }
    }
    
    /**
     * Renomeia um arquivo de estrutura
     */
    public boolean renameStructureFile(String oldName, String newName, Path directory) {
        try {
            SatisfactoryBuildTool.LOGGER.info("Attempting to rename structure: oldName={}, newName={}, directory={}", oldName, newName, directory.toString());
            
            Path oldFilePath = directory.resolve(oldName.toLowerCase() + ".json");
            Path newFilePath = directory.resolve(newName.toLowerCase().replace(" ", "_") + ".json");
            
            SatisfactoryBuildTool.LOGGER.info("Old file path: {}", oldFilePath.toString());
            SatisfactoryBuildTool.LOGGER.info("New file path: {}", newFilePath.toString());
            SatisfactoryBuildTool.LOGGER.info("Old file exists: {}", Files.exists(oldFilePath));
            
            if (Files.exists(oldFilePath)) {
                // Verificar se o novo arquivo já existe
                if (Files.exists(newFilePath)) {
                    SatisfactoryBuildTool.LOGGER.warn("New file already exists, will be overwritten: {}", newFilePath.toString());
                }
                
                Files.move(oldFilePath, newFilePath);
                SatisfactoryBuildTool.LOGGER.info("Successfully renamed structure file from {} to {}", oldFilePath.toString(), newFilePath.toString());
                return true;
            } else {
                SatisfactoryBuildTool.LOGGER.error("Old file does not exist: {}", oldFilePath.toString());
                return false;
            }
        } catch (Exception e) {
            SatisfactoryBuildTool.LOGGER.error("Failed to rename structure file from {} to {}", oldName, newName, e);
            return false;
        }
    }
    
    // Getters para os diretórios
    public Path getServerStructuresDir() { return SERVER_STRUCTURES_DIR; }
    public Path getClientStructuresDir() { return CLIENT_STRUCTURES_DIR; }
    
    // Simplified implementation without complex Optional handling
}