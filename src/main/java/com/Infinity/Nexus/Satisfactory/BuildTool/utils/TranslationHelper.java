package com.Infinity.Nexus.Satisfactory.BuildTool.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.ChatFormatting;

/**
 * Utilitário para gerenciamento de traduções com fallback seguro
 */
public class TranslationHelper {
    
    /**
     * Traduz uma chave com fallback para texto literal
     * @param key Chave de tradução
     * @param fallback Texto alternativo caso a tradução não exista
     * @param args Argumentos para formatação
     * @return Component traduzido ou fallback
     */
    public static Component translateWithFallback(String key, String fallback, Object... args) {
        if (Language.getInstance().has(key)) {
            return Component.translatable(key, args);
        }
        return Component.literal(String.format(fallback, args));
    }
    
    /**
     * Traduz uma chave com fallback vazio
     * @param key Chave de tradução
     * @param args Argumentos para formatação
     * @return Component traduzido ou vazio
     */
    public static Component translateOrEmpty(String key, Object... args) {
        return translateWithFallback(key, "", args);
    }
    
    /**
     * Verifica se uma chave de tradução existe
     * @param key Chave para verificar
     * @return true se a tradução existir
     */
    public static boolean hasTranslation(String key) {
        return Language.getInstance().has(key);
    }
    
    /**
     * Formata uma mensagem de erro padrão
     * @param errorKey Chave do erro
     * @param args Argumentos adicionais
     * @return Component formatado como erro
     */
    public static Component formatError(String errorKey, Object... args) {
        return translateWithFallback(errorKey, "Error: %s", args)
                .copy().withStyle(ChatFormatting.RED);
    }
    
    /**
     * Formata uma mensagem de sucesso
     * @param successKey Chave de sucesso
     * @param args Argumentos adicionais
     * @return Component formatado como sucesso
     */
    public static Component formatSuccess(String successKey, Object... args) {
        return translateWithFallback(successKey, "Success: %s", args)
                .copy().withStyle(ChatFormatting.GREEN);
    }
    
    /**
     * Formata uma mensagem de aviso
     * @param warningKey Chave de aviso
     * @param args Argumentos adicionais
     * @return Component formatado como aviso
     */
    public static Component formatWarning(String warningKey, Object... args) {
        return translateWithFallback(warningKey, "Warning: %s", args)
                .copy().withStyle(ChatFormatting.YELLOW);
    }
}