package smartin.tmnextlevel;

import com.mojang.serialization.JsonOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import smartin.miapi.Miapi;
import smartin.miapi.client.gui.InteractAbleWidget;
import smartin.miapi.item.modular.VisualModularItem;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.modules.ModuleInstance;
import smartin.miapi.modules.conditions.ConditionManager;
import smartin.miapi.modules.edit_options.EditOption;
import smartin.miapi.modules.edit_options.EditOptionIcon;
import smartin.miapi.modules.properties.tag.ModuleTagProperty;
import smartin.miapi.network.Networking;
import smartin.tmnextlevel.selection.UpgradeBatch;
import smartin.tmnextlevel.ui.UpgradeEditView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UpgradeEditOption implements EditOption {

    public static int spendAbleLevel(int oldLevel, int currentXp) {
        int upgradePoints = 0;
        int upgradeCost = Upgrade.xpCost(oldLevel);
        while (currentXp >= upgradeCost && currentXp > 0) {
            currentXp -= upgradeCost;
            upgradePoints++;
            upgradeCost = Upgrade.xpCost(oldLevel + upgradePoints);
        }
        return upgradePoints;
    }

    @Override
    public ItemStack preview(FriendlyByteBuf buffer, EditContext editContext) {
        // Decode a full batch instead of a single selection
        UpgradeBatch batch = UpgradeBatch.STREAM_CODEC.decode(buffer);
        return batch.apply(editContext.getItemstack(), editContext.getPlayer(), editContext);
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
            if (instance.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
                var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                        .decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.UPGRADE_ID))
                        .result();
                if (decodeResult.isPresent()) {
                    upgradeMap = decodeResult.get().getFirst();
                    for (ResourceLocation existingId : upgradeMap.keySet()) {
                        Upgrade existingUpgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(existingId);
                        if (existingUpgrade != null) {
                            existingUpgrades.add(existingUpgrade);
                            return true;
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
                    && ModuleTagProperty.getTags(instance).contains(upgrade.moduleTag())) {
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
                (batch) -> {
                    // send batch to preview
                    FriendlyByteBuf buffer = Networking.createBuffer();
                    UpgradeBatch.STREAM_CODEC.encode(buffer, batch);
                    editContext.preview(buffer);
                },
                (batch) -> {
                    // send batch to craft/apply
                    FriendlyByteBuf buffer = Networking.createBuffer();
                    UpgradeBatch.STREAM_CODEC.encode(buffer, batch);
                    editContext.craft(buffer);
                });
    }

    @Override
    @Environment(EnvType.CLIENT)
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
            if (instance.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
                var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                        .decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.UPGRADE_ID))
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
