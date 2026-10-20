package com.einsteinmods.relativity.client.config;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Right-hand pane text, as YACL's option description: the option name in bold, then its wrapped
 * description, clipped to the pane. Shows the last option hovered or focused; empty until one is.
 */
final class OptionDescriptionPanel {
  /** Gap between the name and the description, as YACL. */
  private static final int NAME_GAP = 5;

  private final Font font;
  private @Nullable DescribedOption shown;

  /** Wrapped lines of {@link #wrappedName} / {@link #wrappedDescription} at {@link #wrapWidth}. */
  private List<FormattedCharSequence> nameLines = List.of();

  private List<FormattedCharSequence> descriptionLines = List.of();
  private @Nullable Component wrappedName;
  private @Nullable Component wrappedDescription;
  private int wrapWidth;

  OptionDescriptionPanel(Font font) {
    this.font = font;
  }

  void show(DescribedOption option) {
    shown = option;
  }

  void extract(GuiGraphicsExtractor graphics, ScreenRectangle area) {
    DescribedOption option = shown;
    if (option == null) {
      return;
    }
    rewrapIfNeeded(option, area.width());
    graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
    int y = area.top();
    for (FormattedCharSequence line : nameLines) {
      graphics.text(font, line, area.left(), y, OptionControl.TEXT_COLOR, true);
      y += font.lineHeight;
    }
    y += NAME_GAP;
    for (FormattedCharSequence line : descriptionLines) {
      graphics.text(font, line, area.left(), y, OptionControl.TEXT_COLOR, true);
      y += font.lineHeight;
    }
    graphics.disableScissor();
  }

  /** Wraps again only when the option or the width changed. */
  private void rewrapIfNeeded(DescribedOption option, int width) {
    Component name = option.name();
    Component description = option.description();
    if (name == wrappedName && description == wrappedDescription && width == wrapWidth) {
      return;
    }
    wrappedName = name;
    wrappedDescription = description;
    wrapWidth = width;
    nameLines = font.split(name.copy().withStyle(ChatFormatting.BOLD), width);
    descriptionLines = font.split(description, width);
  }
}
