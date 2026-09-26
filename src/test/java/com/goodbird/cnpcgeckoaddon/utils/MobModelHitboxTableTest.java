package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.util.TsvResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobModelHitboxTableTest {

    private static final String NAMESPACE = "cnpcgeckoaddon:";
    // Only the addon's own namespace is checked: the donor bundles are gitignored, so a
    // clean clone and CI have this folder and nothing else under assets/.
    private static final Path OWN_ASSETS = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "resources", "assets", "cnpcgeckoaddon"));

    @Test
    @DisplayName("every model the addon ships in its own namespace has a hitbox row")
    void everyOwnModelIsSized() throws IOException {
        List<Path> models;
        try (Stream<Path> files = Files.walk(OWN_ASSETS.resolve("geo"))) {
            models = files.filter(path -> path.toString().endsWith(".geo.json")).toList();
        }
        assertFalse(models.isEmpty(), "found no .geo.json under " + OWN_ASSETS);

        Set<String> unsized = new TreeSet<>();
        for (Path model : models) {
            String id = NAMESPACE + OWN_ASSETS.relativize(model).toString().replace('\\', '/');
            if (!MobModelHitboxResolver.isKnown(id)) {
                unsized.add(id);
            }
        }
        assertTrue(unsized.isEmpty(), "these models have no row in META-INF/MOBMODEL_HITBOXES.tsv, "
                + "so an npc wearing one keeps its old box - run scripts/generate_mob_hitboxes.py: " + unsized);
    }

    @Test
    @DisplayName("every hitbox row of the addon's own namespace names a model it ships")
    void everyOwnRowHasItsModel() {
        Set<String> orphaned = new TreeSet<>();
        for (TsvResource.Row row : TsvResource.read("/META-INF/MOBMODEL_HITBOXES.tsv")) {
            String model = row.key();
            if (model.startsWith(NAMESPACE)
                    && !Files.isRegularFile(OWN_ASSETS.resolve(model.substring(NAMESPACE.length())))) {
                orphaned.add(model);
            }
        }
        assertTrue(orphaned.isEmpty(), "these hitbox rows name models the addon does not ship: " + orphaned);
    }
}
