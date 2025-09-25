package smartin.tmnextlevel;

import com.mojang.serialization.JsonOps;
import com.redpxnda.nucleus.util.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import smartin.miapi.client.gui.InteractAbleWidget;
import smartin.miapi.client.gui.ScrollList;
import smartin.miapi.client.gui.ScrollingTextWidget;
import smartin.miapi.client.gui.SimpleButton;
import smartin.miapi.modules.ItemModule;
import smartin.miapi.modules.ModuleInstance;
import smartin.miapi.modules.conditions.ConditionManager;
import smartin.miapi.modules.edit_options.EditOption;
import smartin.miapi.modules.properties.tag.ModuleTagProperty;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * UpgradeEditView now builds UpgradeRow widgets instead of plain buttons.
 * It collects UpgradeSelections into a batch for preview/apply.
 */
@Environment(EnvType.CLIENT)
public class UpgradeEditView extends InteractAbleWidget {
    private final ScrollList scrollList;
    private final List<InteractAbleWidget> upgradeWidgets = new ArrayList<>();
    private final EditOption.EditContext context;
    private final java.util.function.Consumer<UpgradeBatch> onChange;
    private final java.util.function.Consumer<UpgradeBatch> onCraft;

    private UpgradeBatch lastBatch = null;

    private int availableXP = 0;
    private int usedLevels = 0;
    private int maxToNextLevel = 0;
    private int availablePoints = 0;
    private int currentLVLXp = 0;

    public UpgradeEditView(int x, int y, int width, int height,
                           EditOption.EditContext context,
                           java.util.function.Consumer<UpgradeBatch> onChange,
                           java.util.function.Consumer<UpgradeBatch> onCraft) {
        super(x, y, width, height, Component.empty());
        this.context = context;
        this.onChange = onChange;
        this.onCraft = onCraft;

        this.scrollList = new ScrollList(x, y, width, height - 24, upgradeWidgets);
        this.addChild(scrollList);

        // Fetch XP & points
        this.availableXP = getItemXP(context.getItemstack());
        this.usedLevels = UpgradeEditOption.getTotalUpgradeLevel(context.getItemstack());

        int xp = availableXP;
        int simulatedLevel = usedLevels;
        int upgradePoints = 0;
        int upgradeCost = Upgrade.xpCost(simulatedLevel);

        while (xp >= upgradeCost && xp > 0) {
            xp -= upgradeCost;
            simulatedLevel++;
            upgradePoints++;
            upgradeCost = Upgrade.xpCost(simulatedLevel);
        }

        final int points = upgradePoints;
        final int xpToNextPoint = Upgrade.xpCost(simulatedLevel) - xp;
        maxToNextLevel = xpToNextPoint;
        availablePoints = points;
        currentLVLXp = xp;

        // Points display
        this.addChild(new ScrollingTextWidget(x + 4, y + height - 13, width, Component.translatable("tmnextlevel.ui.points", points)));

        // XP progress bar
        int simulatedFinal = simulatedLevel;
        int costForNext = Upgrade.xpCost(simulatedFinal);
        int progress = (int) (((costForNext - xpToNextPoint) / (double) costForNext) * 100);
        ScrollingTextWidget secondTextWidget = new ScrollingTextWidget(
                x + width - 84, y + height - 13, 30,
                Component.translatable("tmnextlevel.ui.progress", progress)
        );
        secondTextWidget.setOrientation(ScrollingTextWidget.Orientation.CENTERED);
        secondTextWidget.hasTextShadow = false;
        secondTextWidget.textColor = Color.BLACK.abgr();
        this.addChild(new InteractAbleWidget(x + width - 84, y + height - 15, 30, 12, Component.literal("")) {
            @Override
            public void renderWidget(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
                int barWidth = (int) (progress / 100.0 * 30);
                drawContext.fill(getX(), getY(), getX() + 30, getY() + 10, 0xFF555555);
                drawContext.fill(getX(), getY(), getX() + barWidth, getY() + 10, 0xFF00CC00);
                drawContext.enableScissor(getX(), getY(), getX() + barWidth, getY() + 10);
                //secondTextWidget.renderWidget(drawContext, mouseX, mouseY, delta);
                drawContext.disableScissor();
            }
        });
        ScrollingTextWidget widget = new ScrollingTextWidget(
                x + width - 84, y + height - 13, 30,
                Component.translatable("tmnextlevel.ui.progress", progress)
        );
        widget.setOrientation(ScrollingTextWidget.Orientation.CENTERED);
        this.addChild(widget);


        populateUpgrades();

        // Apply button
        SimpleButton<Void> applyButton = new SimpleButton<>(x + width - 45, y + height - 18, 40, 16,
                Component.translatable("miapi.ui.apply"), null, callback -> {
            List<UpgradeSelection> selections = collectSelections();
            UpgradeBatch batch = new UpgradeBatch(selections);
            this.lastBatch = batch;
            this.onCraft.accept(batch);
        });
        collectSelections();
        this.addChild(applyButton);
    }

    /**
     * Collect UpgradeSelections from UpgradeRows into a batch.
     */
    public List<UpgradeSelection> collectSelections() {
        List<UpgradeSelection> result = new ArrayList<>();
        int totalCost = 0;
        for (InteractAbleWidget widget : upgradeWidgets) {
            if (widget instanceof UpgradeRow row) {
                UpgradeSelection sel = row.toSelection();
                totalCost += sel.levels() * row.upgrade.cost();
                if (sel.levels() > 0) {
                    result.add(sel);
                }
            }
        }

        UpgradeBatch batch = new UpgradeBatch(result);

        for (InteractAbleWidget widget : upgradeWidgets) {
            if (widget instanceof UpgradeRow row) {
                row.availablePoints = availablePoints - totalCost;
                row.applyBatch(batch);
            }
        }

        return result;
    }


    /**
     * Build UpgradeRows grouped by module.
     */
    private void populateUpgrades() {
        upgradeWidgets.clear();
        ItemStack itemStack = context.getItemstack();
        List<ModuleInstance> modules = ItemModule.getModules(itemStack).allSubModules();

        // 🔑 Collect all installed upgrades across all ModuleInstances first
        Set<ResourceLocation> globallyInstalled = new HashSet<>();
        Map<ResourceLocation, Integer> allUpgradeLevels = new HashMap<>();

        for (ModuleInstance instance : modules) {
            if (instance.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
                var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                        .decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.UPGRADE_ID))
                        .result();
                if (decodeResult.isPresent()) {
                    Map<ResourceLocation, Integer> upgradeMap = decodeResult.get().getFirst();
                    allUpgradeLevels.putAll(upgradeMap);
                    globallyInstalled.addAll(upgradeMap.keySet());
                }
            }
        }

        // 🔑 Now build rows per module, but skip globally unique upgrades
        for (ModuleInstance instance : modules) {
            ConditionManager.ConditionContext ctx =
                    ConditionManager.playerContext(instance, context.getPlayer(), instance.properties);

            Map<ResourceLocation, Integer> upgradeMap = Map.of();
            if (instance.moduleData.containsKey(Upgrade.UPGRADE_ID)) {
                var decodeResult = Upgrade.MODULE_UPGRADE_ID_CODEC
                        .decode(JsonOps.INSTANCE, instance.moduleData.get(Upgrade.UPGRADE_ID))
                        .result();
                if (decodeResult.isPresent()) {
                    upgradeMap = decodeResult.get().getFirst();
                }
            }

            List<Upgrade> existingUpgrades = new ArrayList<>();
            for (ResourceLocation existingId : upgradeMap.keySet()) {
                Upgrade existingUpgrade = Upgrade.UPGRADE_MIAPI_REGISTRY.get(existingId);
                if (existingUpgrade != null) {
                    existingUpgrades.add(existingUpgrade);
                }
            }

            List<InteractAbleWidget> rowsForModule = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Upgrade> entry : Upgrade.UPGRADE_MIAPI_REGISTRY.getFlatMap().entrySet()) {
                ResourceLocation upgradeId = entry.getKey();
                Upgrade upgrade = entry.getValue();
                int currentLevel = upgradeMap.getOrDefault(upgradeId, 0);

                // 🔑 Skip if globally unique & already installed anywhere
                if (upgrade.unique() && globallyInstalled.contains(upgradeId) && currentLevel == 0) {
                    continue;
                }

                if (upgrade.condition().isAllowed(ctx)
                    && currentLevel < upgrade.max()
                    && upgrade.isAllowed(existingUpgrades)
                    && ModuleTagProperty.getTags(instance).contains(upgrade.moduleTag())) {

                    UpgradeRow row = new UpgradeRow(
                            getX(), 0, getWidth() - 16,
                            upgrade, instance, currentLevel
                    );
                    rowsForModule.add(row);
                }
            }

            if (!rowsForModule.isEmpty()) {
                InteractAbleWidget moduleLabel = new InteractAbleWidget(getX() + 2, 0, getWidth() - 4, 16,
                        Component.literal(instance.getModuleName().getString()).withStyle(style -> style.withBold(true))) {
                    @Override
                    public void renderWidget(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
                        drawContext.drawString(
                                Minecraft.getInstance().font,
                                getMessage(),
                                getX() + 2,
                                getY() + 4,
                                0xFFFFFF,
                                false
                        );
                    }
                };
                upgradeWidgets.add(moduleLabel);
                upgradeWidgets.addAll(rowsForModule);
            }
        }

        scrollList.setList(upgradeWidgets);
    }



    // Dummy method for XP until implemented properly
    private int getItemXP(ItemStack stack) {
        return stack.getOrDefault(Upgrade.COMPONENT, 0);
    }

    @Override
    public void renderWidget(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        super.renderWidget(drawContext, mouseX, mouseY, delta);

        // Fire preview every frame if something changed
        List<UpgradeSelection> selections = collectSelections();
        UpgradeBatch batch = new UpgradeBatch(selections);
        if (!batch.equals(lastBatch)) {
            lastBatch = batch;
            onChange.accept(batch);
        }
    }

    @Override
    public void renderHover(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        super.renderHover(drawContext, mouseX, mouseY, delta);
        if (isMouseOver(mouseX, mouseY) && !scrollList.isMouseOver(mouseX, mouseY)) {
            drawContext.renderTooltip(
                    Minecraft.getInstance().font,
                    List.of(
                            Component.translatable("tmnextlevel.ui.hover.xp", currentLVLXp, currentLVLXp + maxToNextLevel),
                            Component.translatable("tmnextlevel.ui.hover.points", availablePoints),
                            Component.translatable("tmnextlevel.ui.hover.used", usedLevels)
                    ),
                    Optional.empty(),
                    mouseX,
                    mouseY
            );
        }
    }

}
