package dev.life.client.core.module;

public enum Category {
    HUD("HUD"), VISUAL("Visual"), UTILITY("Utility"), HYPIXEL("Hypixel");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
