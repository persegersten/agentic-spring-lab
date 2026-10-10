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
import se.segersten.wreckage.game.domain.GameStatus;
import se.segersten.wreckage.game.domain.MovementOrder;
import se.segersten.wreckage.game.domain.PlayerProgram;
import se.segersten.wreckage.game.domain.Round;
import se.segersten.wreckage.game.domain.RoundPhase;
import se.segersten.wreckage.game.domain.VehicleState;
import se.segersten.wreckage.game.domain.VehicleStatus;
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
        assertThat(game.path("configuration").path("maxPlayers").asInt()).isEqualTo(9);
        assertThat(game.path("configuration").path("programSize").asInt()).isEqualTo(5);
        assertThat(game.path("configuration").path("roundLimit").asInt()).isEqualTo(6);
        assertThat(game.path("configuration").path("checkpointScore").asInt()).isEqualTo(2);
        assertThat(game.path("configuration").path("controlPointScore").asInt()).isEqualTo(1);
        assertThat(game.path("configuration").path("crashPenalty").asInt()).isEqualTo(-1);
        assertThat(game.path("configuration").path("pushCrashScore").asInt()).isEqualTo(1);
        assertThat(game.path("configuration").path("weaponCrashScore").asInt()).isEqualTo(1);
        assertThat(game.path("hostToken").asText()).isNotBlank();
        assertThat(game.path("board").path("width").asInt()).isEqualTo(16);
        assertThat(game.path("board").path("height").asInt()).isEqualTo(16);
        assertThat(game.path("board").path("mapId").asText()).isEqualTo("default-large");
        assertThat(game.path("board").path("walls")).isEmpty();
        assertThat(game.path("board").path("pits")).extracting(
                node -> List.of(node.path("x").asInt(), node.path("y").asInt()))
                .containsExactlyInAnyOrder(List.of(7, 11), List.of(8, 11));
        assertThat(game.path("board").path("checkpoints")).hasSize(4);
        assertThat(game.path("board").path("checkpoints").path(0).path("id").asText())
                .isEqualTo("CP1");
        assertThat(game.path("board").path("checkpoints")).extracting(node -> node.path("order").asInt())
                .containsExactly(1, 2, 3, 4);
        assertThat(game.path("board").path("controlPoints")).hasSize(1);
        assertThat(game.path("board").path("controlPoints").path(0).path("x").asInt()).isEqualTo(8);
        assertThat(game.path("board").path("controlPoints").path(0).path("y").asInt()).isEqualTo(8);

        JsonNode retrieved = json(get("/games/" + game.path("id").asText()));
        assertThat(retrieved.has("hostToken")).isFalse();
        assertThat(retrieved.path("board").path("walls")).isEqualTo(game.path("board").path("walls"));
    }

    @Test
    void createGameWithConfigurationAndRetrieveIt() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":4,"joinTimeoutSeconds":90,"programSize":5,"planningTimeoutSeconds":45,"controlPointScore":4}
                """);
        assertThat(created.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode createdGame = json(created);

        HttpResponse<String> retrieved = get("/games/" + createdGame.path("id").asText());

        JsonNode configuration = json(retrieved).path("configuration");
        assertThat(configuration.path("maxPlayers").asInt()).isEqualTo(4);
        assertThat(configuration.path("joinTimeoutSeconds").asInt()).isEqualTo(90);
        assertThat(configuration.path("programSize").asInt()).isEqualTo(5);
        assertThat(configuration.path("planningTimeoutSeconds").asInt()).isEqualTo(45);
        assertThat(configuration.path("controlPointScore").asInt()).isEqualTo(4);
    }

    @Test
    void rejectInvalidGameConfiguration() throws Exception {
        HttpResponse<String> response = post("/games", """
                {"maxPlayers":4,"joinTimeoutSeconds":90,"programSize":11,"planningTimeoutSeconds":45}
                """);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(response).path("message").asText()).contains("programSize");
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
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}");

        HttpResponse<String> response = post("/games/%s/players".formatted(gameId),
                "{\"name\":\"Carol\"}");

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(response).path("message").asText()).isEqualTo("The lobby is full");
    }

    @Test
    void authenticatedPlayerAddsBotsToTheCurrentLobby() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":3,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));

        HttpResponse<String> first = postBot(gameId, alice);
        HttpResponse<String> second = postBot(gameId, alice);

        assertThat(first.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(json(first).path("name").asText()).isEqualTo("Bot 1");
        assertThat(json(first).path("automated").asBoolean()).isTrue();
        assertThat(json(second).path("name").asText()).isEqualTo("Bot 2");
        JsonNode game = json(get("/games/" + gameId));
        assertThat(game.path("players")).extracting(player -> player.path("name").asText())
                .containsExactly("Alice", "Bot 1", "Bot 2");
        assertThat(game.path("players").path(0).path("automated").asBoolean()).isFalse();
        assertThat(game.path("players").path(1).path("automated").asBoolean()).isTrue();
        assertThat(game.path("vehicles")).hasSize(3);

        HttpResponse<String> full = postBot(gameId, alice);
        assertThat(full.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(full).path("message").asText()).isEqualTo("The lobby is full");
    }

    @Test
    void botCreationRequiresAValidPlayerFromTheSameGame() throws Exception {
        String firstGameId = createGameId();
        JsonNode alice = json(post("/games/%s/players".formatted(firstGameId), "{\"name\":\"Alice\"}"));
        String secondGameId = createGameId();

        HttpResponse<String> wrongToken = postBot(firstGameId, alice, "wrong");
        assertThat(wrongToken.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());

        HttpResponse<String> wrongGame = postBot(secondGameId, alice);
        assertThat(wrongGame.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(wrongGame).path("message").asText()).isEqualTo("Player is not part of this game");
    }

    @Test
    void cannotAddBotAfterTheGameStarts() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":3,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        postBot(gameId, alice);
        postHost(gameId, json(created).path("hostToken").asText());

        HttpResponse<String> response = postBot(gameId, alice);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json(response).path("message").asText()).isEqualTo("The lobby is closed");
    }

    @Test
    void onlyHostStartsPlanningAndPersistsThePhase() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");

        post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}");
        assertThat(json(get("/games/" + gameId)).path("status").asText()).isEqualTo("WAITING_FOR_PLAYERS");
        assertThat(postHost(gameId, "wrong-token").statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(postHost(gameId, json(created).path("hostToken").asText()).statusCode()).isEqualTo(200);
        assertThat(post("/games/%s/players".formatted(gameId), "{\"name\":\"Carol\"}").statusCode())
                .isEqualTo(HttpStatus.CONFLICT.value());
        HttpResponse<String> retrieved = get("/games/" + gameId);

        JsonNode game = json(retrieved);
        assertThat(game.path("status").asText()).isEqualTo("RUNNING");
        assertThat(game.path("board").path("width").asInt()).isEqualTo(10);
        assertThat(game.path("board").path("height").asInt()).isEqualTo(10);
        assertThat(game.path("roundLimit").asInt()).isEqualTo(7);
        assertThat(game.path("configuration").path("roundLimit").asInt()).isEqualTo(7);
        assertThat(game.path("round").path("phase").asText()).isEqualTo("PLANNING");
    }

    @Test
    void returnsTheSameServerOwnedBoardAndVehiclePositionsToEveryPlayer() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        JsonNode bob = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}"));
        postHost(gameId, json(created).path("hostToken").asText());

        JsonNode publicGame = json(get("/games/" + gameId));
        JsonNode aliceGame = json(getPlayerGame(gameId, alice));
        JsonNode bobGame = json(getPlayerGame(gameId, bob));

        assertThat(publicGame.path("round").path("phase").asText()).isEqualTo("PLANNING");
        assertThat(publicGame.path("vehicles")).hasSize(2);
        assertThat(aliceGame.path("board")).isEqualTo(publicGame.path("board"));
        assertThat(bobGame.path("board")).isEqualTo(publicGame.path("board"));
        assertThat(aliceGame.path("vehicles")).isEqualTo(publicGame.path("vehicles"));
        assertThat(bobGame.path("vehicles")).isEqualTo(publicGame.path("vehicles"));
        assertThat(publicGame.path("players").valueStream().map(player -> player.path("score").asInt()))
                .containsOnly(0);
        assertThat(publicGame.path("players").valueStream()
                .map(player -> player.path("visitedCheckpoints").isArray())).containsOnly(true);
        assertThat(aliceGame.path("round").path("state").path("initialScores")
                .path(alice.path("id").asText()).asInt()).isZero();
    }

    @Test
    void keepsFiveCardHandsPrivateAndPublishesReadiness() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":5,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode per = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}"));
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        postHost(gameId, json(created).path("hostToken").asText());

        JsonNode publicGame = json(get("/games/" + gameId));
        JsonNode perGame = json(getPlayerGame(gameId, per));
        JsonNode aliceGame = json(getPlayerGame(gameId, alice));

        assertThat(publicGame.toString()).doesNotContain("\"program\":", "orders");
        assertThat(perGame.path("round").path("hand")).hasSize(8);
        assertThat(aliceGame.path("round").path("hand")).hasSize(8);
        assertThat(perGame.path("round").path("program")).isEmpty();
        assertThat(perGame.path("round").has("state")).isTrue();
        assertThat(perGame.path("round").path("scheduledAction").isNull()).isTrue();
        assertThat(perGame.path("round").size()).isEqualTo(4);

        HttpResponse<String> invented = postPlayer(
                "/games/%s/rounds/current/program".formatted(gameId), per,
                "{\"orders\":[\"WAIT\",\"WAIT\",\"WAIT\",\"WAIT\",\"WAIT\"]}");
        assertThat(invented.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        var selectedOrder = objectMapper.createArrayNode();
        for (int index = 0; index < 5; index++) selectedOrder.add(perGame.path("round").path("hand").get(index));
        HttpResponse<String> submitted = postPlayer(
                "/games/%s/rounds/current/program".formatted(gameId), per,
                objectMapper.createObjectNode()
                        .set("orders", selectedOrder)
                        .toString());
        assertThat(submitted.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(submitted).path("round").path("program")).isEqualTo(selectedOrder);

        JsonNode aliceAfterSubmission = json(getPlayerGame(gameId, alice));
        assertThat(aliceAfterSubmission.path("round").path("state").path("phase").asText())
                .isEqualTo("PLANNING");
        assertThat(aliceAfterSubmission.path("round").path("state").path("ready")
                .path(per.path("id").asText()).asBoolean()).isTrue();
        assertThat(aliceAfterSubmission.path("round").path("program")).isEmpty();
        assertThat(aliceAfterSubmission.toString()).doesNotContain("orders", "playback\":[{");

        var aliceSelectedOrder = objectMapper.createArrayNode();
        for (int index = 0; index < 5; index++) aliceSelectedOrder.add(aliceGame.path("round").path("hand").get(index));
        HttpResponse<String> resolved = postPlayer(
                "/games/%s/rounds/current/program".formatted(gameId), alice,
                objectMapper.createObjectNode()
                        .set("orders", aliceSelectedOrder)
                        .toString());

        assertThat(resolved.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(resolved).path("round").path("state").path("phase").asText())
                .isEqualTo("PLAYBACK");
        JsonNode resolvedPlayback = json(resolved).path("round").path("state").path("playback");
        assertThat(resolvedPlayback.isArray()).isTrue();
        assertThat(json(resolved).path("round").path("state").path("initiative").valueStream()
                .map(JsonNode::asText)).containsExactly(per.path("id").asText(), alice.path("id").asText());
        JsonNode publicResolved = json(get("/games/" + gameId));
        assertThat(publicResolved.path("round").path("phase").asText()).isEqualTo("PLAYBACK");
        assertThat(publicResolved.path("round").path("playback")).isEqualTo(resolvedPlayback);
        JsonNode aliceResolved = json(getPlayerGame(gameId, per));
        assertThat(aliceResolved.path("round").path("state").path("playback"))
                .isEqualTo(resolvedPlayback);
        assertThat(publicResolved.toString()).doesNotContain("\"program\":", "orders");

        HttpResponse<String> nextRound = postPlayer(
                "/games/%s/rounds?completedRound=1".formatted(gameId), per, "");
        HttpResponse<String> duplicateRequest = postPlayer(
                "/games/%s/rounds?completedRound=1".formatted(gameId), alice, "");

        assertThat(nextRound.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(json(nextRound).path("round").path("state").path("number").asInt()).isEqualTo(2);
        assertThat(json(duplicateRequest).path("round").path("state").path("number").asInt()).isEqualTo(2);
        assertThat(json(duplicateRequest).path("round").path("state").path("phase").asText())
                .isEqualTo("PLANNING");
    }

    @Test
    void persistsAndReturnsLaserProgrammingCardsToTheirOwnerOnly() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":5,"planningTimeoutSeconds":45}
                """);
        UUID gameId = UUID.fromString(json(created).path("id").asText());
        JsonNode per = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}"));
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        UUID perId = UUID.fromString(per.path("id").asText());
        UUID aliceId = UUID.fromString(alice.path("id").asText());
        List<MovementOrder> laserHand = java.util.Collections.nCopies(8, MovementOrder.LASER);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Game current = gameRepository.findById(gameId).orElseThrow();
            Map<UUID, PlayerProgram> programs = new LinkedHashMap<>();
            programs.put(perId, PlayerProgram.dealt(perId, 5, laserHand));
            programs.put(aliceId, PlayerProgram.dealt(aliceId, 5, laserHand));
            Round round = new Round(1, programs, List.of(perId, aliceId),
                    new GameState(current.getBoard(), current.getVehicleStates()));
            Map<UUID, VehicleState> vehicles = current.getVehicleStates().stream().collect(
                    java.util.stream.Collectors.toMap(state -> state.vehicle().playerId(), state -> state,
                            (first, second) -> first, LinkedHashMap::new));
            gameRepository.save(new Game(current.getId(), current.getPlayers(), current.getBoard(),
                    GameStatus.RUNNING, vehicles, round, current.getConfiguration(), current.getCreatedAt(),
                    current.getJoinDeadline(), current.getHostTokenHash()));
        });

        JsonNode ownerView = json(getPlayerGame(gameId.toString(), per));
        HttpResponse<String> submitted = postPlayer("/games/%s/rounds/current/program".formatted(gameId), per,
                "{\"orders\":[\"LASER\",\"LASER\",\"LASER\",\"LASER\",\"LASER\"]}");
        JsonNode reconnectedOwnerView = json(getPlayerGame(gameId.toString(), per));
        JsonNode otherView = json(getPlayerGame(gameId.toString(), alice));
        JsonNode publicView = json(get("/games/" + gameId));

        assertThat(ownerView.path("round").path("hand")).hasSize(8)
                .allSatisfy(card -> assertThat(card.asText()).isEqualTo("LASER"));
        assertThat(submitted.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(reconnectedOwnerView.path("round").path("program")).hasSize(5)
                .allSatisfy(card -> assertThat(card.asText()).isEqualTo("LASER"));
        assertThat(otherView.path("round").path("program")).isEmpty();
        assertThat(publicView.toString()).doesNotContain("\"hand\"", "\"program\"");
    }

    @Test
    void persistsAPlayersPrivatePlanningDraftForReconnect() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode per = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}"));
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");
        postHost(gameId, json(created).path("hostToken").asText());
        JsonNode original = json(getPlayerGame(gameId, per));
        var draft = objectMapper.createArrayNode();
        draft.add("TURN_LEFT"); draft.add("TURN_LEFT");

        HttpResponse<String> saved = putPlayer(
                "/games/%s/rounds/current/program".formatted(gameId), per,
                objectMapper.createObjectNode().set("orders", draft).toString());

        assertThat(saved.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode recovered = json(getPlayerGame(gameId, per));
        assertThat(recovered.path("round").path("program")).isEqualTo(draft);
        assertThat(recovered.path("round").path("state").path("number").asInt()).isEqualTo(1);
        assertThat(recovered.path("board")).isEqualTo(original.path("board"));
        assertThat(recovered.path("round").path("state").path("ready")
                .path(per.path("id").asText()).asBoolean()).isFalse();
        assertThat(json(get("/games/" + gameId)).toString()).doesNotContain("\"program\":", "orders");
    }

    @Test
    void editsPersistsLocksAndKeepsScheduledActionPrivate() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        JsonNode bob = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}"));
        putPlayer("/games/%s/players/%s/loadout".formatted(gameId, alice.path("id").asText()), alice,
                "{\"weapon\":\"REPULSOR\",\"ability\":\"SHIELD\"}");
        postHost(gameId, json(created).path("hostToken").asText());
        String shield = """
                {"orders":[],"scheduledAction":{"actionType":"SHIELD","registerIndex":2}}
                """;

        assertThat(putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, shield).statusCode())
                .isEqualTo(HttpStatus.OK.value());
        JsonNode aliceDraft = json(getPlayerGame(gameId, alice));
        assertThat(aliceDraft.path("round").path("scheduledAction").path("actionType").asText()).isEqualTo("SHIELD");
        assertThat(aliceDraft.path("round").path("scheduledAction").path("registerIndex").asInt()).isEqualTo(2);

        String repulsor = """
                {"orders":[],"scheduledAction":{"actionType":"REPULSOR","registerIndex":3}}
                """;
        putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, repulsor);
        JsonNode changed = json(getPlayerGame(gameId, alice));
        assertThat(changed.path("round").path("scheduledAction").path("actionType").asText()).isEqualTo("REPULSOR");
        assertThat(changed.path("round").path("scheduledAction").path("registerIndex").asInt()).isEqualTo(3);

        putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice,
                "{\"orders\":[],\"scheduledAction\":null}");
        assertThat(json(getPlayerGame(gameId, alice)).path("round").path("scheduledAction").isNull()).isTrue();
        putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, repulsor);

        JsonNode bobView = json(getPlayerGame(gameId, bob));
        JsonNode publicView = json(get("/games/" + gameId));
        assertThat(bobView.path("round").path("scheduledAction").isNull()).isTrue();
        assertThat(bobView.path("round").toString()).doesNotContain("registerIndex");
        assertThat(publicView.path("round").toString()).doesNotContain("scheduledAction", "registerIndex");

        HttpResponse<String> invalid = putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, """
                {"orders":[],"scheduledAction":{"actionType":"ROCKET","registerIndex":4}}
                """);
        assertThat(invalid.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        String lockedPlan = """
                {"orders":["WAIT","WAIT","WAIT"],"scheduledAction":{"actionType":"REPULSOR","registerIndex":3}}
                """;
        assertThat(postPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, lockedPlan).statusCode())
                .isEqualTo(HttpStatus.OK.value());
        assertThat(putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, """
                {"orders":[],"scheduledAction":null}
                """).statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void exposesAuthoritativeLaserDamageAndOrderedPlaybackToEveryPlayer() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":1,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        JsonNode bob = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}"));
        postHost(gameId, json(created).path("hostToken").asText());
        JsonNode started = json(getPlayerGame(gameId, alice));
        assertThat(started.path("board").path("mapId").asText()).isEqualTo("default-small");
        JsonNode aliceStart = started.path("vehicles").valueStream()
                .filter(vehicle -> vehicle.path("playerId").asText().equals(alice.path("id").asText()))
                .findFirst().orElseThrow();
        JsonNode bobStart = started.path("vehicles").valueStream()
                .filter(vehicle -> vehicle.path("playerId").asText().equals(bob.path("id").asText()))
                .findFirst().orElseThrow();
        assertThat(aliceStart.path("x").asInt()).isEqualTo(2);
        assertThat(aliceStart.path("y").asInt()).isZero();
        assertThat(aliceStart.path("direction").asText()).isEqualTo("NORTH");
        assertThat(bobStart.path("x").asInt()).isEqualTo(5);
        assertThat(bobStart.path("y").asInt()).isZero();

        postPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, """
                {"orders":["TURN_RIGHT"],"scheduledAction":{"actionType":"LASER","registerIndex":1}}
                """);
        postPlayer("/games/%s/rounds/current/program".formatted(gameId), bob,
                "{\"orders\":[\"WAIT\"],\"scheduledAction\":null}");

        JsonNode aliceView = json(getPlayerGame(gameId, alice));
        JsonNode bobView = json(getPlayerGame(gameId, bob));
        JsonNode alicePlayback = aliceView.path("round").path("state").path("playback");
        JsonNode bobPlayback = bobView.path("round").path("state").path("playback");
        assertThat(alicePlayback).isEqualTo(bobPlayback);
        assertThat(alicePlayback).extracting(event -> event.path("type").asText())
                .containsSequence("WEAPON_FIRED", "WEAPON_HIT", "DAMAGE_APPLIED");
        JsonNode damage = java.util.stream.StreamSupport.stream(alicePlayback.spliterator(), false)
                .filter(event -> event.path("type").asText().equals("DAMAGE_APPLIED")).findFirst().orElseThrow();
        assertThat(damage.path("playerId").asText()).isEqualTo(bob.path("id").asText());
        assertThat(damage.path("sourcePlayerId").asText()).isEqualTo(alice.path("id").asText());
        assertThat(damage.path("oldDamage").asInt()).isZero();
        assertThat(damage.path("newDamage").asInt()).isEqualTo(1);
        assertThat(java.util.stream.StreamSupport.stream(aliceView.path("vehicles").spliterator(), false)
                .filter(vehicle -> vehicle.path("playerId").asText().equals(bob.path("id").asText()))
                .map(vehicle -> vehicle.path("damage").asInt()).toList()).containsExactly(1);
    }

    @Test
    void persistsRocketConsumptionAndRejectsSchedulingItAgain() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":1,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        JsonNode bob = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}"));
        putPlayer("/games/%s/players/%s/loadout".formatted(gameId, alice.path("id").asText()), alice,
                "{\"weapon\":\"ROCKET\",\"ability\":\"SHIELD\"}");
        postHost(gameId, json(created).path("hostToken").asText());

        postPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, """
                {"orders":["WAIT"],"scheduledAction":{"actionType":"ROCKET","registerIndex":1}}
                """);
        postPlayer("/games/%s/rounds/current/program".formatted(gameId), bob,
                "{\"orders\":[\"WAIT\"],\"scheduledAction\":null}");

        JsonNode resolved = json(getPlayerGame(gameId, alice));
        assertThat(resolved.path("round").path("state").path("playback"))
                .extracting(event -> event.path("type").asText())
                .startsWith("WEAPON_FIRED", "AMMO_CHANGED");
        JsonNode aliceVehicle = java.util.stream.StreamSupport.stream(resolved.path("vehicles").spliterator(), false)
                .filter(vehicle -> vehicle.path("playerId").asText().equals(alice.path("id").asText()))
                .findFirst().orElseThrow();
        assertThat(aliceVehicle.path("rocketAmmo").asInt()).isZero();

        assertThat(postPlayer("/games/%s/rounds?completedRound=1".formatted(gameId), alice, "").statusCode())
                .isEqualTo(HttpStatus.CREATED.value());
        HttpResponse<String> stale = putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice, """
                {"orders":[],"scheduledAction":{"actionType":"ROCKET","registerIndex":1}}
                """);
        assertThat(stale.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
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
    void defaultsEditsPublishesAndLocksLoadoutAndRejectsUnequippedActions() throws Exception {
        HttpResponse<String> created = post("/games", null);
        String gameId = json(created).path("id").asText();
        JsonNode alice = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}"));
        JsonNode bob = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Bob\"}"));

        JsonNode defaults = json(get("/games/" + gameId)).path("vehicles").path(0);
        assertThat(defaults.path("primaryWeapon").asText()).isEqualTo("LASER");
        assertThat(defaults.path("specialAbility").asText()).isEqualTo("SHIELD");

        HttpResponse<String> changedResponse = putPlayer(
                "/games/%s/players/%s/loadout".formatted(gameId, alice.path("id").asText()), alice,
                "{\"weapon\":\"REPULSOR\",\"ability\":\"TURBO\"}");
        assertThat(changedResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
        JsonNode changed = json(getPlayerGame(gameId, bob)).path("vehicles").valueStream()
                .filter(vehicle -> vehicle.path("playerId").asText().equals(alice.path("id").asText()))
                .findFirst().orElseThrow();
        assertThat(changed.path("primaryWeapon").asText()).isEqualTo("REPULSOR");
        assertThat(changed.path("specialAbility").asText()).isEqualTo("TURBO");

        postHost(gameId, json(created).path("hostToken").asText());
        assertThat(putPlayer("/games/%s/rounds/current/program".formatted(gameId), alice,
                "{\"orders\":[],\"scheduledAction\":{\"actionType\":\"LASER\",\"registerIndex\":1}}").statusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(putPlayer("/games/%s/players/%s/loadout".formatted(gameId, alice.path("id").asText()), alice,
                "{\"weapon\":\"ROCKET\",\"ability\":\"ANCHOR\"}").statusCode())
                .isEqualTo(HttpStatus.CONFLICT.value());
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
        assertThat(game.path("vehicles")).allSatisfy(vehicle ->
                assertThat(vehicle.path("status").asText()).isEqualTo("ACTIVE"));
        assertThat(game.path("board").path("width").asInt()).isEqualTo(16);
        assertThat(game.path("board").path("height").asInt()).isEqualTo(16);
        assertThat(game.toString()).doesNotContain("token", "\"program\":");
    }

    @Test
    void exposesPersistedCrashedVehicleStatusWithoutRemovingPlayerAggregate() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":3,"planningTimeoutSeconds":45}
                """);
        String gameId = json(created).path("id").asText();
        JsonNode per = json(post("/games/%s/players".formatted(gameId), "{\"name\":\"Per\"}"));
        post("/games/%s/players".formatted(gameId), "{\"name\":\"Alice\"}");
        UUID perId = UUID.fromString(per.path("id").asText());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Game current = gameRepository.findById(UUID.fromString(gameId)).orElseThrow();
            Map<UUID, VehicleState> vehicles = new LinkedHashMap<>();
            current.getVehicleStates().forEach(state -> vehicles.put(state.vehicle().playerId(),
                    state.vehicle().playerId().equals(perId)
                            ? new VehicleState(state.vehicle(), new se.segersten.wreckage.game.domain.Position(-1, 0),
                                    state.orientation(), VehicleStatus.CRASHED)
                            : state));
            gameRepository.save(new Game(current.getId(), current.getPlayers(), current.getBoard(),
                    current.getStatus(), vehicles, current.getRound(), current.getConfiguration(),
                    current.getCreatedAt(), current.getJoinDeadline()));
        });

        JsonNode game = json(get("/games/" + gameId));

        assertThat(game.path("players")).hasSize(2);
        JsonNode crashed = game.path("vehicles").valueStream()
                .filter(vehicle -> vehicle.path("playerId").asText().equals(perId.toString()))
                .findFirst().orElseThrow();
        assertThat(crashed.path("status").asText()).isEqualTo("CRASHED");
        assertThat(crashed.path("x").asInt()).isEqualTo(-1);
    }

    @Test
    void exposesAuthoritativeFinishedPlacementsAndWinners() throws Exception {
        HttpResponse<String> created = post("/games", """
                {"maxPlayers":2,"joinTimeoutSeconds":90,"programSize":1,"planningTimeoutSeconds":45,"roundLimit":1}
                """);
        UUID gameId = UUID.fromString(json(created).path("id").asText());
        UUID firstId = UUID.fromString(json(post("/games/%s/players".formatted(gameId),
                "{\"name\":\"Per\"}")).path("id").asText());
        UUID secondId = UUID.fromString(json(post("/games/%s/players".formatted(gameId),
                "{\"name\":\"Alice\"}")).path("id").asText());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Game current = gameRepository.findById(gameId).orElseThrow();
            current.requirePlayer(firstId).changeScore(4);
            current.requirePlayer(firstId).visitCheckpoint("CP1");
            current.requirePlayer(secondId).changeScore(4);
            current.requirePlayer(secondId).visitCheckpoint("CP1");
            gameRepository.save(new Game(current.getId(), current.getPlayers(), current.getBoard(),
                    GameStatus.FINISHED, current.getVehicleStates().stream().collect(java.util.stream.Collectors.toMap(
                            state -> state.vehicle().playerId(), state -> state, (a, b) -> a, LinkedHashMap::new)),
                    current.getRound(), current.getConfiguration(), current.getCreatedAt(), current.getJoinDeadline()));
        });

        JsonNode game = json(get("/games/" + gameId));

        assertThat(game.path("placements")).hasSize(2);
        assertThat(game.path("placements")).allSatisfy(result -> {
            assertThat(result.path("score").asInt()).isEqualTo(4);
            assertThat(result.path("checkpointsVisited").asInt()).isEqualTo(1);
            assertThat(result.path("crashes").asInt()).isZero();
        });
        assertThat(game.path("placements")).extracting(result -> result.path("placement").asInt())
                .containsExactly(1, 2);
        assertThat(game.path("placements")).filteredOn(result -> result.path("winner").asBoolean()).hasSize(1);
        assertThat(game.path("players")).allSatisfy(player -> assertThat(player.path("crashes").asInt()).isZero());
        assertThat(game.path("players")).allSatisfy(player -> {
            assertThat(player.path("capturedCheckpoints").asInt()).isEqualTo(1);
            assertThat(player.path("nextCheckpoint").asText()).isEqualTo("CP2");
        });
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
        assertThat(paths.path("/games/{gameId}/bots").has("post")).isTrue();
        assertThat(paths.path("/games/{gameId}/start").has("post")).isTrue();
        assertThat(paths.path("/games/{gameId}").has("get")).isTrue();
        assertThat(paths.path("/games/{gameId}/players/{playerId}").has("get")).isTrue();
        assertThat(paths.path("/games/{gameId}/players/{playerId}/loadout").has("put")).isTrue();
        assertThat(paths.path("/games/{gameId}/rounds").has("post")).isTrue();
        assertThat(paths.path("/games/{gameId}/rounds/current/program").has("post")).isTrue();
        assertThat(paths.path("/games/{gameId}/rounds/current/program").has("put")).isTrue();
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

    private HttpResponse<String> postHost(String gameId, String hostToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri("/games/%s/start".formatted(gameId)))
                .header("X-Host-Token", hostToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
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

    private HttpResponse<String> postBot(String gameId, JsonNode player)
            throws IOException, InterruptedException {
        return postBot(gameId, player, player.path("token").asText());
    }

    private HttpResponse<String> postBot(String gameId, JsonNode player, String token)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri("/games/%s/bots".formatted(gameId)))
                .header("X-Player-Id", player.path("id").asText())
                .header("X-Player-Token", token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putPlayer(String path, JsonNode player, String body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-Player-Id", player.path("id").asText())
                .header("X-Player-Token", player.path("token").asText())
                .PUT(HttpRequest.BodyPublishers.ofString(body))
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
