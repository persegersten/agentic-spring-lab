package se.segersten.wreckage.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class ResourceMapRepositoryTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MapValidator validator = new MapValidator();

    @Test void loadsAndSelectsAllDefaultConfigurations() {
        ResourceMapRepository maps = new ResourceMapRepository(validator);
        assertThat(maps.defaultFor(2).id()).isEqualTo("default-small");
        assertThat(maps.defaultFor(4).id()).isEqualTo("default-medium");
        assertThat(maps.defaultFor(10).id()).isEqualTo("default-large");
        assertThat(maps.findAll()).hasSize(3).allSatisfy(validator::validate);
    }

    @Test void malformedMapFailsWithResourceName() {
        Resource bad = named("broken.json", "{not json}");
        assertThatThrownBy(() -> new ResourceMapRepository(mapper, validator, new Resource[]{bad}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("broken.json");
    }

    @Test void duplicateMapIdFails() throws Exception {
        byte[] json = new org.springframework.core.io.ClassPathResource("maps/default-small.json")
                .getInputStream().readAllBytes();
        assertThatThrownBy(() -> new ResourceMapRepository(mapper, validator,
                new Resource[]{named("one.json", new String(json, StandardCharsets.UTF_8)),
                        named("two.json", new String(json, StandardCharsets.UTF_8))}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate map id");
    }

    private Resource named(String name, String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8), name) {
            @Override public String getDescription() { return name; }
        };
    }
}
