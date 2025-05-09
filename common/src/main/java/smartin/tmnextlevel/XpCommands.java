package smartin.tmnextlevel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import smartin.miapi.item.modular.ModularItem;

public class XpCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("miapi")
                .then(Commands.literal("tmnextlevel")
                        .then(Commands.argument("xp", StringArgumentType.word())
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0)))
                                        .executes(XpCommands::executeSet))));
        dispatcher.register(literal);
    }

    private static int executeSet(CommandContext<CommandSourceStack> context) {
        Integer level = IntegerArgumentType.getInteger(context, "level");
        if (context.getSource().isPlayer()) {
            ItemStack itemStack = context.getSource().getPlayer().getItemInHand(InteractionHand.MAIN_HAND);
            if (ModularItem.isModularItem(itemStack)) {
                itemStack.update(Upgrade.COMPONENT, 0, (integer -> level));
                context.getSource().sendSuccess(() -> Component.literal("Set Xp Level to " + level), false);
                return 1;
            }
            context.getSource().sendFailure(Component.literal("HeldItem is not Modular"));
            return 0;
        }
        context.getSource().sendFailure(Component.literal("can only be called by a Player"));
        return 0; // Return success
    }
}
