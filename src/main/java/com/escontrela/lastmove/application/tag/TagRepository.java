package com.escontrela.lastmove.application.tag;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Persistence boundary for reusable labels and their generic resource assignments. */
public interface TagRepository {
  List<Tag> listAll();
  List<ManagedTag> listManaged();
  Tag findOrCreate(String name);
  List<Tag> findByTarget(TagTarget target);
  Map<TagTarget, List<Tag>> findByTargets(Collection<TagTarget> targets);
  void assign(TagTarget target, Tag tag);
  void remove(TagTarget target, long tagId);
  void update(long tagId, String name);
  void delete(long tagId);
}
