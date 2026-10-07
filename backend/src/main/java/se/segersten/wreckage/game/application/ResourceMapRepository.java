package se.segersten.wreckage.game.application;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import se.segersten.wreckage.game.domain.MapDefinition;

@Component
public class ResourceMapRepository implements MapRepository {
    private final Map<String, MapDefinition> maps;

    @Autowired
    public ResourceMapRepository(MapValidator validator) {
        this(new ObjectMapper().findAndRegisterModules(), validator, loadResources());
    }
    ResourceMapRepository(ObjectMapper mapper, MapValidator validator, Resource[] resources) {
        Map<String, MapDefinition> loaded = new LinkedHashMap<>();
        for (Resource resource : resources) {
            try {
                MapDefinition map = mapper.readValue(resource.getInputStream(), MapDefinition.class);
                validator.validate(map);
                if (loaded.putIfAbsent(map.id(), map) != null)
                    throw new IllegalArgumentException("Duplicate map id '" + map.id() + "'");
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load map resource " + resource.getDescription()
                        + ": " + exception.getMessage(), exception);
            }
        }
        if (loaded.isEmpty()) throw new IllegalStateException("No map resources found");
        maps = Map.copyOf(loaded);
    }
    private static Resource[] loadResources() {
        try { return new PathMatchingResourcePatternResolver().getResources("classpath*:maps/*.json"); }
        catch (IOException exception) { throw new IllegalStateException("Could not scan map resources", exception); }
    }
    @Override public MapDefinition get(String id) {
        MapDefinition map = maps.get(id);
        if (map == null) throw new IllegalArgumentException("Unknown map id '" + id + "'");
        return map;
    }
    @Override public List<MapDefinition> findAll() { return List.copyOf(maps.values()); }
}
