package smartin.tmnextlevel;


import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import smartin.miapi.Miapi;
import smartin.miapi.modules.PropertyHolder;
import smartin.miapi.modules.conditions.ConditionManager;
import smartin.miapi.modules.conditions.ModuleCondition;
import smartin.miapi.registries.MiapiRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record Upgrade(
        String moduleTag,
        ModuleCondition condition,
        Map<Integer, PropertyHolder> properties,
        List<ResourceLocation> incompatible,
        int max,
        int cost
) {
    public static final MiapiRegistry<Upgrade> UPGRADE_MIAPI_REGISTRY = MiapiRegistry.getInstance(Upgrade.class);
    public static final ResourceLocation UPGRADE_ID = Miapi.id("upgrade");

    public static final Codec<Integer> INT_CODEC = Codec.withAlternative(Codec.STRING.xmap(Integer::valueOf, i -> "" + i), Codec.INT);

    public static final Codec<Upgrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("tag").forGetter(Upgrade::moduleTag),
            ConditionManager.CONDITION_CODEC_DIRECT.fieldOf("condition").forGetter(Upgrade::condition),
            Codec.unboundedMap(INT_CODEC, PropertyHolder.MAP_CODEC.codec()).fieldOf("properties").forGetter(Upgrade::properties),
            ResourceLocation.CODEC.listOf().optionalFieldOf("incompatible", List.of()).forGetter(Upgrade::incompatible),
            INT_CODEC.optionalFieldOf("max", 0).forGetter(Upgrade::max),
            INT_CODEC.optionalFieldOf("cost", 1).forGetter(Upgrade::cost)
    ).apply(instance, Upgrade::new));
    public static final Codec<Map<ResourceLocation, Integer>> MODULE_UPGRADE_ID_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, INT_CODEC);

    public static DataComponentType<Integer> COMPONENT = DataComponentType.<Integer>builder()
            .persistent(ExtraCodecs.NON_NEGATIVE_INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT).build();

    // Dummy XP cost logic (double per level)
    public static int xpCost(int oldUpgrades) {
        return (int) Math.pow(2, oldUpgrades) * 10 + 100;
    }

    public Component name() {
        ResourceLocation id = getID();
        return Component.translatable("miapi.upgrade." + id.toString().replace(":", "."));
    }

    public Component description() {
        ResourceLocation id = getID();
        return Component.translatable("miapi.upgrade." + id.toString().replace(":", ".") + ".description");
    }

    public ResourceLocation getID() {
        return UPGRADE_MIAPI_REGISTRY.findKey(this);
    }

    public boolean isAllowed(Collection<Upgrade> upgrades) {
        return upgrades.stream().filter(upgrade -> incompatible().contains(upgrade.getID())).findAny().isEmpty();
    }

}

