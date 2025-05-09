package smartin.tmnextlevel;

import com.mojang.serialization.JsonOps;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import io.netty.handler.codec.DecoderException;
import net.minecraft.resources.ResourceLocation;
import smartin.miapi.Miapi;
import smartin.miapi.datapack.ReloadHelpers;
import smartin.miapi.events.MiapiEvents;
import smartin.miapi.item.modular.PropertyResolver;
import smartin.miapi.item.modular.StatResolver;
import smartin.miapi.modules.PropertyHolder;
import smartin.miapi.modules.properties.util.ModuleProperty;
import smartin.miapi.registries.RegistryInventory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class Tmnextlevel {
    public static final String MOD_ID = "tmnextlevel";

    public static void init() {
        ReloadHelpers.registerReloadHandler(
                "miapi/upgrade",
                Upgrade.UPGRADE_MIAPI_REGISTRY,
                Upgrade.CODEC,
                0.0f
        );

        RegistryInventory.EDIT_OPTION_MIAPI_REGISTRY.register(Miapi.id("module_upgrades"), new UpgradeEditOption());

        StatResolver.registerResolver("module_upgrade", (data, instance) -> {
            if (instance.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
                ResourceLocation id = Miapi.id(data.replaceFirst("\\.", ":"));

                var levelMap = Upgrade.MODULE_UPGRADE_ID_CODEC.decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.UPGRADE_ID)).getOrThrow(
                        (s) -> new DecoderException("Could not decode UpgradeID " + s));
                return levelMap.getFirst().getOrDefault(id, 0);

            }
            return 0;
        });
        RegistryInventory.COMPONENT_TYPE_REGISTRAR.register(Miapi.id("module_upgrade_xp"), () -> Upgrade.COMPONENT);
        MiapiEvents.MODULAR_ITEM_DAMAGE.register(
                (damage, itemStack, level) ->
                        itemStack.update(Upgrade.COMPONENT, 0, (old) -> old + damage));


        PropertyResolver.register(Miapi.id(MOD_ID, "upgrade"), (moduleInstance, oldMap) -> {
            AtomicReference<Map<ModuleProperty<?>, Object>> map = new AtomicReference<>(new ConcurrentHashMap<>(oldMap));
            var json = moduleInstance.moduleData.get(Upgrade.UPGRADE_ID);

            if (json != null) {
                Upgrade.MODULE_UPGRADE_ID_CODEC.parse(JsonOps.INSTANCE, json).result().ifPresent(upgrades -> {
                    for (ResourceLocation upgradeId : upgrades.keySet()) {
                        int level = upgrades.get(upgradeId);
                        Upgrade upgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(upgradeId);
                        if (upgrade != null) {
                            PropertyHolder selectedHolder = null;
                            int bestLevel = Integer.MIN_VALUE;
                            for (Map.Entry<Integer, PropertyHolder> entry : upgrade.properties().entrySet()) {
                                int keyLevel = entry.getKey();
                                if (keyLevel <= level && keyLevel > bestLevel) {
                                    bestLevel = keyLevel;
                                    selectedHolder = entry.getValue();
                                }
                            }
                            if (selectedHolder != null) {
                                map.set(selectedHolder.applyHolder(map.get(), Optional.of(upgrade.name())));
                            }
                        }
                    }
                });
            }
            return map.get();
        }, List.of(Miapi.id("skin")));

        CommandRegistrationEvent.EVENT.register((serverCommandSourceCommandDispatcher, registryAccess, listener) -> {
            XpCommands.register(serverCommandSourceCommandDispatcher);
        });
    }
}
