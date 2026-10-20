package com.einsteinmods.relativity.client.config;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/**
 * Options column laid out like YACL's option list: full-width option rows with a small reset button
 * on their right. No list background: the screen's menu background shows through, as in YACL.
 *
 * <p>The hovered (or else focused) row is reported to the description panel while drawing.
 */
final class SettingsList extends ContainerObjectSelectionList<SettingsList.OptionRow> {
  /** Option height plus the gap below it, as YACL rows. */
  private static final int ROW_HEIGHT = OptionControl.HEIGHT + 2;

  /** Rows are shifted left of centre by this much, as YACL. */
  private static final int ROW_LEFT_SHIFT = 6;

  /** Room kept free on the right for the scroll bar. */
  private static final int SCROLLBAR_SPACE = 20;

  private static final int RESET_BUTTON_SIZE = 20;
  private static final Component RESET_SYMBOL = Component.literal("⟲");
  private static final Tooltip RESET_TOOLTIP =
      Tooltip.create(Component.translatable("controls.reset"));

  private final Consumer<DescribedOption> describer;

  SettingsList(Minecraft minecraft, Consumer<DescribedOption> describer) {
    super(minecraft, 0, 0, 0, ROW_HEIGHT);
    this.describer = describer;
    this.centerListVertically = false;
  }

  void addOption(OptionControl<?> control) {
    addEntry(new OptionRow(control, describer));
  }

  @Override
  public int getRowWidth() {
    return getWidth() - ROW_LEFT_SHIFT - SCROLLBAR_SPACE;
  }

  @Override
  public int getRowLeft() {
    return super.getRowLeft() - ROW_LEFT_SHIFT;
  }

  @Override
  protected void extractListBackground(GuiGraphicsExtractor graphics) {
    // No darker list background, as YACL: the description pane carries it instead.
  }

  @Override
  protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    // The tab bar above draws the header line; the list reaches the bottom of the screen.
  }

  /** Option box on the left, reset button on its right. */
  static final class OptionRow extends ContainerObjectSelectionList.Entry<OptionRow> {
    private final OptionControl<?> control;
    private final Button reset;
    private final Consumer<DescribedOption> describer;

    private OptionRow(OptionControl<?> control, Consumer<DescribedOption> describer) {
      this.control = control;
      this.describer = describer;
      this.reset =
          Button.builder(RESET_SYMBOL, button -> control.resetToDefault())
              .size(RESET_BUTTON_SIZE, RESET_BUTTON_SIZE)
              .tooltip(RESET_TOOLTIP)
              .build();
    }

    @Override
    public void extractContent(
        GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
      int x = getContentX();
      int y = getContentY();
      control.setRectangle(getContentWidth() - RESET_BUTTON_SIZE, OptionControl.HEIGHT, x, y);
      control.extractRenderState(graphics, mouseX, mouseY, partialTick);
      reset.active = control.active && !control.isDefault();
      reset.setPosition(control.getRight(), y);
      reset.extractRenderState(graphics, mouseX, mouseY, partialTick);
      if (hovered || control.isFocused()) {
        describer.accept(control);
      }
    }

    @Override
    public List<? extends GuiEventListener> children() {
      return List.of(control, reset);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
      return List.of(control, reset);
    }
  }
}
