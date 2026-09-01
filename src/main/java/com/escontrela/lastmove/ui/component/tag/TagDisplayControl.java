package com.escontrela.lastmove.ui.component.tag;

import com.escontrela.lastmove.application.tag.Tag;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

/** Read-only, reusable display of labels attached to a library resource. */
public final class TagDisplayControl extends FlowPane {

  public TagDisplayControl() {
    super(6, 4);
    getStyleClass().add("tag-display-control");
  }

  public void setTags(List<Tag> tags) {
    getChildren().setAll(List.copyOf(Objects.requireNonNull(tags, "tags must not be null")).stream()
        .map(tag -> {
          Label chip = new Label(tag.name());
          chip.getStyleClass().add("tag-display-chip");
          return chip;
        }).toList());
    boolean visible = !getChildren().isEmpty();
    setVisible(visible);
    setManaged(visible);
  }
}
