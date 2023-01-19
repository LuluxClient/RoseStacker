package dev.rosewood.rosestacker.stack;

import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.stack.settings.ItemStackSettings;
import dev.rosewood.rosestacker.stack.settings.ItemStackSettingsImpl;
import dev.rosewood.rosestacker.utils.StackerUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class StackedItemImpl extends AbstractStack<ItemStackSettings> implements StackedItem, Comparable<StackedItem> {

    private int size;
    private Item item;

    private final ItemStackSettingsImpl stackSettings;

    public StackedItemImpl(int size, Item item) {
        this.size = size;
        this.item = item;

        this.stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getItemStackSettings(this.item);

        if (Bukkit.isPrimaryThread())
            this.updateDisplay();
    }

    public Item getItem() {
        return this.item;
    }

    public void updateItem() {
        Item item = (Item) Bukkit.getEntity(this.item.getUniqueId());
        if (item == null || item == this.item)
            return;

        this.item = item;
        this.updateDisplay();
    }

    public void increaseStackSize(int amount, boolean updateDisplay) {
        this.size += amount;
        if (updateDisplay)
            this.updateDisplay();
    }

    @Override
    public void setStackSize(int size) {
        this.size = size;
        this.updateDisplay();
    }

    @Override
    public int getStackSize() {
        return this.size;
    }

    @Override
    public Location getLocation() {
        return this.item.getLocation();
    }

    @Override
    public void updateDisplay() {
        ItemStack itemStack = this.item.getItemStack();
        itemStack.setAmount(Math.min(this.size, itemStack.getMaxStackSize()));

        if (itemStack.getType() == Material.AIR)
            return;

        this.item.setItemStack(itemStack);

        if (!Setting.ITEM_DISPLAY_TAGS.getBoolean() || this.stackSettings == null || !this.stackSettings.isStackingEnabled()) {
            this.item.setCustomNameVisible(false);
            return;
        }

        String displayName;
        ItemMeta itemMeta = itemStack.getItemMeta();

        boolean hasCustomName = itemMeta != null && itemMeta.hasDisplayName();
        if (hasCustomName && Setting.ITEM_DISPLAY_CUSTOM_NAMES.getBoolean()) {
            if (Setting.ITEM_DISPLAY_CUSTOM_NAMES_COLOR.getBoolean()) {
                displayName = itemMeta.getDisplayName();
            } else {
                displayName = ChatColor.stripColor(itemMeta.getDisplayName());
            }
        } else {
            displayName = this.stackSettings.getDisplayName();
        }

        String displayString;
        if (this.getStackSize() > 1) {
            displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("item-stack-display", StringPlaceholders.builder("amount", StackerUtils.formatNumber(this.getStackSize()))
                    .addPlaceholder("name", displayName).build());
        } else {
            displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("item-stack-display-single", StringPlaceholders.single("name", displayName));
        }

        this.item.setCustomNameVisible((this.size > 1 || Setting.ITEM_DISPLAY_TAGS_SINGLE.getBoolean() || (Setting.ITEM_DISPLAY_CUSTOM_NAMES_ALWAYS.getBoolean() && hasCustomName)) &&
                (this.size > itemStack.getMaxStackSize() || !Setting.ITEM_DISPLAY_TAGS_ABOVE_VANILLA_STACK_SIZE.getBoolean()));
        this.item.setCustomName(displayString);
    }

    @Override
    public ItemStackSettingsImpl getStackSettings() {
        return this.stackSettings;
    }

    /**
     * Gets the StackedItem that two stacks should stack into
     *
     * @param stack2 the second StackedItem
     * @return a positive int if this stack should be preferred, or a negative int if the other should be preferred
     */
    @Override
    public int compareTo(StackedItem stack2) {
        Entity entity1 = this.getItem();
        Entity entity2 = stack2.getItem();

        if (this == stack2)
            return 0;

        if (Setting.ITEM_MERGE_INTO_NEWEST.getBoolean())
            return entity1.getTicksLived() < entity2.getTicksLived() ? 1 : -1;

        if (this.getStackSize() == stack2.getStackSize())
            return entity1.getTicksLived() > entity2.getTicksLived() ? 2 : -2;

        return this.getStackSize() > stack2.getStackSize() ? 1 : -1;
    }

}
