package smartin.tmnextlevel.upgrade;

import com.mojang.serialization.JsonOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import smartin.miapi.Miapi;
import smartin.miapi.client.gui.InteractAbleWidget;
import smartin.miapi.client.gui.SimpleButton;
import smartin.miapi.client.gui.crafting.CraftingScreen;
import smartin.miapi.item.modular.VisualModularItem;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.modules.MiapiPermissions;
import smartin.miapi.modules.ModuleInstance;
import smartin.miapi.modules.conditions.ConditionManager;
import smartin.miapi.modules.edit_options.EditOption;
import smartin.miapi.modules.edit_options.EditOptionIcon;
import smartin.miapi.modules.properties.TagProperty;
import smartin.miapi.modules.properties.util.ComponentApplyProperty;
import smartin.miapi.network.Networking;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UpgradeEditOption implements EditOption {

    @Override
    public ItemStack preview(FriendlyByteBuf buffer, EditContext editContext) {
        UpgradeSelection selection = UpgradeSelection.STREAM_CODEC.decode(buffer);
        Upgrade upgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(selection.upgradeId());
        if (upgrade == null) return editContext.getItemstack();

        ItemStack itemStack = editContext.getItemstack().copy();
        ModuleInstance instance = selection.resolveModule(itemStack).orElse(null);
        if (instance == null) return itemStack;

        ConditionManager.ConditionContext conditionCtx = ConditionManager.playerContext(instance, editContext.getPlayer(), instance.properties);
        if (!upgrade.condition().isAllowed(conditionCtx)) return itemStack;

        instance.clearCaches();
        instance = instance.copy();

        Map<ResourceLocation, Integer> rawUpgradeMap = new HashMap<>();
        if (instance.moduleData.containsKey(Upgrade.upgradeId)) {
            Upgrade.MODULE_UPGRADE_ID_CODEC.decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.upgradeId))
                    .result().ifPresent(pair -> rawUpgradeMap.putAll(pair.getFirst()));
        }

        int currentLevel = rawUpgradeMap.getOrDefault(selection.upgradeId(), 0);
        if (currentLevel >= upgrade.max()) return itemStack;

        // === XP Cost Logic ===
        int xpCost = 0;
        for (int i = 0; i < upgrade.cost(); i++) {
            xpCost += Upgrade.xpCost(getTotalUpgradeLevel(itemStack) + i);
        }
        int currentXP = getItemXP(itemStack);
        if (currentXP < xpCost) {
            return itemStack; // Not enough XP to apply the upgrade
        }

        // Deduct XP (you’ll later persist this)
        setItemXP(itemStack, currentXP - xpCost);

        rawUpgradeMap.put(selection.upgradeId(), currentLevel + 1);

        instance.moduleData.put(
                Upgrade.upgradeId,
                Upgrade.MODULE_UPGRADE_ID_CODEC.encodeStart(JsonOps.INSTANCE, rawUpgradeMap).getOrThrow()
        );

        instance.getRoot().writeToItem(itemStack);
        instance.clearCaches();
        ComponentApplyProperty.updateItemStack(itemStack, editContext.getPlayer().registryAccess());

        return itemStack;
    }

    @Override
    public boolean isVisible(EditContext editContext) {
        return VisualModularItem.isVisualModularItem(editContext.getItemstack()) &&
               hasUpgrades(editContext.getItemstack(), editContext.getPlayer());
    }

    private boolean hasUpgrades(ItemStack itemStack, Player player) {
        List<ModuleInstance> modules = ItemModule.getModules(itemStack).allSubModules();

        for (ModuleInstance instance : modules) {
            ConditionManager.ConditionContext ctx = ConditionManager.playerContext(instance, player, instance.properties);

            Map<ResourceLocation, Integer> upgradeMap = Map.of();
            List<Upgrade> existingUpgrades = new ArrayList<>();
            if (instance.moduleData.containsKey(Upgrade.upgradeId)) {
                var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                        .decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.upgradeId))
                        .result();
                if (decodeResult.isPresent()) {
                    upgradeMap = decodeResult.get().getFirst();
                    for (ResourceLocation existingId : upgradeMap.keySet()) {
                        Upgrade existingUpgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(existingId);
                        if (existingUpgrade != null) {
                            existingUpgrades.add(existingUpgrade);
                        }
                    }
                }
            }

            for (Map.Entry<ResourceLocation, Upgrade> entry : Upgrade.UPGRADE_MIAPI_REGISTRY.getFlatMap().entrySet()) {
                ResourceLocation upgradeId = entry.getKey();
                Upgrade upgrade = entry.getValue();

                int currentLevel = upgradeMap.getOrDefault(upgradeId, 0);

                if (upgrade.condition().isAllowed(ctx)
                    && currentLevel < upgrade.max()
                    && upgrade.isAllowed(existingUpgrades)
                    && TagProperty.getTags(instance).contains(upgrade.moduleTag())) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public InteractAbleWidget getGui(int x, int y, int width, int height, EditContext editContext) {
        return new UpgradeEditView(x, y, width, height, editContext,
                (selection) -> {
                    FriendlyByteBuf buffer = Networking.createBuffer();
                    UpgradeSelection.STREAM_CODEC.encode(buffer, selection);
                    editContext.preview(buffer);
                },
                (selection) -> {
                    FriendlyByteBuf buffer = Networking.createBuffer();
                    UpgradeSelection.STREAM_CODEC.encode(buffer, selection);
                    editContext.craft(buffer);
                });
    }

    @Environment(EnvType.CLIENT)
    @Override
    public InteractAbleWidget getIconGui(int x, int y, int width, int height, Consumer<EditOption> select, Supplier<EditOption> getSelected) {
        return new EditOptionIcon(x, y, width, height, select, getSelected, Miapi.id("tmnextlevel", "textures/gui/background.png"), 0, 0, 64, 64, "miapi.ui.edit_option.hover.upgrade", this);
    }


    public static int getItemXP(ItemStack stack) {
        return stack.getOrDefault(Upgrade.COMPONENT, 0);
    }

    public static void setItemXP(ItemStack stack, int newXP) {
        stack.set(Upgrade.COMPONENT, Math.max(newXP, 0));
    }

    public static int getTotalUpgradeLevel(ItemStack stack) {
        int total = 0;
        for (ModuleInstance instance : ItemModule.getModules(stack).allSubModules()) {
            if (instance.moduleData.containsKey(Upgrade.upgradeId)) {
                var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                        .decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.upgradeId))
                        .result();
                if (decodeResult.isPresent()) {
                    Map<ResourceLocation, Integer> map = decodeResult.get().getFirst();
                    for (int level : map.values()) {
                        total += level;
                    }
                }
            }
        }
        return total;
    }
}
