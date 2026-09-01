package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.tag.Tag;
import com.escontrela.lastmove.application.tag.TagRepository;
import com.escontrela.lastmove.application.tag.TagTarget;
import com.escontrela.lastmove.application.tag.TagTargetType;
import com.escontrela.lastmove.domain.game.GameId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Coordinates generic tag creation and assignment for games and future library resources. */
@Service
public final class TagService {
  private final TagRepository tags;

  public TagService(TagRepository tags) {
    this.tags = Objects.requireNonNull(tags, "tags must not be null");
  }

  public List<Tag> availableTags() {
    return tags.listAll();
  }

  public List<Tag> tagsFor(TagTarget target) {
    return tags.findByTarget(Objects.requireNonNull(target, "target must not be null"));
  }

  public Tag assign(TagTarget target, String name) {
    Tag tag = tags.findOrCreate(name);
    tags.assign(Objects.requireNonNull(target, "target must not be null"), tag);
    return tag;
  }

  public void remove(TagTarget target, long tagId) {
    tags.remove(Objects.requireNonNull(target, "target must not be null"), tagId);
  }

  public Tag assignToGame(GameId gameId, String name) {
    return assign(gameTarget(gameId), name);
  }

  public void removeFromGame(GameId gameId, long tagId) {
    remove(gameTarget(gameId), tagId);
  }

  public Map<GameId, List<Tag>> tagsForGames(Collection<GameId> gameIds) {
    List<GameId> ids = List.copyOf(Objects.requireNonNull(gameIds, "game ids must not be null"));
    Map<TagTarget, List<Tag>> assigned = tags.findByTargets(ids.stream().map(TagService::gameTarget).toList());
    Map<GameId, List<Tag>> result = new LinkedHashMap<>();
    for (GameId gameId : ids) {
      result.put(gameId, assigned.getOrDefault(gameTarget(gameId), List.of()));
    }
    return result;
  }

  public static TagTarget gameTarget(GameId gameId) {
    return new TagTarget(TagTargetType.GAME, Objects.requireNonNull(gameId, "game id must not be null").value().toString());
  }
}
