package com.akiratochiro.life_and_money_api.category;

/**
 * A paleta fechada das categorias. A API guarda o nome, e o front decide o tom exato de cada
 * um: assim a identidade visual pode mudar sem migrar dados, e nenhuma categoria recebe uma cor
 * que o app não sabe desenhar.
 */
public enum CategoryColor {
    ROSE, PINK, PEACH, CORAL, AMBER, LIME, MINT, TEAL, SKY, LAVENDER, PLUM, SAND
}
