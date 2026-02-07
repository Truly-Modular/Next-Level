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
import smartin.miapi.item.modular.ModularItem;
import smartin.tmnextlevel.UpgradeEditOption;

import java.util.function.Consumer;

import static smartin.tmnextlevel.UpgradeEditOption.*;

@Mixin(ItemStack.class)
public abstract class ItemstackMixin {

    @Inject(
            method = "hurtAndBreak",
            at = @At("HEAD"),
            cancellable = true
    )
    public void tm_next_level$update_message(int damage, ServerLevel serverLevel, ServerPlayer serverPlayer, Consumer<Item> consumer, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (ModularItem.isModularItem(stack) && serverPlayer != null) {
            int usedLevels = UpgradeEditOption.getTotalUpgradeLevel(stack);
            int oldXP = getItemXP(stack);
            int newXp = damage + oldXP;

            if (spendAbleLevel(usedLevels, oldXP) < spendAbleLevel(usedLevels, newXp)) {
                serverPlayer.sendSystemMessage(Component.translatable("miapi.tm_next_level.upgrade_chat_message", stack.getDisplayName(), spendAbleLevel(usedLevels, newXp)));
            }
        }
    }
}
