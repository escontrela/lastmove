package com.knightshade.engine.benchmark;

import com.knightshade.engine.KnightshadeEngine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchTelemetryListener;
import com.knightshade.engine.api.StopSignal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Headless, repeatable performance harness for Knightshade search.
 *
 * <p>It records individual samples and grouped medians. It does not measure Elo.
 */
public final class SearchBenchmark {

  private static final List<CorpusPosition> BUILT_IN_CORPUS =
      List.of(
          position("opening-start", "opening",
              "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
          position("opening-italian", "opening",
              "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"),
          position("tactical-queen-sacrifice", "tactical",
              "r3k2r/1pQ2p1p/4p1p1/P2p3q/3P4/P3N3/2R1PP1N/2B3RK b - - 1 23"),
          position("tactical-mate", "tactical",
              "6k1/5ppp/8/8/8/8/8/4R2K w - - 0 1"),
          position("endgame-pawns", "endgame",
              "8/5pk1/4p1p1/3pP3/3P1P2/5KP1/8/8 w - - 0 40"),
          position("endgame-rook", "endgame",
              "8/5pk1/8/3r4/3P4/5KP1/8/3R4 w - - 0 40"));

  private SearchBenchmark() {}

  public static void main(String[] args) throws IOException {
    Options options = Options.parse(args);
    List<CorpusPosition> corpus = selectCorpus(options);
    Metadata metadata = Metadata.capture(options);
    List<Run> runs = new ArrayList<>();

    for (int round = 0; round < options.warmups(); round++) {
      warm(corpus, options);
    }
    for (int repetition = 1; repetition <= options.repetitions(); repetition++) {
      for (boolean telemetry : options.telemetryModes()) {
        for (int threads : options.threads()) {
          for (CorpusPosition position : corpus) {
            runs.add(run(position, options, repetition, threads, telemetry));
          }
        }
      }
    }

    String csv = toCsv(metadata, runs);
    if (options.output() == null) {
      System.out.print(csv);
    } else {
      Path parent = options.output().toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(options.output(), csv, StandardCharsets.UTF_8);
      System.out.println("Wrote " + options.output().toAbsolutePath());
    }
  }

  private static List<CorpusPosition> selectCorpus(Options options) throws IOException {
    List<CorpusPosition> corpus = new ArrayList<>(BUILT_IN_CORPUS);
    if (options.historyFen() != null) corpus.add(loadFenHistory(options.historyFen()));
    if (!options.suite().equals("representative")) return corpus;
    return corpus.stream()
        .filter(position -> position.id().equals("opening-italian")
            || position.id().equals("tactical-queen-sacrifice")
            || position.id().equals("endgame-pawns")
            || position.id().startsWith("match-"))
        .toList();
  }

  private static void warm(List<CorpusPosition> corpus, Options options) {
    for (int threads : options.threads()) {
      for (CorpusPosition position : corpus) {
        new KnightshadeEngine(threads).search(
            position.fen(), position.history(), SearchLimits.depth(options.warmupDepth()), StopSignal.never());
      }
    }
  }

  private static Run run(
      CorpusPosition position, Options options, int repetition, int threads, boolean telemetry) {
    SearchLimits limits =
        options.timeMillis() > 0
            ? SearchLimits.timeOnly(java.time.Duration.ofMillis(options.timeMillis()))
            : SearchLimits.depth(options.depth());
    var result =
        new KnightshadeEngine(threads).search(
            position.fen(),
            position.history(),
            limits,
            StopSignal.never(),
            telemetry ? snapshot -> {} : SearchTelemetryListener.NONE);
    return new Run(
        position.id(),
        position.phase(),
        repetition,
        threads,
        telemetry,
        result.depth(),
        result.move() == null ? "" : result.move().toUci(),
        result.score(),
        result.nodes(),
        result.elapsedMillis(),
        result.elapsedMillis() == 0 ? 0 : result.nodes() * 1_000L / result.elapsedMillis());
  }

  /**
   * Loads a replay extracted from an official PGN. First non-comment line is {@code id|phase};
   * following lines are chronological FEN positions and include the current root as the last line.
   */
  private static CorpusPosition loadFenHistory(Path path) throws IOException {
    List<String> lines =
        Files.readAllLines(path, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();
    if (lines.size() < 2) {
      throw new IllegalArgumentException("A FEN history file needs an id line and at least one FEN");
    }
    String[] descriptor = lines.getFirst().split("\\|", 2);
    List<String> positions = lines.subList(1, lines.size());
    return new CorpusPosition(
        descriptor[0].trim(),
        descriptor.length == 2 ? descriptor[1].trim() : "match",
        positions.getLast(),
        List.copyOf(positions));
  }

  private static String toCsv(Metadata metadata, List<Run> runs) {
    StringBuilder csv = new StringBuilder();
    csv.append(
        "recordType,label,build,jdk,vm,os,arch,availableProcessors,maxMemoryBytes,startedAt,"
            + "suite,limitType,limitValue,position,phase,repetition,threads,telemetry,depth,move,"
            + "score,nodes,elapsedMillis,nps,medianElapsedMillis,stddevElapsedMillis,medianNps\n");
    for (Run run : runs) {
      appendPrefix(csv, "run", metadata);
      csv.append(run.position()).append(',').append(run.phase()).append(',').append(run.repetition())
          .append(',').append(run.threads()).append(',').append(run.telemetry() ? "on" : "off")
          .append(',').append(run.depth()).append(',').append(run.move()).append(',')
          .append(run.score()).append(',').append(run.nodes()).append(',').append(run.elapsedMillis())
          .append(',').append(run.nps()).append(",,,\n");
    }
    for (Summary summary : summarize(runs)) {
      appendPrefix(csv, "summary", metadata);
      csv.append(summary.position()).append(',').append(summary.phase()).append(",,")
          .append(summary.threads()).append(',').append(summary.telemetry() ? "on" : "off")
          .append(",,,,,,,").append(summary.medianElapsedMillis()).append(',')
          .append(String.format(Locale.ROOT, "%.2f", summary.stddevElapsedMillis())).append(',')
          .append(summary.medianNps()).append('\n');
    }
    return csv.toString();
  }

  private static void appendPrefix(StringBuilder csv, String recordType, Metadata metadata) {
    csv.append(recordType).append(',').append(csv(metadata.label())).append(',')
        .append(csv(metadata.build())).append(',').append(csv(metadata.jdk())).append(',')
        .append(csv(metadata.vm())).append(',').append(csv(metadata.os())).append(',')
        .append(csv(metadata.arch())).append(',').append(metadata.availableProcessors()).append(',')
        .append(metadata.maxMemoryBytes()).append(',').append(metadata.startedAt()).append(',')
        .append(metadata.suite()).append(',').append(metadata.limitType()).append(',')
        .append(metadata.limitValue()).append(',');
  }

  private static List<Summary> summarize(List<Run> runs) {
    Map<String, List<Run>> groups = new LinkedHashMap<>();
    for (Run run : runs) {
      groups.computeIfAbsent(run.position() + "|" + run.threads() + "|" + run.telemetry(),
          ignored -> new ArrayList<>()).add(run);
    }
    return groups.values().stream()
        .map(SearchBenchmark::summary)
        .sorted(Comparator.comparing(Summary::position).thenComparingInt(Summary::threads))
        .toList();
  }

  private static Summary summary(List<Run> runs) {
    Run first = runs.getFirst();
    List<Long> elapsed = runs.stream().map(Run::elapsedMillis).sorted().toList();
    List<Long> nps = runs.stream().map(Run::nps).sorted().toList();
    double mean = elapsed.stream().mapToLong(Long::longValue).average().orElse(0);
    double variance =
        elapsed.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0);
    return new Summary(first.position(), first.phase(), first.threads(), first.telemetry(), median(elapsed),
        Math.sqrt(variance), median(nps));
  }

  private static long median(List<Long> values) {
    int middle = values.size() / 2;
    return values.size() % 2 == 0 ? (values.get(middle - 1) + values.get(middle)) / 2 : values.get(middle);
  }

  private static CorpusPosition position(String id, String phase, String fen) {
    return new CorpusPosition(id, phase, fen, List.of());
  }

  private static String csv(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private record CorpusPosition(String id, String phase, String fen, List<String> history) {
    private CorpusPosition {
      Objects.requireNonNull(id);
      Objects.requireNonNull(phase);
      Objects.requireNonNull(fen);
      history = List.copyOf(history);
    }
  }

  private record Run(
      String position, String phase, int repetition, int threads, boolean telemetry, int depth,
      String move, int score, long nodes, long elapsedMillis, long nps) {}

  private record Summary(
      String position, String phase, int threads, boolean telemetry, long medianElapsedMillis,
      double stddevElapsedMillis, long medianNps) {}

  private record Metadata(
      String label, String build, String jdk, String vm, String os, String arch,
      int availableProcessors, long maxMemoryBytes, Instant startedAt, String suite,
      String limitType, long limitValue) {
    private static Metadata capture(Options options) {
      Runtime runtime = Runtime.getRuntime();
      return new Metadata(
          options.label(), System.getProperty("knightshade.build", "unknown"),
          System.getProperty("java.runtime.version"), System.getProperty("java.vm.name"),
          System.getProperty("os.name"), System.getProperty("os.arch"), runtime.availableProcessors(),
          runtime.maxMemory(), Instant.now(), options.suite(),
          options.timeMillis() > 0 ? "time" : "depth",
          options.timeMillis() > 0 ? options.timeMillis() : options.depth());
    }
  }

  private record Options(
      String label, String suite, int depth, long timeMillis, int repetitions, int warmups,
      int warmupDepth, List<Integer> threads, List<Boolean> telemetryModes, Path output,
      Path historyFen) {
    private static Options parse(String[] args) {
      Map<String, String> arguments = new LinkedHashMap<>();
      for (int index = 0; index < args.length; index += 2) {
        if (!args[index].startsWith("--") || index + 1 >= args.length) {
          throw new IllegalArgumentException("Use --name value arguments");
        }
        arguments.put(args[index].substring(2), args[index + 1]);
      }
      return new Options(
          arguments.getOrDefault("label", "unnamed"),
          arguments.getOrDefault("suite", "all"),
          Integer.parseInt(arguments.getOrDefault("depth", "8")),
          Long.parseLong(arguments.getOrDefault("time-ms", "0")),
          Integer.parseInt(arguments.getOrDefault("repetitions", "5")),
          Integer.parseInt(arguments.getOrDefault("warmups", "2")),
          Integer.parseInt(arguments.getOrDefault("warmup-depth", "3")),
          List.of(arguments.getOrDefault("threads", "1").split(",")).stream()
              .map(Integer::parseInt).toList(),
          telemetry(arguments.getOrDefault("telemetry", "both")),
          arguments.containsKey("output") ? Path.of(arguments.get("output")) : null,
          arguments.containsKey("history-fen") ? Path.of(arguments.get("history-fen")) : null);
    }

    private static List<Boolean> telemetry(String value) {
      return switch (value) {
        case "off" -> List.of(false);
        case "on" -> List.of(true);
        case "both" -> List.of(false, true);
        default -> throw new IllegalArgumentException("telemetry must be off, on or both");
      };
    }
  }
}
