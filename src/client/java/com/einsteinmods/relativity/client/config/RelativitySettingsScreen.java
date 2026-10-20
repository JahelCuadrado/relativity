package com.einsteinmods.relativity.client.config;

import com.einsteinmods.relativity.Relativity;
import com.einsteinmods.relativity.config.RelativitySettings;
import com.einsteinmods.relativity.core.OptimizationToggle;
import com.einsteinmods.relativity.core.OptimizationToggles;
import com.einsteinmods.relativity.core.ToggleStates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Mod settings built from vanilla widgets, laid out like a YACL screen: a tab bar with the
 * category, one tick box per {@link OptimizationToggles#ALL} entry on the left two thirds, and on
 * the right a description of the hovered option above the Cancel/Reset, Undo and Save/Done buttons.
 *
 * <p>As in YACL, changes stay pending until "Save Changes", which keeps the screen open; with
 * nothing pending that button reads "Done" and closes. "Cancel" (or Escape) discards pending
 * changes and closes; with nothing pending it reads "Reset" and sets every option to its default
 * (still pending, so Undo brings the previous values back).
 *
 * <p>A note asks for a restart while a {@code requiresRestart} option differs from the value the
 * game started with.
 */
public final class RelativitySettingsScreen extends Screen {
  private static final String KEY_PREFIX = "config." + Relativity.MOD_ID + ".";
  private static final String TOOLTIP_SUFFIX = ".tooltip";

  private static final Component SAVE = Component.translatable(KEY_PREFIX + "save");
  private static final Tooltip SAVE_TOOLTIP = tooltip("save");
  private static final Tooltip DONE_TOOLTIP = tooltip("done");
  private static final Tooltip CANCEL_TOOLTIP = tooltip("cancel");
  private static final Component RESET_ALL = Component.translatable("controls.reset");
  private static final Tooltip RESET_ALL_TOOLTIP = tooltip("reset");
  private static final Component UNDO = Component.translatable(KEY_PREFIX + "undo");
  private static final Tooltip UNDO_TOOLTIP = tooltip("undo");
  private static final Component EMPTY = Component.translatable(KEY_PREFIX + "empty");
  private static final Component RESTART_REQUIRED =
      Component.translatable(KEY_PREFIX + "restart_required").withStyle(ChatFormatting.YELLOW);

  /** Darker texture behind the description pane, as YACL. */
  private static final Identifier MENU_LIST_BACKGROUND =
      Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");

  /** Background and separator textures tile in squares of this size. */
  private static final int TEXTURE_SIZE = 32;

  private static final int TAB_HEADER_TEXTURE_SIZE = 16;

  private static final int SEPARATOR_THICKNESS = 2;
  private static final float VERTICAL_RADIANS = (float) Math.toRadians(90.0);

  /** The options list takes two of three columns; the right pane the third. */
  private static final int COLUMNS = 3;

  private static final int LIST_COLUMNS = 2;

  /** Pane padding is this fraction of the column width, as YACL. */
  private static final int PADDING_DIVISOR = 20;

  private static final int MAX_COLUMN_WIDTH = 400;
  private static final int BUTTON_HEIGHT = 20;
  private static final int BUTTON_ROW_GAP = 2;
  private static final int BUTTON_GAP = 4;

  private final @Nullable Screen parent;

  /** One tick box per optimization, in display order. */
  private final Map<OptimizationToggle, TickBoxControl> controls = new LinkedHashMap<>();

  private final SettingsList list;
  private final OptionDescriptionPanel description;
  private final Button cancelOrReset;
  private final Button undo;
  private final Button saveOrDone;
  private final TabManager tabManager =
      new TabManager(this::addRenderableWidget, this::removeWidget);
  private final GeneralTab generalTab = new GeneralTab();
  private @Nullable MenuTabBar tabBar;
  private @Nullable ScreenRectangle descriptionArea;
  private @Nullable ScreenRectangle rightPane;

  /** Restart note wrapped to the pane, and where it starts; set on layout. */
  private List<FormattedCharSequence> restartLines = List.of();

  private int restartX;
  private int restartY;

  /** Empty-state line position, centred on the list; set on layout. */
  private int emptyX;

  private int emptyY;
  private boolean restartRequired;

  public RelativitySettingsScreen(@Nullable Screen parent) {
    super(Component.translatable(KEY_PREFIX + "title"));
    this.parent = parent;
    ToggleStates saved = RelativitySettings.current();
    description = new OptionDescriptionPanel(font);
    list = new SettingsList(minecraft, description::show);
    for (OptimizationToggle toggle : OptimizationToggles.ALL) {
      TickBoxControl control =
          new TickBoxControl(
              font,
              Component.translatable(toggle.translationKey()),
              Component.translatable(toggle.descriptionKey()),
              saved.isEnabled(toggle),
              toggle.enabledByDefault(),
              value -> updateButtons());
      controls.put(toggle, control);
      list.addOption(control);
    }

    cancelOrReset = Button.builder(CommonComponents.GUI_CANCEL, button -> cancelOrReset()).build();
    undo = Button.builder(UNDO, button -> undoAll()).tooltip(UNDO_TOOLTIP).build();
    saveOrDone = Button.builder(CommonComponents.GUI_DONE, button -> saveOrDone()).build();
    updateButtons();
  }

  @Override
  protected void init() {
    MenuTabBar bar = MenuTabBar.builder(tabManager, width).addTabs(generalTab).build();
    tabBar = bar;
    addRenderableWidget(bar);
    bar.selectTab(0, false);
    repositionElements();
  }

  @Override
  protected void repositionElements() {
    if (tabBar == null) {
      return;
    }
    tabBar.arrangeElements(width);
    int top = tabBar.getRectangle().bottom();
    tabManager.setTabArea(new ScreenRectangle(0, top, width, height - top));
  }

  @Override
  public void onClose() {
    minecraft.gui.setScreen(parent);
  }

  /** Tab header texture above, menu background below, as vanilla tabbed screens. */
  @Override
  protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
    int headerHeight = tabBar == null ? 0 : tabBar.getRectangle().bottom();
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        CreateWorldScreen.TAB_HEADER_BACKGROUND,
        0,
        0,
        0.0F,
        0.0F,
        width,
        headerHeight,
        TAB_HEADER_TEXTURE_SIZE,
        TAB_HEADER_TEXTURE_SIZE);
    extractMenuBackground(graphics, 0, headerHeight, width, height - headerHeight);
  }

  /** Darker description pane with a separator along its left edge, as YACL. */
  @Override
  public void extractBackground(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractBackground(graphics, mouseX, mouseY, partialTick);
    if (rightPane == null) {
      return;
    }
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        minecraft.level == null
            ? MENU_LIST_BACKGROUND
            : AbstractSelectionList.INWORLD_MENU_LIST_BACKGROUND,
        rightPane.left(),
        rightPane.top(),
        rightPane.right(),
        rightPane.bottom(),
        rightPane.width(),
        rightPane.height(),
        TEXTURE_SIZE,
        TEXTURE_SIZE);
    graphics.pose().pushMatrix();
    graphics.pose().translate(rightPane.left(), rightPane.top());
    graphics.pose().rotate(VERTICAL_RADIANS);
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        minecraft.level == null ? FOOTER_SEPARATOR : INWORLD_FOOTER_SEPARATOR,
        0,
        0,
        0.0F,
        0.0F,
        rightPane.height(),
        SEPARATOR_THICKNESS,
        TEXTURE_SIZE,
        SEPARATOR_THICKNESS);
    graphics.pose().popMatrix();
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (controls.isEmpty()) {
      graphics.text(font, EMPTY, emptyX, emptyY, OptionControl.TEXT_COLOR, true);
    }
    if (descriptionArea != null) {
      description.extract(graphics, descriptionArea);
    }
    if (restartRequired) {
      int y = restartY;
      for (FormattedCharSequence line : restartLines) {
        graphics.text(font, line, restartX, y, OptionControl.TEXT_COLOR, true);
        y += font.lineHeight;
      }
    }
  }

  private static Tooltip tooltip(String button) {
    return Tooltip.create(Component.translatable(KEY_PREFIX + button + TOOLTIP_SUFFIX));
  }

  private ToggleStates pendingStates() {
    Map<String, Boolean> pending = new LinkedHashMap<>();
    controls.forEach((toggle, control) -> pending.put(toggle.key(), control.get()));
    return new ToggleStates(pending);
  }

  private boolean hasPendingChanges() {
    return controls.values().stream().anyMatch(OptionControl::isChanged);
  }

  /** True while a restart-bound option differs from the value the game started with. */
  private boolean differsFromStartup() {
    ToggleStates startup = RelativitySettings.startup();
    for (Map.Entry<OptimizationToggle, TickBoxControl> entry : controls.entrySet()) {
      OptimizationToggle toggle = entry.getKey();
      if (toggle.requiresRestart() && entry.getValue().get() != startup.isEnabled(toggle)) {
        return true;
      }
    }
    return false;
  }

  /** Button labels follow YACL: Save Changes / Done, Cancel / Reset; Undo only with changes. */
  private void updateButtons() {
    boolean pending = hasPendingChanges();
    saveOrDone.setMessage(pending ? SAVE : CommonComponents.GUI_DONE);
    saveOrDone.setTooltip(pending ? SAVE_TOOLTIP : DONE_TOOLTIP);
    cancelOrReset.setMessage(pending ? CommonComponents.GUI_CANCEL : RESET_ALL);
    cancelOrReset.setTooltip(pending ? CANCEL_TOOLTIP : RESET_ALL_TOOLTIP);
    undo.active = pending;
    restartRequired = differsFromStartup();
  }

  private void saveOrDone() {
    if (!hasPendingChanges()) {
      onClose();
      return;
    }
    RelativitySettings.save(pendingStates());
    controls.values().forEach(OptionControl::markSaved);
    updateButtons();
  }

  private void cancelOrReset() {
    if (hasPendingChanges()) {
      onClose();
      return;
    }
    controls.values().forEach(OptionControl::resetQuietly);
    updateButtons();
  }

  private void undoAll() {
    controls.values().forEach(OptionControl::undo);
    updateButtons();
  }

  /**
   * The single category. Lays out YACL's columns: list on the left two thirds; on the right, the
   * description and the restart note above Cancel/Reset + Undo, with Save/Done along the bottom.
   */
  private final class GeneralTab implements Tab {
    private final FrameLayout bounds = new FrameLayout();

    @Override
    public Component getTabTitle() {
      return Component.translatable(KEY_PREFIX + "category.general");
    }

    @Override
    public Component getTabExtraNarration() {
      return Component.empty();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
      childrenConsumer.accept(list);
      childrenConsumer.accept(cancelOrReset);
      childrenConsumer.accept(undo);
      childrenConsumer.accept(saveOrDone);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
      bounds.setPosition(area.left(), area.top());
      bounds.setMinDimensions(area.width(), area.height());
      bounds.arrangeElements();
      int column = area.width() / COLUMNS;
      int padding = column / PADDING_DIVISOR;
      int paneWidth = Math.min(column, MAX_COLUMN_WIDTH) - padding * 2;
      int listWidth = column * LIST_COLUMNS;
      int paneCenter = listWidth + column / 2;
      int paneLeft = paneCenter - paneWidth / 2;

      list.updateSizeAndPosition(listWidth + 1, area.height(), area.left(), area.top());
      rightPane =
          new ScreenRectangle(
              area.left() + listWidth + 1, area.top(), area.width() - listWidth - 1, area.height());
      emptyX = area.left() + (listWidth - font.width(EMPTY)) / 2;
      emptyY = area.top() + padding;

      int saveY = area.bottom() - padding - BUTTON_HEIGHT;
      saveOrDone.setRectangle(paneWidth, BUTTON_HEIGHT, paneLeft, saveY);
      int halfWidth = paneWidth / 2 - BUTTON_GAP / 2;
      int topRowY = saveY - BUTTON_HEIGHT - BUTTON_ROW_GAP;
      cancelOrReset.setRectangle(halfWidth, BUTTON_HEIGHT, paneLeft, topRowY);
      undo.setRectangle(halfWidth, BUTTON_HEIGHT, paneLeft + halfWidth + BUTTON_GAP, topRowY);

      restartLines = font.split(RESTART_REQUIRED, paneWidth);
      restartX = paneLeft;
      restartY = topRowY - padding - restartLines.size() * font.lineHeight;

      int descriptionTop = area.top() + padding;
      descriptionArea =
          new ScreenRectangle(
              paneLeft,
              descriptionTop,
              paneWidth,
              Math.max(0, restartY - padding - descriptionTop));
    }

    @Override
    public Layout getLayout() {
      return bounds;
    }
  }
}
