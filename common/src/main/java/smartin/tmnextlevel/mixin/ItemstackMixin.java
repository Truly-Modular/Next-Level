package smartin.tmnextlevel.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smartin.tmnextlevel.upgrade.Upgrade;

import java.util.function.Consumer;

import static smartin.tmnextlevel.upgrade.UpgradeEditOption.*;

@Mixin(ItemStack.class)
public class ItemstackMixin {

    @Inject(
            method = "hurtAndBreak",
            at = @At("HEAD"),
            cancellable = true
    )
    public void tm_next_level$update_message(int damage, ServerLevel serverLevel, ServerPlayer serverPlayer, Consumer<Item> consumer, CallbackInfo ci) {
        ItemStack stack = (ItemStack)(Object) this;

        int oldXP = getItemXP(stack);
        int oldLevel = getTotalUpgradeLevel(stack);
        Upgrade.xpCost(oldLevel);

        // Simulate XP gain on use (you can change this logic)
        int xpGain = damage; // For example: 1 XP per use
        int newXP = oldXP + xpGain;

        int newLevel = getTotalUpgradeLevel(stack);

        if (newLevel > oldLevel) {
            serverPlayer.sendSystemMessage(Component.literal("Your item has leveled up! Level " + newLevel));
        }
    }
}
