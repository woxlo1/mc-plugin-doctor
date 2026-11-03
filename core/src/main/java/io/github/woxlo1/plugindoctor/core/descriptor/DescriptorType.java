package io.github.woxlo1.plugindoctor.core.descriptor;

public enum DescriptorType {
    BUKKIT("plugin.yml"),
    PAPER("paper-plugin.yml"),
    BUNGEE("bungee.yml"),
    VELOCITY("velocity-plugin.json");

    private final String path;

    DescriptorType(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
