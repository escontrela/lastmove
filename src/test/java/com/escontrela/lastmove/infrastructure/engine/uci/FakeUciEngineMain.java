package com.escontrela.lastmove.infrastructure.engine.uci;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Test subprocess implementing the small UCI subset exercised by {@link UciProcessEngineTest}. */
public final class FakeUciEngineMain {

  private FakeUciEngineMain() {}

  public static void main(String[] args) throws Exception {
    String mode = args.length > 0 ? args[0] : "normal";
    String move = args.length > 1 ? args[1] : "e7e5";
    boolean positionReceived = false;
    boolean searching = false;
    try (BufferedReader input =
            new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output =
            new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
      String command;
      while ((command = input.readLine()) != null) {
        if ("uci".equals(command)) {
          output.println("id name LastMove fake UCI engine");
          if (!"no-uciok".equals(mode)) {
            output.println("uciok");
          }
        } else if ("isready".equals(command)) {
          output.println("readyok");
        } else if (command.startsWith("position fen ")) {
          positionReceived = true;
        } else if (command.startsWith("go ")) {
          if (!positionReceived) {
            output.println("bestmove 0000");
          } else if ("exit-on-go".equals(mode)) {
            return;
          } else if ("ignore-search".equals(mode)) {
            // Deliberately remain alive without producing bestmove.
          } else if ("wait-for-stop".equals(mode)) {
            searching = true;
          } else {
            output.println("info depth 3 score cp 12 pv " + move);
            output.println("bestmove " + move);
          }
        } else if ("stop".equals(command) && searching) {
          searching = false;
          output.println("bestmove " + move);
        } else if ("quit".equals(command) && !"ignore-quit".equals(mode)) {
          return;
        }
      }
    }
  }
}
