package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

/**
 * Utilitário para manipulação de cores RGBA
 */
public class ColorUtils {
    
    /**
     * Converte valores RGBA float para inteiro
     * @param r Componente vermelho (0.0 - 1.0)
     * @param g Componente verde (0.0 - 1.0)
     * @param b Componente azul (0.0 - 1.0)
     * @param a Componente alpha (0.0 - 1.0)
     * @return Inteiro ARGB
     */
    public static int rgbaToInt(float r, float g, float b, float a) {
        int alpha = (int) (a * 255) & 0xFF;
        int red = (int) (r * 255) & 0xFF;
        int green = (int) (g * 255) & 0xFF;
        int blue = (int) (b * 255) & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
    
    /**
     * Converte valores RGB int para inteiro (alpha = 1.0)
     * @param r Componente vermelho (0 - 255)
     * @param g Componente verde (0 - 255)
     * @param b Componente azul (0 - 255)
     * @return Inteiro ARGB com alpha máximo
     */
    public static int rgbToInt(int r, int g, int b) {
        return rgbaToInt(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
    }
    
    /**
     * Extrai componente alpha de um inteiro ARGB
     * @param color Cor ARGB
     * @return Valor alpha (0 - 255)
     */
    public static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }
    
    /**
     * Extrai componente vermelho de um inteiro ARGB
     * @param color Cor ARGB
     * @return Valor vermelho (0 - 255)
     */
    public static int getRed(int color) {
        return (color >> 16) & 0xFF;
    }
    
    /**
     * Extrai componente verde de um inteiro ARGB
     * @param color Cor ARGB
     * @return Valor verde (0 - 255)
     */
    public static int getGreen(int color) {
        return (color >> 8) & 0xFF;
    }
    
    /**
     * Extrai componente azul de um inteiro ARGB
     * @param color Cor ARGB
     * @return Valor azul (0 - 255)
     */
    public static int getBlue(int color) {
        return color & 0xFF;
    }
    
    /**
     * Cores pré-definidas
     */
    public static class Presets {
        public static final int TRANSPARENT_BLACK = rgbaToInt(0f, 0f, 0f, 0.5f);
        public static final int HIGHLIGHT_ORANGE = rgbaToInt(1f, 0.65f, 0f, 1f);
        public static final int WHITE = rgbaToInt(1f, 1f, 1f, 1f);
        public static final int BLACK = rgbaToInt(0f, 0f, 0f, 1f);
        public static final int RED = rgbaToInt(1f, 0f, 0f, 1f);
        public static final int GREEN = rgbaToInt(0f, 1f, 0f, 1f);
        public static final int BLUE = rgbaToInt(0f, 0f, 1f, 1f);
        public static final int YELLOW = rgbaToInt(1f, 1f, 0f, 1f);
        public static final int CYAN = rgbaToInt(0f, 1f, 1f, 1f);
        public static final int MAGENTA = rgbaToInt(1f, 0f, 1f, 1f);
        public static final int GRAY = rgbaToInt(0.5f, 0.5f, 0.5f, 1f);
        public static final int LIGHT_GRAY = rgbaToInt(0.8f, 0.8f, 0.8f, 1f);
    }
    
    /**
     * Interpola entre duas cores
     * @param color1 Primeira cor
     * @param color2 Segunda cor
     * @param factor Fator de interpolação (0.0 - 1.0)
     * @return Cor interpolada
     */
    public static int interpolate(int color1, int color2, float factor) {
        int a1 = getAlpha(color1);
        int r1 = getRed(color1);
        int g1 = getGreen(color1);
        int b1 = getBlue(color1);
        
        int a2 = getAlpha(color2);
        int r2 = getRed(color2);
        int g2 = getGreen(color2);
        int b2 = getBlue(color2);
        
        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Escurece uma cor
     * @param color Cor original
     * @param factor Fator de escurecimento (0.0 - 1.0)
     * @return Cor escurecida
     */
    public static int darken(int color, float factor) {
        int r = (int) (getRed(color) * (1 - factor));
        int g = (int) (getGreen(color) * (1 - factor));
        int b = (int) (getBlue(color) * (1 - factor));
        return (getAlpha(color) << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Clareia uma cor
     * @param color Cor original
     * @param factor Fator de clareamento (0.0 - 1.0)
     * @return Cor clareada
     */
    public static int lighten(int color, float factor) {
        int r = Math.min(255, (int) (getRed(color) + (255 - getRed(color)) * factor));
        int g = Math.min(255, (int) (getGreen(color) + (255 - getGreen(color)) * factor));
        int b = Math.min(255, (int) (getBlue(color) + (255 - getBlue(color)) * factor));
        return (getAlpha(color) << 24) | (r << 16) | (g << 8) | b;
    }
}