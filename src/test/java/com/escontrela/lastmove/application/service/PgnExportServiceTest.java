package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionFactory;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoPgnReader;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.escontrela.lastmove.infrastructure.session.InMemoryAnalysisSessionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class PgnExportServiceTest {

  private final InMemoryAnalysisSessionRepository repository =
      new InMemoryAnalysisSessionRepository();
  private final FenService fenService = new FenService();
  private final AnalysisSessionService analysisService =
      new AnalysisSessionService(
          repository,
          new ChessGameFactory(new ChesspressoRulesEngine()),
          new AnalysisSessionFactory(),
          fenService);
  private final PgnExportService exportService = new PgnExportService(repository, fenService);

  @Test
  void exportsTheMainLineAndVariationsAsParseablePgn() throws Exception {
    var imported =
        new ChesspressoPgnReader()
            .readImportedFirst(
                "[Event \"Export test\"]\n\n1. e4 e5 (1... c5) 2. Nf3 (2. Bc4) *");
    var session = analysisService.createPgnSession(imported);

    String exported = exportService.export(session.sessionId());
    var reparsed = new ChesspressoPgnReader().readImportedFirst(exported);

    assertTrue(exported.contains("[Event \"? vs. ? – Export test\"]"));
    assertTrue(exported.contains("1. e4 e5 (1... c5) 2. Nf3 (2. Bc4) *"), exported);
    assertEquals(
        List.of("e4"),
        reparsed.rootVariations().stream()
            .map(ply -> ply.execution().move().orElseThrow().san().getValue())
            .toList());
  }

  @Test
  void includesSetupAndFenTagsForANonStandardInitialPosition() {
    String fen = "8/8/8/8/8/8/8/K6k b - - 7 42";
    var session = analysisService.createFenSession(Fen.of(fen));

    String exported = exportService.export(session.sessionId());

    assertTrue(exported.contains("[SetUp \"1\"]"));
    assertTrue(exported.contains("[FEN \"" + fen + "\"]"));
    assertTrue(exported.endsWith("*\n"));
  }
}
