package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

/**
 * Classe centralizada para todas as constantes do mod
 * Mantém valores padrão que podem ser sobrescritos pela configuração
 */
public class ModConstants {
    
    /**
     * Cores pré-definidas em formato inteiro
     * Utiliza ColorUtils para manipulação consistente
     */
    public static class Colors {
        // Cores RGBA usando ColorUtils
        public static final int TRANSPARENT_BLACK = ColorUtils.rgbaToInt(0f, 0f, 0f, 0.5f);
        public static final int HIGHLIGHT_ORANGE = ColorUtils.rgbaToInt(1f, 0.65f, 0f, 1f);
        public static final int WHITE = ColorUtils.rgbaToInt(1f, 1f, 1f, 1f);
        public static final int GRAY = ColorUtils.rgbaToInt(0.5f, 0.5f, 0.5f, 1f);
        public static final int LIGHT_GRAY = ColorUtils.rgbaToInt(0.8f, 0.8f, 0.8f, 1f);
        
        // Cores derivadas
        public static final int DARK_GRAY = ColorUtils.darken(GRAY, 0.3f);
    }
    
    /**
     * Mensagens e keys de tradução padrão
     */
    public static class TranslationKeys {
        public static final String LOG_LOADING_STRUCTURES = "log.satisfactory_build_tool.loading_structures";
        public static final String LOG_SYNCING_STRUCTURES = "log.satisfactory_build_tool.syncing_structures";
    }
    
    /**
     * Valores padrão para diferentes modos
     */
    public static class Defaults {
        public static final String DEFAULT_DESCRIPTION = "";
    }
}