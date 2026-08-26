package com.escontrela.lastmove.infrastructure.chesspresso;

import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.ImportedPly;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads PGN into LastMove's engine-neutral tree.
 *
 * <p>Chesspresso remains the rules engine, but its PGN reader loses deeply nested Lichess
 * recursive annotation variations after comments such as {@code [%cal ...]}. This reader
 * tokenizes the movetext itself and validates every SAN through the rules adapter, preserving
 * branches and move comments at the infrastructure boundary.
 */
@Component
public class ChesspressoPgnReader {

  private static final Pattern TAG =
      Pattern.compile("^\\s*\\[([^\\s]+)\\s+\\\"((?:\\\\.|[^\\\"])*)\\\"\\]\\s*$");
  private final ChesspressoRulesEngine rulesEngine;

  public ChesspressoPgnReader() {
    this(new ChesspressoRulesEngine());
  }

  ChesspressoPgnReader(ChesspressoRulesEngine rulesEngine) {
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
  }

  public PgnGame readFirst(String pgn) throws Exception {
    return readImportedFirst(pgn).game();
  }

  /** Reads the first game and all of its recursive annotation variations. */
  public ImportedPgnGame readImportedFirst(String pgn) throws Exception {
    Objects.requireNonNull(pgn, "pgn must not be null");
    ParsedPgn parsed = ParsedPgn.parse(pgn);
    PositionSnapshot initialPosition =
        parsed.startingFen == null
            ? rulesEngine.startingPosition()
            : rulesEngine.positionFrom(parsed.startingFen);
    List<MutablePly> roots = new MovetextParser(parsed.moveText, rulesEngine).parse(initialPosition);
    return new ImportedPgnGame(parsed.toGame(), freeze(roots));
  }

  public PgnGame readFirst(InputStream inputStream, String sourceName) throws Exception {
    return readImportedFirst(inputStream, sourceName).game();
  }

  public ImportedPgnGame readImportedFirst(InputStream inputStream, String sourceName) throws Exception {
    Objects.requireNonNull(inputStream, "inputStream must not be null");
    return readImportedFirst(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
  }

  public ImportedPgnGame readImportedFirst(Path path) throws Exception {
    try (InputStream inputStream = Files.newInputStream(path)) {
      return readImportedFirst(inputStream, path.getFileName().toString());
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to read PGN file: " + path, exception);
    }
  }

  private static List<ImportedPly> freeze(List<MutablePly> source) {
    return source.stream()
        .map(ply -> new ImportedPly(ply.execution, ply.comment.toString(), freeze(ply.variations)))
        .toList();
  }

  private static final class MovetextParser {
    private final List<Token> tokens;
    private final ChesspressoRulesEngine rulesEngine;
    private int index;

    private MovetextParser(String movetext, ChesspressoRulesEngine rulesEngine) {
      this.tokens = tokenize(movetext);
      this.rulesEngine = rulesEngine;
    }

    private List<MutablePly> parse(PositionSnapshot initialPosition) {
      List<MutablePly> roots = new ArrayList<>();
      parseLine(initialPosition, roots, false);
      return roots;
    }

    private void parseLine(PositionSnapshot position, List<MutablePly> variations, boolean nested) {
      PositionSnapshot current = position;
      PositionSnapshot beforeLast = position;
      List<MutablePly> beforeLastVariations = variations;
      MutablePly last = null;
      StringBuilder leadingComment = new StringBuilder();
      while (index < tokens.size()) {
        Token token = tokens.get(index++);
        if (token.type == TokenType.CLOSE) {
          if (!nested) throw error("Unexpected ')' in movetext");
          return;
        }
        if (token.type == TokenType.OPEN) {
          if (last == null) throw error("A variation must follow a move");
          parseLine(beforeLast, beforeLastVariations, true);
          continue;
        }
        if (token.type == TokenType.COMMENT) {
          appendComment(last == null ? leadingComment : last.comment, token.value);
          continue;
        }
        if (token.type != TokenType.SYMBOL || isMoveNumber(token.value) || isNag(token.value)) continue;
        if (isResult(token.value)) continue;
        MoveExecutionResult execution = rulesEngine.execute(current, SanMove.of(token.value));
        if (!execution.accepted()) {
          throw error(
              "Illegal SAN '"
                  + token.value
                  + "': "
                  + execution.rejectionReason().orElse("unknown reason"));
        }
        MutablePly next = new MutablePly(execution);
        appendComment(next.comment, leadingComment.toString());
        leadingComment.setLength(0);
        variations.add(next);
        beforeLast = current;
        beforeLastVariations = variations;
        current = execution.newSnapshot();
        last = next;
        variations = next.variations;
      }
      if (nested) throw error("Unclosed '(' in movetext");
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(message + " near token " + index);
    }
  }

  private static final class MutablePly {
    private final MoveExecutionResult execution;
    private final StringBuilder comment = new StringBuilder();
    private final List<MutablePly> variations = new ArrayList<>();

    private MutablePly(MoveExecutionResult execution) {
      this.execution = execution;
    }
  }

  private record ParsedPgn(Map<String, String> headers, String moveText, Fen startingFen, GameResult result) {
    private static ParsedPgn parse(String pgn) {
      Map<String, String> headers = new LinkedHashMap<>();
      StringBuilder movetext = new StringBuilder();
      for (String line : pgn.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
        Matcher tag = TAG.matcher(line);
        if (tag.matches()) {
          headers.put(tag.group(1), tag.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
        } else {
          movetext.append(line).append('\n');
        }
      }
      String fen = headers.get("FEN");
      return new ParsedPgn(
          headers,
          movetext.toString(),
          fen == null || fen.isBlank() ? null : Fen.of(fen),
          GameResult.fromPgn(headers.getOrDefault("Result", "*")));
    }

    private PgnGame toGame() {
      return new PgnGame(headers, moveText, result, startingFen);
    }
  }

  private enum TokenType {
    SYMBOL,
    COMMENT,
    OPEN,
    CLOSE
  }

  private record Token(TokenType type, String value) {}

  private static List<Token> tokenize(String text) {
    List<Token> tokens = new ArrayList<>();
    StringBuilder symbol = new StringBuilder();
    for (int cursor = 0; cursor < text.length(); cursor++) {
      char current = text.charAt(cursor);
      if (Character.isWhitespace(current)) {
        addSymbol(tokens, symbol);
      } else if (current == '{') {
        addSymbol(tokens, symbol);
        int end = text.indexOf('}', cursor + 1);
        if (end < 0) throw new IllegalArgumentException("Unclosed PGN comment");
        tokens.add(new Token(TokenType.COMMENT, text.substring(cursor + 1, end)));
        cursor = end;
      } else if (current == ';') {
        addSymbol(tokens, symbol);
        int end = text.indexOf('\n', cursor + 1);
        if (end < 0) end = text.length();
        tokens.add(new Token(TokenType.COMMENT, text.substring(cursor + 1, end)));
        cursor = end;
      } else if (current == '(' || current == ')') {
        addSymbol(tokens, symbol);
        tokens.add(new Token(current == '(' ? TokenType.OPEN : TokenType.CLOSE, Character.toString(current)));
      } else {
        symbol.append(current);
      }
    }
    addSymbol(tokens, symbol);
    return List.copyOf(tokens);
  }

  private static void addSymbol(List<Token> tokens, StringBuilder symbol) {
    if (!symbol.isEmpty()) {
      tokens.add(new Token(TokenType.SYMBOL, symbol.toString()));
      symbol.setLength(0);
    }
  }

  private static void appendComment(StringBuilder destination, String comment) {
    String normalized = comment == null ? "" : comment.strip();
    if (normalized.isBlank()) return;
    if (!destination.isEmpty()) destination.append('\n');
    destination.append(normalized);
  }

  private static boolean isMoveNumber(String value) {
    return value.matches("\\d+\\.{1,3}");
  }

  private static boolean isNag(String value) {
    return value.matches("\\$\\d+");
  }

  private static boolean isResult(String value) {
    return value.equals("1-0") || value.equals("0-1") || value.equals("1/2-1/2") || value.equals("*");
  }
}
