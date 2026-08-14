package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoPgnReader;
import org.junit.jupiter.api.Test;

class GameLoadServiceTest {

  @Test
  void importPgn_returnsTheImportedGameWithoutCreatingAStudySession() {
    GameLoadService service = new GameLoadService(new ChesspressoPgnReader());

    var imported = service.importPgn(PgnImportRequest.fromText("[Event \"Import\"]\n\n1. e4 e5 *"));

    assertEquals("Import", imported.game().getEvent().orElseThrow());
    assertEquals(
        "e4",
        imported.rootVariations().getFirst().execution().move().orElseThrow().san().getValue());
  }
}
