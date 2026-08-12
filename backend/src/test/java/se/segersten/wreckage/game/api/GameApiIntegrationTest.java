package se.segersten.wreckage.game.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import se.segersten.wreckage.TestcontainersConfiguration;
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

    @Test
    void createGame() throws Exception {
        HttpResponse<String> response = post("/games", null);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        JsonNode game = json(response);
        assertThat(game.path("id").asText()).isNotBlank();
        assertThat(game.path("players").isEmpty()).isTrue();
        assertThat(game.path("board").path("width").asInt()).isEqualTo(20);
        assertThat(game.path("board").path("height").asInt()).isEqualTo(20);
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

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(port, path));
    }

    private JsonNode json(HttpResponse<String> response) throws IOException {
        return objectMapper.readTree(response.body());
    }
}
