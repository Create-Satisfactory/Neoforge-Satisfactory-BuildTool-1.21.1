// ClientStructureSaver.java
package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.Infinity.Nexus.Satisfactory.BuildTool.SatisfactoryBuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.item.BuildTool;
import com.Infinity.Nexus.Satisfactory.BuildTool.networking.packet.StructureSyncS2CPacket;
import com.Infinity.Nexus.Satisfactory.BuildTool.utils.StructureSaver.StructureData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ClientStructureSaver {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /**
     * Salva uma estrutura recebida do servidor no lado do cliente
     */
    public static boolean saveStructureLocally(StructureSyncS2CPacket.StructureData structureData) {
        try {
            // Criar diretório CLIENTE CACHE se não existir
            Path dirPath = StructureLoader.getClientStructuresDir();
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Converter para o formato de dados da estrutura (Usando StructureSaver.StructureData)
            StructureData localStructureData = new StructureData(
                    structureData.name(),
                    structureData.author(),
                    structureData.description(),
                    structureData.category(),
                    structureData.guiPath(),
                    structureData.iconPath(),
                    structureData.tier(),
                    structureData.subtier(),
                    structureData.energyConsumption(),
                    structureData.onlyItems(),
                    structureData.requiredItems(),
                    getWidth(structureData.pattern()),
                    getLength(structureData.pattern()),
                    getHeight(structureData.pattern()),
                    structureData.pattern()
            );

            // Salvar como JSON
            String fileName = structureData.name().toLowerCase().replace(" ", "_") + ".json";
            File outputFile = new File(dirPath.toFile(), fileName);

            try (FileWriter writer = new FileWriter(outputFile)) {
                GSON.toJson(localStructureData, writer);
            }

            SatisfactoryBuildTool.LOGGER.info("Structure saved locally to cache: {}", fileName);
            
            // Converter para Loader Format e adicionar à memória DO CLIENTE
            StructureLoader.StructureData loaderData = StructureConverter.convertToLoaderFormat(localStructureData);
            if (loaderData != null) {
                loaderData.setFileName(fileName);
                StructureLoader.addClientStructure(loaderData, structureData.isOfficial());
                BuildTool.loadStructures(); // Atualiza a lista no Client
            }
            
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Calcula a largura (X) baseada no pattern
     */
    private static int getWidth(List<List<List<String>>> pattern) {
        if (pattern.isEmpty() || pattern.get(0).isEmpty()) return 0;
        return pattern.get(0).get(0).size();
    }

    /**
     * Calcula o comprimento (Z) baseado no pattern
     */
    private static int getLength(List<List<List<String>>> pattern) {
        if (pattern.isEmpty()) return 0;
        return pattern.get(0).size();
    }

    /**
     * Calcula a altura (Y) baseada no pattern
     */
    private static int getHeight(List<List<List<String>>> pattern) {
        return pattern.size();
    }
}