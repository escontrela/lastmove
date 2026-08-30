package com.escontrela.lastmove.infrastructure.lichess;

import com.escontrela.lastmove.application.arena.LichessBotClient;
import com.escontrela.lastmove.application.arena.LichessTournamentRequestException;
import com.escontrela.lastmove.application.arena.LichessTournamentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Java HttpClient adapter for Lichess Bot API; each NDJSON stream is consumed on its own daemon task. */
@Component
public final class HttpLichessBotClient implements LichessBotClient {
  private static final Logger log=LoggerFactory.getLogger(HttpLichessBotClient.class);
  private static final String API = "https://lichess.org"; private static final Duration TIMEOUT=Duration.ofSeconds(15);
  private final HttpClient client; private final ObjectMapper json; private final ExecutorService streams=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"lichess-stream");t.setDaemon(true);return t;});
  public HttpLichessBotClient(){this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),new ObjectMapper());}
  HttpLichessBotClient(HttpClient client,ObjectMapper json){this.client=Objects.requireNonNull(client);this.json=Objects.requireNonNull(json);}
  public StreamHandle streamEvents(String token,Consumer<JsonNode> event,Consumer<Throwable> closed){return stream(token,"/api/stream/event",event,closed);}
  public StreamHandle streamGame(String token,String gameId,Consumer<JsonNode> event,Consumer<Throwable> closed){return stream(token,"/api/bot/game/stream/"+gameId,event,closed);}
  public JsonNode currentGames(String token){try{HttpResponse<String> response=client.send(request(token,"/api/account/playing").header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("Lichess current games request failed (HTTP "+response.statusCode()+").");return json.readTree(response.body());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Lichess current games request interrupted.",e);}catch(IOException e){throw new IllegalStateException("Could not read current Lichess games.",e);}}
  public List<LichessTournamentSnapshot> currentTournaments(String token){
    try {
      HttpResponse<String> response=client.send(request(token,"/api/tournament").header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200) throw tournamentFailure(response.statusCode());
      return LichessTournamentJsonMapper.mapCurrentTournaments(json.readTree(response.body()));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LichessTournamentRequestException(LichessTournamentRequestException.Kind.TRANSPORT,"Lichess tournament request was interrupted.",exception);
    } catch (IOException exception) {
      throw new LichessTournamentRequestException(LichessTournamentRequestException.Kind.TRANSPORT,"Could not reach Lichess to load tournaments.",exception);
    }
  }
  public void acceptChallenge(String token,String id){post(token,"/api/challenge/"+id+"/accept","");}
  public void declineChallenge(String token,String id,String reason){post(token,"/api/challenge/"+id+"/decline","reason="+java.net.URLEncoder.encode(reason,java.nio.charset.StandardCharsets.UTF_8));}
  public void sendMove(String token,String gameId,String uci){post(token,"/api/bot/game/"+gameId+"/move/"+uci,"");}
  public void resign(String token,String gameId){post(token,"/api/bot/game/"+gameId+"/resign","");}
  public void offerDraw(String token,String gameId){post(token,"/api/bot/game/"+gameId+"/draw/yes","");}
  private StreamHandle stream(String token,String path,Consumer<JsonNode> event,Consumer<Throwable> closed){
    Future<?> task=streams.submit(()->{try{HttpResponse<InputStream> response=client.send(request(token,path).GET().build(),HttpResponse.BodyHandlers.ofInputStream());if(response.statusCode()!=200)throw new IOException("Lichess stream returned HTTP "+response.statusCode());log.info("Lichess NDJSON stream opened: path={}",path);try(BufferedReader reader=new BufferedReader(new InputStreamReader(response.body()))){String line;while(!Thread.currentThread().isInterrupted()&&(line=reader.readLine())!=null)if(!line.isBlank()){JsonNode node=json.readTree(line);log.info("Lichess NDJSON event received: path={} type={} moves={}",path,node.path("type").asText(),node.path("moves").asText(node.path("state").path("moves").asText()));event.accept(node);}if(!Thread.currentThread().isInterrupted())throw new IOException("Lichess NDJSON stream ended unexpectedly");}}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(Throwable failure){if(!Thread.currentThread().isInterrupted()){log.warn("Lichess NDJSON stream failed: path={}",path,failure);closed.accept(failure);}} });
    return ()->task.cancel(true);
  }
  private void post(String token,String path,String body){try{HttpResponse<String> response=client.send(request(token,path).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Lichess request failed (HTTP "+response.statusCode()+").");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Lichess request interrupted.",e);}catch(IOException e){throw new IllegalStateException("Could not reach Lichess.",e);}}
  private static LichessTournamentRequestException tournamentFailure(int status) {
    return switch (status) {
      case 401 -> new LichessTournamentRequestException(LichessTournamentRequestException.Kind.UNAUTHORIZED,"Lichess rejected the bot token while loading tournaments.");
      case 403 -> new LichessTournamentRequestException(LichessTournamentRequestException.Kind.FORBIDDEN,"The configured Lichess token is not allowed to load tournaments.");
      case 429 -> new LichessTournamentRequestException(LichessTournamentRequestException.Kind.RATE_LIMITED,"Lichess is rate-limiting tournament requests. Wait one minute before retrying.");
      default -> new LichessTournamentRequestException(LichessTournamentRequestException.Kind.UNEXPECTED_RESPONSE,"Lichess tournament request failed (HTTP "+status+").");
    };
  }
  private HttpRequest.Builder request(String token,String path){return HttpRequest.newBuilder(URI.create(API+path)).timeout(TIMEOUT).header("Accept","application/x-ndjson, application/json").header("Authorization","Bearer "+required(token));}
  private static String required(String token){String v=Objects.requireNonNull(token).trim();if(v.isEmpty())throw new IllegalArgumentException("Lichess bot token must not be blank");return v;}
}
