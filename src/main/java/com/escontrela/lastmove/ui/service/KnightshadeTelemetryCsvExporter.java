package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/** Builds the detail export without JavaFX or position/FEN data. */
public final class KnightshadeTelemetryCsvExporter {
  private KnightshadeTelemetryCsvExporter() {}

  public static String toCsv(List<SearchTelemetrySnapshot> samples, Set<String> parameters,
      KnightshadeTelemetryService telemetry) {
    StringBuilder csv = new StringBuilder("gameId,searchId,event,searchStartedAt,observedAt,sideToMove,fullmoveNumber,limits,engineVersion,engineBuild,depth,score,scoreType,bestMove,elapsedMillis,parameters,mainNodes,qNodes,nps,ttProbes,ttHits,ttCutoffs,betaCutoffs,pvsResearches,nullMoveAttempts,nullMoveCutoffs,lmrApplications,lmrResearches,aspirationRetries,mateConfirmations,evaluationCacheHits,evaluationCacheMisses,requestedWorkers,activeWorkers,quiescenceEntries,standPatCutoffs,stalemateChecks,moveListsGenerated,quietChecksExamined,seeEvaluations,seePrunes,quietnessMetricsAvailable,stopReason,ponderReusedDepth,ponderHits,ponderMisses,ponderDecisions,ponderHitRate,ponderReusedDepthAvg,ponderReusedDepthMax,ponderStarts\n");
    String visibleParameters = String.join("|", parameters);
    for (SearchTelemetrySnapshot sample : samples) {
      var context = sample.context();
      csv.append(csvValue(context.gameId())).append(',').append(csvValue(context.searchId())).append(',')
          .append(sample.event()).append(',').append(context.startedAt()).append(',')
          .append(sample.observedAt()).append(',').append(context.sideToMove()).append(',')
          .append(context.fullmoveNumber()).append(',').append(csvValue(context.limits())).append(',')
          .append(csvValue(context.engineVersion())).append(',').append(csvValue(context.engineBuild())).append(',')
          .append(sample.depth()).append(',').append(sample.score()).append(',')
          .append(sample.isMateScore() ? "MATE" : "CENTIPAWNS").append(',')
          .append(csvValue(sample.bestMove() == null ? "" : sample.bestMove().toUci())).append(',')
          .append(sample.elapsedMillis()).append(',').append(csvValue(visibleParameters)).append(',')
          .append(sample.mainNodes()).append(',').append(sample.qNodes()).append(',').append(nps(sample)).append(',')
          .append(sample.ttProbes()).append(',').append(sample.ttHits()).append(',').append(sample.ttCutoffs()).append(',')
          .append(sample.betaCutoffs()).append(',').append(sample.pvsResearches()).append(',')
          .append(sample.nullMoveAttempts()).append(',').append(sample.nullMoveCutoffs()).append(',')
          .append(sample.lmrApplications()).append(',').append(sample.lmrResearches()).append(',')
          .append(sample.aspirationRetries()).append(',').append(sample.mateConfirmations()).append(',')
          .append(sample.evaluationCacheHits()).append(',').append(sample.evaluationCacheMisses()).append(',')
          .append(sample.requestedWorkers()).append(',').append(sample.activeWorkers()).append(',')
          .append(sample.quiescenceEntries()).append(',').append(sample.standPatCutoffs()).append(',')
          .append(sample.stalemateChecks()).append(',').append(sample.moveListsGenerated()).append(',')
          .append(sample.quietChecksExamined()).append(',').append(sample.seeEvaluations()).append(',')
          .append(sample.seePrunes()).append(',').append(sample.quietnessMetricsAvailable()).append(',')
          .append(sample.stopReason()).append(',').append(sample.ponderReusedDepth()).append(',')
          .append(telemetry.ponderHits()).append(',').append(telemetry.ponderMisses()).append(',')
          .append(telemetry.ponderDecisions()).append(',').append(rate(telemetry)).append(',')
          .append(averageDepth(telemetry)).append(',').append(maxDepth(telemetry)).append(',')
          .append(telemetry.ponderStarts()).append('\n');
    }
    return csv.toString();
  }

  private static long nps(SearchTelemetrySnapshot sample) {
    return sample.elapsedMillis() <= 0 ? 0
        : (sample.mainNodes() + sample.qNodes()) * 1_000L / sample.elapsedMillis();
  }

  private static String rate(KnightshadeTelemetryService telemetry) {
    return telemetry.ponderHitRate().isPresent()
        ? String.format(Locale.ROOT, "%.6f", telemetry.ponderHitRate().getAsDouble()) : "";
  }

  private static String averageDepth(KnightshadeTelemetryService telemetry) {
    return telemetry.ponderReusedDepthAverage().isPresent()
        ? String.format(Locale.ROOT, "%.2f", telemetry.ponderReusedDepthAverage().getAsDouble()) : "";
  }

  private static String maxDepth(KnightshadeTelemetryService telemetry) {
    return telemetry.ponderReusedDepthMax().isPresent()
        ? Integer.toString(telemetry.ponderReusedDepthMax().getAsInt()) : "";
  }

  private static String csvValue(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return text.contains(",") || text.contains("\"")
        ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
  }
}
