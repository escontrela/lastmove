package com.knightshade.engine.evaluation.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.evaluation.PositionalTerm;
import org.junit.jupiter.api.Test;

class WeaknessEvaluationTest {
  private final WeakPawnTerm pawns = new WeakPawnTerm();
  private final OutpostTerm outposts = new OutpostTerm();

  @Test
  void rewardsPressureOnAPawnThatCannotObtainPawnSupport() {
    int remote = score(pawns, "7k/8/8/3p4/8/8/8/K3R3 w - - 0 1");
    int attacked = score(pawns, "7k/8/8/3p4/8/8/8/K2R4 w - - 0 1");
    int pieceDefended = score(pawns, "3r3k/8/8/3p4/8/8/8/K2R4 w - - 0 1");
    int futurePawnDefended = score(pawns, "7k/2p5/8/3p4/8/8/8/K2R4 w - - 0 1");
    int pawnDefended = score(pawns, "7k/8/2p5/3p4/8/8/8/K2R4 w - - 0 1");
    assertEquals(0, remote);
    assertTrue(attacked > pieceDefended && pieceDefended > remote);
    assertTrue(attacked > futurePawnDefended && futurePawnDefended > remote);
    assertEquals(0, pawnDefended);
  }

  @Test
  void doesNotCountRookPressureThroughAnInterveningPiece() {
    assertEquals(0, score(pawns, "7k/8/8/3p4/8/3N4/8/K2R4 w - - 0 1"));
  }

  @Test
  void detectsABackwardPawnAndRecognizesFutureSupportOrACaptureExit() {
    int backward = score(pawns, "7k/5p2/4p3/8/3P4/8/8/K7 w - - 0 1");
    int supportPossible = score(pawns, "7k/5p2/4p3/8/3P4/8/2P5/K7 w - - 0 1");
    int captureAvailable = score(pawns, "7k/5p2/4p3/2p5/3P4/8/8/K7 w - - 0 1");
    assertTrue(backward < 0);
    assertEquals(0, supportPossible);
    assertEquals(0, captureAvailable);
  }

  @Test
  void doesNotTreatAnUnopposedPromotionAsABackwardPawn() {
    assertEquals(0, score(pawns, "7k/3P4/8/8/8/8/8/K7 w - - 0 1"));
  }

  @Test
  void rewardsSupportedOutpostsForBothMinorPieces() {
    int knight = score(outposts, "7k/7p/8/3N4/2P5/8/8/K7 w - - 0 1");
    int bishop = score(outposts, "7k/7p/8/3B4/2P5/8/8/K7 w - - 0 1");
    int unsupported = score(outposts, "7k/7p/8/3N4/8/8/8/K7 w - - 0 1");
    assertTrue(knight > bishop && bishop > 0);
    assertEquals(0, unsupported);
  }

  @Test
  void aPawnThatCanAdvanceToChallengeTheOutpostRemovesTheBonus() {
    assertEquals(0, score(outposts, "7k/4p2p/8/3N4/2P5/8/8/K7 w - - 0 1"));
    assertEquals(0, score(outposts, "7k/7p/4p3/3N4/2P5/8/8/K7 w - - 0 1"));
    assertTrue(score(outposts, "7k/7p/8/3N4/2P1p3/8/8/K7 w - - 0 1") > 0,
        "a pawn already past the outpost cannot turn around to attack it");
  }

  @Test
  void rewardsPreparingASafeOutpostButLessThanOccupyingIt() {
    int prepared = score(outposts, "7k/7p/8/8/2P2N2/8/8/K7 w - - 0 1");
    int occupied = score(outposts, "7k/7p/8/3N4/2P5/8/8/K7 w - - 0 1");
    int contested = score(outposts, "3r3k/7p/8/8/2P2N2/8/8/K7 w - - 0 1");
    assertTrue(occupied > prepared && prepared > 0);
    assertEquals(0, contested);
  }

  @Test
  void doesNotRewardAnInaccessibleHoleOrARearSquare() {
    assertEquals(0, score(outposts, "7k/7p/8/8/2P5/8/8/KN6 w - - 0 1"));
    assertEquals(0, score(outposts, "7k/7p/8/8/8/4N3/3P4/K7 w - - 0 1"));
  }

  @Test
  void bothWeaknessTermsReverseSignWhenColorsAndRanksAreMirrored() {
    int whiteOutpost = score(outposts, "7k/7p/8/3N4/2P5/8/8/K7 w - - 0 1");
    int blackOutpost = score(outposts, "k7/8/8/2p5/3n4/8/7P/7K b - - 0 1");
    assertEquals(whiteOutpost, -blackOutpost);
    int whitePressure = score(pawns, "7k/8/8/3p4/8/8/8/K2R4 w - - 0 1");
    int blackPressure = score(pawns, "k2r4/8/8/8/3P4/8/8/7K b - - 0 1");
    assertEquals(whitePressure, -blackPressure);
  }

  private int score(PositionalTerm term, String fen) {
    return term.evaluate(FenParser.parse(fen));
  }
}
