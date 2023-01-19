package dev.rosewood.rosestacker.stack.settings;

import dev.rosewood.rosegarden.config.CommentedConfigurationSection;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;

public abstract class BaseStackSettings implements StackSettings {

    protected CommentedConfigurationSection settingsConfiguration;
    private boolean hasChanges;

    public BaseStackSettings(CommentedFileConfiguration settingsConfiguration) {
        this.settingsConfiguration = settingsConfiguration;
    }

    protected abstract String getConfigurationSectionKey();

    protected void setDefaults() {
        CommentedConfigurationSection settingsConfiguration = this.settingsConfiguration;
        this.settingsConfiguration = this.settingsConfiguration.getConfigurationSection(this.getConfigurationSectionKey());
        if (this.settingsConfiguration == null)
            this.settingsConfiguration = settingsConfiguration.createSection(this.getConfigurationSectionKey());
    }

    protected void setIfNotExists(String setting, Object value) {
        if (this.settingsConfiguration.get(setting) == null) {
            this.settingsConfiguration.set(setting, value);
            this.hasChanges = true;
        }
    }

    public boolean hasChanges() {
        return this.hasChanges;
    }

}
