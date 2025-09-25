package smartin.tmnextlevel;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import smartin.miapi.blocks.ModularWorkBenchEntity;
import smartin.miapi.craft.CraftAction;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.modules.ModuleInstance;
import smartin.miapi.modules.conditions.ConditionManager;
import smartin.miapi.modules.properties.tag.ModuleTagProperty;
import smartin.miapi.modules.properties.util.CraftingProperty;
import smartin.miapi.modules.properties.util.MergeType;
import smartin.miapi.modules.properties.util.ModuleProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpgradeProperty implements CraftingProperty, ModuleProperty<Void> {


    @Override
    public boolean shouldExecuteOnCraft(@Nullable ModuleInstance module, ModuleInstance root, ItemStack stack, CraftAction craftAction) {
        return true;
    }
    @Override
    public ItemStack preview(ItemStack old, ItemStack crafting, Player player, @Nullable ModularWorkBenchEntity bench, CraftAction craftAction, ItemModule module, List<ItemStack> inventory, Map<ResourceLocation, JsonElement> data) {
        ModuleInstance oldModule = craftAction.getModifyingModuleInstance(old);
        ModuleInstance nextModule = craftAction.getModifyingModuleInstance(crafting);

        // decode upgrades from oldModule
        Map<ResourceLocation, Integer> oldUpgrades = Map.of();
        if (oldModule.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
            var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                    .decode(JsonOps.INSTANCE, oldModule.moduleData.get(Upgrade.UPGRADE_ID))
                    .result();
            if (decodeResult.isPresent()) {
                oldUpgrades = decodeResult.get().getFirst();
            }
        }

// decode upgrades from nextModule
        Map<ResourceLocation, Integer> nextUpgrades = new HashMap<>();
        if (nextModule.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
            var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                    .decode(JsonOps.INSTANCE, nextModule.moduleData.get(Upgrade.UPGRADE_ID))
                    .result();
            if (decodeResult.isPresent()) {
                nextUpgrades = new HashMap<>(decodeResult.get().getFirst());
            }
        }

        ConditionManager.ConditionContext ctx =
                ConditionManager.playerContext(nextModule, player, nextModule.properties);

        List<Upgrade> existingUpgrades = new ArrayList<>();
        for (ResourceLocation existingId : nextUpgrades.keySet()) {
            Upgrade existing = Upgrade.UPGRADE_MIAPI_REGISTRY.get(existingId);
            if (existing != null) {
                existingUpgrades.add(existing);
            }
        }

// Try to apply each upgrade from oldModule
        for (Map.Entry<ResourceLocation, Integer> entry : oldUpgrades.entrySet()) {
            ResourceLocation upgradeId = entry.getKey();
            int level = entry.getValue();
            Upgrade upgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(upgradeId);

            if (upgrade == null) continue;

            int currentLevel = nextUpgrades.getOrDefault(upgradeId, 0);

            // Check if we can apply it
            if (upgrade.condition().isAllowed(ctx)
                && currentLevel < upgrade.max()
                && upgrade.isAllowed(existingUpgrades)
                && ModuleTagProperty.getTags(nextModule).contains(upgrade.moduleTag())) {

                // clamp to max
                int newLevel = Math.max(currentLevel, level);
                nextUpgrades.put(upgradeId, newLevel);

                if (!existingUpgrades.contains(upgrade)) {
                    existingUpgrades.add(upgrade);
                }
            }
        }

// save back into nextModule
        nextModule.moduleData.put(Upgrade.UPGRADE_ID,
                Upgrade.MODULE_UPGRADE_ID_CODEC.encodeStart(JsonOps.INSTANCE, nextUpgrades).getOrThrow());


        return crafting;
    }

    @Override
    public Void decode(JsonElement jsonElement) {
        return null;
    }

    @Override
    public JsonElement encode(Void unused) {
        return null;
    }

    @Override
    public Void merge(Void unused, Void t1, MergeType mergeType) {
        return null;
    }
}
