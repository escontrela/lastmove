package com.escontrela.lastmove.infrastructure.lichess;

import com.escontrela.lastmove.application.arena.LichessBotClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Java HttpClient adapter for Lichess Bot API; each NDJSON stream is consumed on its own daemon task. */
@Component
public final class HttpLichessBotClient implements LichessBotClient {
  private static final String API = "https://lichess.org"; private static final Duration TIMEOUT=Duration.ofSeconds(15);
  private final HttpClient client; private final ObjectMapper json; private final ExecutorService streams=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"lichess-stream");t.setDaemon(true);return t;});
  public HttpLichessBotClient(){this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),new ObjectMapper());}
  HttpLichessBotClient(HttpClient client,ObjectMapper json){this.client=Objects.requireNonNull(client);this.json=Objects.requireNonNull(json);}
  public StreamHandle streamEvents(String token,Consumer<JsonNode> event,Consumer<Throwable> closed){return stream(token,"/api/stream/event",event,closed);}
  public StreamHandle streamGame(String token,String gameId,Consumer<JsonNode> event,Consumer<Throwable> closed){return stream(token,"/api/bot/game/stream/"+gameId,event,closed);}
  public void acceptChallenge(String token,String id){post(token,"/api/challenge/"+id+"/accept","");}
  public void declineChallenge(String token,String id,String reason){post(token,"/api/challenge/"+id+"/decline","reason="+java.net.URLEncoder.encode(reason,java.nio.charset.StandardCharsets.UTF_8));}
  public void sendMove(String token,String gameId,String uci){post(token,"/api/bot/game/"+gameId+"/move/"+uci,"");}
  public void resign(String token,String gameId){post(token,"/api/bot/game/"+gameId+"/resign","");}
  public void offerDraw(String token,String gameId){post(token,"/api/bot/game/"+gameId+"/draw/yes","");}
  private StreamHandle stream(String token,String path,Consumer<JsonNode> event,Consumer<Throwable> closed){
    Future<?> task=streams.submit(()->{try{HttpResponse<InputStream> response=client.send(request(token,path).GET().build(),HttpResponse.BodyHandlers.ofInputStream());if(response.statusCode()!=200)throw new IOException("Lichess stream returned HTTP "+response.statusCode());try(BufferedReader reader=new BufferedReader(new InputStreamReader(response.body()))){String line;while(!Thread.currentThread().isInterrupted()&&(line=reader.readLine())!=null)if(!line.isBlank())event.accept(json.readTree(line));}}catch(Throwable failure){closed.accept(failure);} });
    return ()->task.cancel(true);
  }
  private void post(String token,String path,String body){try{HttpResponse<String> response=client.send(request(token,path).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Lichess request failed (HTTP "+response.statusCode()+").");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Lichess request interrupted.",e);}catch(IOException e){throw new IllegalStateException("Could not reach Lichess.",e);}}
  private HttpRequest.Builder request(String token,String path){return HttpRequest.newBuilder(URI.create(API+path)).timeout(TIMEOUT).header("Accept","application/x-ndjson, application/json").header("Authorization","Bearer "+required(token));}
  private static String required(String token){String v=Objects.requireNonNull(token).trim();if(v.isEmpty())throw new IllegalArgumentException("Lichess bot token must not be blank");return v;}
}
