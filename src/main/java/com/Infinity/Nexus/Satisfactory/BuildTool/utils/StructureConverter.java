// StructureConverter.java
package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

public class StructureConverter {

    /**
     * Converte StructureLoader.StructureData para StructureSaver.StructureData
     */
    public static StructureSaver.StructureData convertToSaverFormat(StructureLoader.StructureData loaderData) {
        if (loaderData == null) {
            return null;
        }

        return new StructureSaver.StructureData(
                loaderData.getName(),
                loaderData.getAuthor(),
                loaderData.getDescription(),
                loaderData.getCategory(),
                loaderData.getGuiPath(),
                loaderData.getIconPath(),
                loaderData.getTier(),
                loaderData.getSubtier(),
                loaderData.getEnergyConsumption(),
                loaderData.isOnlyItems(),
                loaderData.getRequiredItems(),
                loaderData.getWidth(),
                loaderData.getLength(),
                loaderData.getHeight(),
                loaderData.getPattern()
        );
    }

    /**
     * Converte StructureSaver.StructureData para StructureLoader.StructureData
     */
    public static StructureLoader.StructureData convertToLoaderFormat(StructureSaver.StructureData saverData) {
        if (saverData == null) {
            return null;
        }

        StructureLoader.StructureData loaderData = new StructureLoader.StructureData();

        try {
            java.lang.reflect.Field[] fields = StructureLoader.StructureData.class.getDeclaredFields();

            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                switch (field.getName()) {
                    case "name" -> field.set(loaderData, saverData.getName());
                    case "author" -> field.set(loaderData, saverData.getAuthor());
                    case "description" -> field.set(loaderData, saverData.getDescription());
                    case "category" -> field.set(loaderData, saverData.getCategory());
                    case "guiPath" -> field.set(loaderData, saverData.getGuiPath());
                    case "iconPath" -> field.set(loaderData, saverData.getIconPath());
                    case "tier" -> field.set(loaderData, saverData.getTier());
                    case "subtier" -> field.set(loaderData, saverData.getSubtier());
                    case "energyConsumption" -> field.set(loaderData, saverData.getEnergyConsumption());
                    case "onlyItems" -> field.set(loaderData, saverData.isOnlyItems());
                    case "requiredItems" -> field.set(loaderData, saverData.getRequiredItems());
                    case "width" -> field.set(loaderData, saverData.getWidth());
                    case "length" -> field.set(loaderData, saverData.getLength());
                    case "height" -> field.set(loaderData, saverData.getHeight());
                    case "pattern" -> field.set(loaderData, saverData.getPattern());
                }
            }

            loaderData.preprocessBlockStates();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return loaderData;
    }
}