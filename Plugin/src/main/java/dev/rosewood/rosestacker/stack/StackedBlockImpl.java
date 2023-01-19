package dev.rosewood.rosestacker.stack;

import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.event.StackGUIOpenEvent;
import dev.rosewood.rosestacker.gui.StackedBlockGui;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.HologramManager;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.stack.settings.BlockStackSettings;
import dev.rosewood.rosestacker.stack.settings.BlockStackSettingsImpl;
import dev.rosewood.rosestacker.utils.StackerUtils;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class StackedBlockImpl extends AbstractStack<BlockStackSettings> implements StackedBlock {

    private int size;
    private final Block block;
    private StackedBlockGui stackedBlockGui;

    private final BlockStackSettingsImpl stackSettings;

    public StackedBlockImpl(int size, Block block) {
        this.size = size;
        this.block = block;
        this.stackedBlockGui = null;

        this.stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getBlockStackSettings(this.block);

        if (Bukkit.isPrimaryThread())
            this.updateDisplay();
    }

    public Block getBlock() {
        return this.block;
    }

    public boolean isLocked() {
        if (this.stackedBlockGui == null)
            return false;
        return this.stackedBlockGui.hasViewers();
    }

    public void kickOutGuiViewers() {
        if (this.stackedBlockGui != null)
            this.stackedBlockGui.kickOutViewers();
    }

    public void increaseStackSize(int amount) {
        this.size += amount;

        this.updateDisplay();
    }

    public void setStackSize(int size) {
        this.size = size;

        this.updateDisplay();
    }

    public void openGui(Player player) {
        StackGUIOpenEvent event = new StackGUIOpenEvent(player, this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        if (this.stackedBlockGui == null)
            this.stackedBlockGui = new StackedBlockGui(this);
        this.stackedBlockGui.openFor(player);
    }

    @Override
    public int getStackSize() {
        return this.size;
    }

    @Override
    public Location getLocation() {
        return this.block.getLocation();
    }

    @Override
    public void updateDisplay() {
        if (!Setting.BLOCK_DISPLAY_TAGS.getBoolean() || this.stackSettings == null)
            return;

        HologramManager hologramManager = RoseStacker.getInstance().getManager(HologramManager.class);

        Location location = this.getHologramLocation();

        if (this.size <= 1) {
            hologramManager.deleteHologram(location);
            return;
        }

        List<String> displayStrings = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessages("block-hologram-display", StringPlaceholders.builder("amount", StackerUtils.formatNumber(this.getStackSize()))
                .addPlaceholder("name", this.stackSettings.getDisplayName()).build());

        hologramManager.createOrUpdateHologram(location, displayStrings);
    }

    public Location getHologramLocation() {
        return this.block.getLocation().add(0.5, Setting.BLOCK_DISPLAY_TAGS_HEIGHT_OFFSET.getDouble(), 0.5);
    }

    @Override
    public BlockStackSettingsImpl getStackSettings() {
        return this.stackSettings;
    }

}
