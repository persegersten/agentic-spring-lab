package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import se.segersten.wreckage.TestcontainersConfiguration;
import se.segersten.wreckage.game.domain.Game;
import se.segersten.wreckage.game.domain.GameRepository;
import se.segersten.wreckage.game.domain.GameState;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.VehicleState;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GameApiIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createGame() throws Exception {
        HttpResponse<String> response = post("/games", null);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode game = json(response);
        assertThat(game.path("id").asText()).isNotBlank();
        assertThat(game.path("players").isEmpty()).isTrue();
        assertThat(game.path("status").asText()).isEqualTo("WAITING_FOR_PLAYERS");
        assertThat(game.path("configuration").path("maxPlayers").asInt()).isEqualTo(12);
        assertThat(game.path("configuration").path("cardsPerRound").asInt()).isEqualTo(3);
        assertThat(game.path("joinDeadline").asText()).isNotBlank();
        assertThat(game.path("board").path("width").asInt()).isEqualTo(20);
        assertThat(game.path("board").path("height").asInt()).isEqualTo(20);
        assertThat(game.path("board").path("pits")).hasSize(1);
        assertThat(game.path("board").path("pits").path(0).path("x").asInt()).isEqualTo(4);
        assertThat(game.path("board").path("pits").path(0).path("y").asInt()).isEqualTo(5);
    }

    @Test
    void createGameWithConfigurationAndRetrieveIt() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":4,"joinTimeoutSeconds":90,"cardsPerRound":10,"planningTimeoutSeconds":45}
                """);
        assertThat(created.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode createdGame = json(created);

        HttpResponse<String> retrieved = get("/games/" + createdGame.path("id").asText());

        JsonNode configuration = json(retrieved).path("configuration");
        assertThat(configuration.path("maxPlayers").asInt()).isEqualTo(4);
        assertThat(configuration.path("joinTimeoutSeconds").asInt()).isEqualTo(90);
        assertThat(configuration.path("cardsPerRound").asInt()).isEqualTo(10);
        assertThat(configuration.path("planningTimeoutSeconds").asInt()).isEqualTo(45);
    }

    @Test
    void rejectInvalidGameConfiguration() throws Exception {
        HttpResponse<String> response = post("/games", """
                {"maxPlayers":4,"joinTimeoutSeconds":90,"cardsPerRound":11,"planningTimeoutSeconds":45}
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(response).path("message").asText()).contains("cardsPerRound");
    }

    @Test
    void rejectDuplicateNickname() throws Exception {
        String gameId = createGameId();
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");

        HttpResponse<String> response = post("/games/%s/players".formatted(gameId),
                "{\"name\":\" Alice \"}");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(response).path("message").asText()).isEqualTo("Nickname is already in use");
    }

    @Test
    void rejectPlayerWhenLobbyIsFull() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":1,"joinTimeoutSeconds":90,"cardsPerRound":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");

        HttpResponse<String> response = post("/games/%s/players".formatted(gameId),
                "{\"name\":\"Bob\"}");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(response).path("message").asText()).isEqualTo("The lobby is full");
    }

    @Test
    void startsPlanningWhenLobbyBecomesFullAndPersistsThePhase() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"cardsPerRound":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");

        post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}");
        HttpResponse<String> retrieved = get("/games/" + gameId);

        JsonNode game = json(retrieved);
        assertThat(game.path("status").asText()).isEqualTo("RUNNING");
        assertThat(game.path("round").path("phase").asText()).isEqualTo("PLANNING");
    }

    @Test
    void returnsTheSameServerOwnedBoardAndVehiclePositionsToEveryPlayer() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"cardsPerRound":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        JsonNode bob = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}"));

        JsonNode publicGame = json(get("/games/" + gameId));
        JsonNode aliceGame = json(getPlayerGame(gameId, alice));
        JsonNode bobGame = json(getPlayerGame(gameId, bob));

        assertThat(publicGame.path("round").path("phase").asText()).isEqualTo("PLANNING");
        assertThat(publicGame.path("vehicles")).hasSize(2);
        assertThat(aliceGame.path("board")).isEqualTo(publicGame.path("board"));
        assertThat(bobGame.path("board")).isEqualTo(publicGame.path("board"));
        assertThat(aliceGame.path("vehicles")).isEqualTo(publicGame.path("vehicles"));
        assertThat(bobGame.path("vehicles")).isEqualTo(publicGame.path("vehicles"));
    }

    @Test
    void keepsFiveCardHandsPrivateAndPublishesReadiness() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"cardsPerRound":5,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode per = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}"));
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));

        JsonNode publicGame = json(get("/games/" + gameId));
        JsonNode perGame = json(getPlayerGame(gameId, per));
        JsonNode aliceGame = json(getPlayerGame(gameId, alice));

        assertThat(publicGame.toString()).doesNotContain("hand", "orders");
        assertThat(perGame.path("round").path("hand")).hasSize(5);
        assertThat(aliceGame.path("round").path("hand")).hasSize(5);
        assertThat(perGame.path("round").has("state")).isTrue();
        assertThat(perGame.path("round").size()).isEqualTo(2);

        var selectedOrder = objectMapper.createArrayNode();
        for (int index = 4; index >= 0; index--) {
            selectedOrder.add(perGame.path("round").path("hand").path(index).asText());
        }
        HttpResponse<String> submitted = postPlayer(
                "/games/%s/rounds/current/program".formatted(gameId), per,
                objectMapper.createObjectNode()
                        .set("orders", selectedOrder)
                        .toString());
        assertThat(submitted.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(submitted).path("round").path("hand")).isEqualTo(selectedOrder);

        JsonNode aliceAfterSubmission = json(getPlayerGame(gameId, alice));
        assertThat(aliceAfterSubmission.path("round").path("state").path("phase").asText())
                .isEqualTo("PLANNING");
        assertThat(aliceAfterSubmission.path("round").path("state").path("ready")
                .path(per.path("id").asText()).asBoolean()).isTrue();
        assertThat(aliceAfterSubmission.path("round").path("hand")).hasSize(5);
        assertThat(aliceAfterSubmission.toString()).doesNotContain("orders", "playback\":[{");

        HttpResponse<String> resolved = postPlayer(
                "/games/%s/rounds/current/program".formatted(gameId), alice,
                objectMapper.createObjectNode()
                        .set("orders", aliceGame.path("round").path("hand"))
                        .toString());

        assertThat(resolved.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(resolved).path("round").path("state").path("phase").asText())
                .isEqualTo("PLAYBACK");
        JsonNode resolvedPlayback = json(resolved).path("round").path("state").path("playback");
        assertThat(resolvedPlayback.isArray()).isTrue();
        assertThat(resolvedPlayback.valueStream().map(event -> event.path("type").asText()))
                .contains("FIRE");
        JsonNode publicResolved = json(get("/games/" + gameId));
        assertThat(publicResolved.path("round").path("phase").asText()).isEqualTo("PLAYBACK");
        assertThat(publicResolved.path("round").path("playback")).isEqualTo(resolvedPlayback);
        JsonNode aliceResolved = json(getPlayerGame(gameId, per));
        assertThat(aliceResolved.path("round").path("state").path("playback"))
                .isEqualTo(resolvedPlayback);
        assertThat(publicResolved.toString()).doesNotContain("hand", "orders");
    }

    @Test
    void returnsMandatoryMalfunctionOnlyToTheDamagedPlayer() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"cardsPerRound":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode per = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}"));
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Game current = transaction.execute(status ->
                gameRepository.findById(UUID.fromString(gameId)).orElseThrow());
        UUID perId = UUID.fromString(per.path("id").asText());
        List<VehicleState> damagedStates = current.getVehicleStates().stream()
                .map(state -> state.vehicle().playerId().equals(perId)
                        ? new VehicleState(state.vehicle(), state.position(), state.orientation(), 1)
                        : state)
                .toList();
        Map<UUID, VehicleState> vehicles = new LinkedHashMap<>();
        damagedStates.forEach(state -> vehicles.put(state.vehicle().playerId(), state));
        Round completedRound = new Round(current.getRound().number(), RoundPhase.PLAYBACK,
                current.getRound().programs(), new GameState(current.getBoard(), damagedStates), List.of());
        transaction.executeWithoutResult(status -> gameRepository.save(new Game(current.getId(),
                current.getPlayers(), current.getBoard(), current.getStatus(), vehicles, completedRound,
                current.getConfiguration(), current.getCreatedAt(), current.getJoinDeadline())));

        HttpResponse<String> nextRound = postPlayer("/games/%s/rounds".formatted(gameId), per, "");

        assertThat(nextRound.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode perGame = json(nextRound);
        JsonNode aliceGame = json(getPlayerGame(gameId, alice));
        JsonNode publicGame = json(get("/games/" + gameId));
        assertThat(perGame.path("round").path("hand").valueStream()
                .map(JsonNode::asText)).contains("MALFUNCTION_REVERSE");
        assertThat(aliceGame.toString()).doesNotContain("MALFUNCTION_REVERSE");
        assertThat(publicGame.toString()).doesNotContain("MALFUNCTION_REVERSE", "hand", "orders");
    }

    @Test
    void addPlayer() throws Exception {
        String gameId = createGameId();

        HttpResponse<String> response = post(
                "/games/%s/players".formatted(gameId),
                "{\"name\":\" Per \"}");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode player = json(response);
        assertThat(player.path("id").asText()).isNotBlank();
        assertThat(player.path("name").asText()).isEqualTo("Per");
        assertThat(player.path("token").asText()).isNotBlank();
    }

    @Test
    void getGame() throws Exception {
        String gameId = createGameId();
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}");
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Ulrika\"}");

        HttpResponse<String> response = get("/games/" + gameId);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode game = json(response);
        assertThat(game.path("id").asText()).isEqualTo(gameId);
        assertThat(game.path("players").size()).isEqualTo(2);
        assertThat(game.path("players").path(0).path("name").asText()).isEqualTo("Per");
        assertThat(game.path("players").path(1).path("name").asText()).isEqualTo("Ulrika");
        assertThat(game.path("board").path("width").asInt()).isEqualTo(20);
        assertThat(game.path("board").path("height").asInt()).isEqualTo(20);
        assertThat(game.toString()).doesNotContain("token", "hand");
    }

    @Test
    void getRunningGames() throws Exception {
        String gameId = createGameId();

        HttpResponse<String> response = get("/games/running");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode games = json(response);
        assertThat(games.isArray()).isTrue();
        assertThat(games.valueStream()
                .map(game -> game.path("id").asText()))
                .contains(gameId);
    }

    @Test
    void getFinishedGamesReturnsEmptyListWhenNoneExist() throws Exception {
        HttpResponse<String> response = get("/games/finished");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode games = json(response);
        assertThat(games.isArray()).isTrue();
        assertThat(games.isEmpty()).isTrue();
    }

    @Test
    void cannotAddPlayerToMissingGame() throws Exception {
        UUID missingGameId = UUID.randomUUID();

        HttpResponse<String> response = post(
                "/games/%s/players".formatted(missingGameId),
                "{\"name\":\"Per\"}");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(json(response).path("message").asText())
                .isEqualTo("Game not found: " + missingGameId);
    }

    @Test
    void publishesOpenApiDocumentationForGameEndpoints() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode paths = json(response).path("paths");
        assertThat(paths.path("/games").has("post")).isTrue();
        assertThat(paths.path("/games/configuration/defaults").has("get")).isTrue();
        assertThat(paths.path("/games/{gameId}/players").has("post")).isTrue();
        assertThat(paths.path("/games/{gameId}").has("get")).isTrue();
        assertThat(paths.path("/games/{gameId}/players/{playerId}").has("get")).isTrue();
        assertThat(paths.path("/games/{gameId}/rounds").has("post")).isTrue();
        assertThat(paths.path("/games/{gameId}/rounds/current/program").has("post")).isTrue();
        assertThat(paths.path("/games/running").has("get")).isTrue();
        assertThat(paths.path("/games/finished").has("get")).isTrue();
    }

    @Test
    void exposesSwaggerUi() throws Exception {
        HttpResponse<String> response = get("/swagger-ui.html");

        assertThat(response.statusCode()).isBetween(300, 399);
        assertThat(response.headers().firstValue("location"))
                .hasValueSatisfying(location -> assertThat(location).contains("/swagger-ui/index.html"));
    }

    private String createGameId() throws Exception {
        HttpResponse<String> response = post("/games", null);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        return json(response).path("id").asText();
    }

    private HttpResponse<String> post(String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(publisher)
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getPlayerGame(String gameId, JsonNode player)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri("/games/%s/players/%s".formatted(
                        gameId, player.path("id").asText())))
                .header("X-Player-Token", player.path("token").asText())
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postPlayer(String path, JsonNode player, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-Player-Id", player.path("id").asText())
                .header("X-Player-Token", player.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return objectMapper.readTree(response.body());
    }
}
