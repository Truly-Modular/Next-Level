package smartin.tmnextlevel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import smartin.miapi.Miapi;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.modules.ModuleInstance;
import smartin.miapi.modules.conditions.ConditionManager;
import smartin.miapi.modules.edit_options.EditOption;
import smartin.miapi.modules.properties.util.ComponentApplyProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record UpgradeBatch(List<UpgradeSelection> selections) {
    public static final Codec<UpgradeBatch> CODEC =
            UpgradeSelection.CODEC.listOf().xmap(UpgradeBatch::new, UpgradeBatch::selections);

    public static final StreamCodec<ByteBuf, UpgradeBatch> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    /**
     * Applies all upgrades in this batch to the given itemstack.
     *
     * @param baseStack  The item being upgraded
     * @param player     The player doing the upgrade
     * @param editOption editContext
     * @return A modified copy of the stack with upgrades applied
     */
    public ItemStack apply(ItemStack baseStack, Player player, EditOption.EditContext editOption) {
        ItemStack stack = baseStack.copy();
        ModuleInstance moduleInstance = ItemModule.getModules(stack).copy();
        moduleInstance.writeToItem(stack);
        moduleInstance.clearCaches();

        int currentXp = UpgradeEditOption.getItemXP(stack);
        int totalLevel = UpgradeEditOption.getTotalUpgradeLevel(stack);
        if (selections().isEmpty()) {
            return stack;
        }

        for (UpgradeSelection selection : selections) {
            Upgrade upgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(selection.upgradeId());
            if (upgrade == null) continue;

            ModuleInstance instance = selection.resolveModule(stack).orElse(null);
            if (instance == null) continue;

            // Condition check
            ConditionManager.ConditionContext ctx =
                    ConditionManager.playerContext(instance, player, instance.properties);
            if (!upgrade.condition().isAllowed(ctx)) continue;

            // Resolve current upgrade map
            Map<ResourceLocation, Integer> upgradeMap = new HashMap<>();
            if (instance.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
                Upgrade.MODULE_UPGRADE_ID_CODEC.decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.UPGRADE_ID))
                        .result()
                        .ifPresent(pair -> upgradeMap.putAll(pair.getFirst()));
            }

            int currentLevel = upgradeMap.getOrDefault(selection.upgradeId(), 0);
            if (currentLevel >= upgrade.max()) {
                Miapi.LOGGER.warn("Could not apply update - cannot exceed max level of upgrade!");
                return baseStack;
            }

            // Compute XP cost for this upgrade
            int xpCost = 0;
            for (int i = 0; i < upgrade.cost() * selection.levels(); i++) {
                xpCost += Upgrade.xpCost(totalLevel + i);
            }

            if (currentXp < xpCost) {
                // not enough XP → skip
                Miapi.LOGGER.warn("Could not apply update - player did not have enough xp! How did he trigger this action? " + player.getStringUUID());
                return baseStack;
            }

            // Apply upgrade
            currentXp -= xpCost;
            totalLevel += selection.levels() * upgrade.cost();
            upgradeMap.put(selection.upgradeId(), currentLevel + selection.levels());

            // Write upgrade map back
            instance.moduleData.put(
                    Upgrade.UPGRADE_ID,
                    Upgrade.MODULE_UPGRADE_ID_CODEC.encodeStart(JsonOps.INSTANCE, upgradeMap).getOrThrow()
            );
            instance.getRoot().clearCaches();
            instance.getRoot().writeToItem(stack);
        }

        moduleInstance.clearCaches();

        // Update XP on the stack
        UpgradeEditOption.setItemXP(stack, currentXp);
        // Refresh components
        ComponentApplyProperty.updateItemStack(stack, player.registryAccess());

        return stack;
    }
}
