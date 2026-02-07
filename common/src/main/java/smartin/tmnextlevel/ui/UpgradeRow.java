package smartin.tmnextlevel.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import smartin.miapi.client.gui.InteractAbleWidget;
import smartin.miapi.client.gui.ScrollingTextWidget;
import smartin.miapi.client.gui.SimpleButton;
import smartin.miapi.client.gui.crafting.CraftingScreen;
import smartin.miapi.modules.ModuleInstance;
import smartin.tmnextlevel.Upgrade;
import smartin.tmnextlevel.selection.UpgradeBatch;
import smartin.tmnextlevel.selection.UpgradeSelection;

@Environment(EnvType.CLIENT)
public class UpgradeRow extends InteractAbleWidget {
    public final Upgrade upgrade;
    private final ModuleInstance instance;
    private int pendingLevels = 0;
    private final int currentLevel;
    private final int maxLevel;

    private final SimpleButton<Void> plusButton;
    private final SimpleButton<Void> minusButton;

    private final ScrollingTextWidget nameCostWidget;
    private final ScrollingTextWidget levelWidget;

    private boolean blockedByOther = false;

    public int availablePoints = 0;

    public UpgradeRow(int x, int y, int width, Upgrade upgrade, ModuleInstance instance, int currentLevel) {
        super(x, y, width, 20, Component.empty());
        this.upgrade = upgrade;
        this.instance = instance;
        this.currentLevel = currentLevel;
        this.maxLevel = upgrade.max();

        // First row: upgrade name + cost
        this.nameCostWidget = new ScrollingTextWidget(x, y, width - 10, buildNameCost());
        addChild(nameCostWidget);

        // Second row: current level (and pending level if applicable)
        this.levelWidget = new ScrollingTextWidget(x, y + 10, width - 10, buildLevelLabel());
        addChild(levelWidget);

        // Minus button
        minusButton = new SimpleButton<>(x + width - 10, y + 10, 9, 9, Component.literal("-"), null, v -> {
            if (pendingLevels > 0) {
                pendingLevels--;
                refreshLabels();
            }
            checkButtons();
        });

        // Plus button
        plusButton = new SimpleButton<>(x + width - 10, y, 9, 9, Component.literal("+"), null, v -> {
            if (currentLevel + pendingLevels < maxLevel) {
                pendingLevels++;
                refreshLabels();
            }
            checkButtons();
        });
        checkButtons();

        addChild(plusButton);
        addChild(minusButton);
    }

    public void checkButtons() {
        minusButton.isEnabled = pendingLevels > 0;
        plusButton.isEnabled =
                currentLevel + pendingLevels < maxLevel &&
                availablePoints >= upgrade.cost();
        refreshLabels();
    }

    public void applyBatch(UpgradeBatch batch) {
        boolean taken = batch.selections().stream()
                .anyMatch(sel -> sel.upgradeId().equals(upgrade.getID()) && sel.levels() > 0);

        if (upgrade.unique() && taken && pendingLevels == 0) {
            // Someone else took this unique upgrade, disable this row
            plusButton.isEnabled = false;
            minusButton.isEnabled = false;
            blockedByOther = true;
        } else {
            checkButtons(); // fallback to normal enable/disable logic
            blockedByOther = false;
        }
    }


    @Override
    public void setX(int x) {
        super.setX(x);
        nameCostWidget.setX(x + 3);
        levelWidget.setX(x + 5);
        plusButton.setX(x + getWidth() - 9);
        minusButton.setX(x + getWidth() - 9);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        nameCostWidget.setY(y + 2);
        levelWidget.setY(y + 11);
        plusButton.setY(y + 1);
        minusButton.setY(y + 10);
    }

    @Override
    public void renderWidget(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        // Width available for the texture (leave space for + / - buttons)
        int textureWidth = getWidth() - 10;

        // X offset based on level (looks like a style choice you’re using)
        int offsetX = (-1) * 2;

        int hoverOffset = 0;
        if (this.isMouseOver(mouseX, mouseY)) {
            hoverOffset = 1;
        }

        if (this.isHoveredOrFocused()) {
            hoverOffset = 2;
        }

        // Y position for row background
        int x = this.getX() + offsetX;
        int y = this.getY();

        // Draw the background bar
        drawTextureWithEdge(
                drawContext,
                CraftingScreen.BACKGROUND_TEXTURE,
                x,
                y,
                405,                        // U
                18 * hoverOffset + 1,           // V
                106,                        // region width
                16,                         // region height
                textureWidth - offsetX,     // target width
                this.getHeight(),           // target height
                512,                        // texture atlas width
                512,                        // texture atlas height
                4                           // edge size
        );

        // Let children (labels + buttons) render on top
        super.renderWidget(drawContext, mouseX, mouseY, delta);
    }

    @Override
    public void renderHover(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        if (isMouseOver(mouseX, mouseY)) {
            if (blockedByOther) {
                drawContext.renderTooltip(Minecraft.getInstance().font, Component.translatable("tmnextlevel.ui.blocked.upgrade"), mouseX, mouseY);
            } else {
                drawContext.renderTooltip(Minecraft.getInstance().font, upgrade.description(), mouseX, mouseY);
            }
        }
    }

    public UpgradeSelection toSelection() {
        checkButtons();
        return new UpgradeSelection(instance, upgrade.getID(), pendingLevels);
    }

    private void refreshLabels() {
        nameCostWidget.setText(buildNameCost());
        levelWidget.setText(buildLevelLabel());
    }

    private Component buildNameCost() {
        return Component.translatable(
                "tmnextlevel.ui.row.name_cost",
                upgrade.name(),
                upgrade.cost()
        );
    }

    private Component buildLevelLabel() {
        if (currentLevel == 0 && pendingLevels == 0) {
            return Component.translatable("tmnextlevel.ui.row.level.none");
        } else if (pendingLevels > 0) {
            return Component.translatable(
                    "tmnextlevel.ui.row.level.pending",
                    currentLevel,
                    currentLevel + pendingLevels
            );
        } else {
            return Component.translatable(
                    "tmnextlevel.ui.row.level",
                    currentLevel
            );
        }
    }
}
