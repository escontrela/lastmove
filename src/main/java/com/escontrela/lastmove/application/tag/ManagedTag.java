package com.escontrela.lastmove.application.tag;

/** A tag together with its lifecycle state and global assignment count. */
public record ManagedTag(long id, String name, int assetCount) {
  public ManagedTag {
    if (id < 0) throw new IllegalArgumentException("tag id must not be negative");
    Tag.normalizedName(name);
    name = name.trim();
    if (assetCount < 0) throw new IllegalArgumentException("asset count must not be negative");
  }
}
