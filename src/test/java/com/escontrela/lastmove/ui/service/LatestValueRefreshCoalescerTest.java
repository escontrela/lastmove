package com.escontrela.lastmove.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LatestValueRefreshCoalescerTest {
  @Test
  void burstKeepsOnlyLatestValueAndQueuesOneRefreshAtATime() {
    var coalescer = new LatestValueRefreshCoalescer<Integer>();
    var queued = new ArrayList<Runnable>();
    Runnable refresh = () -> {};

    coalescer.offer(1, queued::add, refresh);
    coalescer.offer(2, queued::add, refresh);
    coalescer.offer(3, queued::add, refresh);

    assertEquals(1, queued.size());
    assertEquals(3, coalescer.latest());

    coalescer.finished(1, queued::add, refresh);
    assertEquals(2, queued.size());
    coalescer.finished(3, queued::add, refresh);
    coalescer.offer(4, queued::add, refresh);
    assertEquals(3, queued.size());
    assertEquals(4, coalescer.latest());
  }
}
