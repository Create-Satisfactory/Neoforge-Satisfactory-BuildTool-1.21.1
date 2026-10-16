package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

public class StructureConverter {

    public static StructureSaver.StructureData convertToSaverFormat(StructureLoader.StructureData loaderData) {
        if (loaderData == null) return null;

        return new StructureSaver.StructureData(
                loaderData.getName(),
                loaderData.getAuthor(),
                loaderData.getDescription(),
                loaderData.getCategory(),
                loaderData.getGuiPath(),
                loaderData.getIconPath(),
                loaderData.getSchematics(),
                loaderData.getTier(),
                loaderData.getSubtier(),
                loaderData.getEnergyConsumption(),
                loaderData.isOnlyItems(),
                loaderData.getRequiredItems(),
                loaderData.getWidth(),
                loaderData.getLength(),
                loaderData.getHeight(),
                loaderData.getPattern(),
                loaderData.getMaterialPatterns()
        );
    }

    public static StructureLoader.StructureData convertToLoaderFormat(StructureSaver.StructureData saverData) {
        if (saverData == null) return null;

        StructureLoader.StructureData loaderData = new StructureLoader.StructureData(
                saverData.getName(),
                saverData.getAuthor(),
                saverData.getDescription(),
                saverData.getCategory(),
                saverData.getGuiPath(),
                saverData.getIconPath(),
                saverData.getSchematics(),
                saverData.getTier(),
                saverData.getSubtier(),
                saverData.getEnergyConsumption(),
                saverData.isOnlyItems(),
                saverData.getRequiredItems(),
                saverData.getPattern()
        );
        loaderData.setMaterialPatterns(saverData.getMaterialPatterns());
        loaderData.preprocessBlockStates();
        return loaderData;
    }
}