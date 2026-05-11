package edu.upc.essi.dtim.nextiabs.golden;

import edu.upc.essi.dtim.NextiaCore.graph.Graph;
import edu.upc.essi.dtim.nextiabs.implementations.CSVBootstrap;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden-fixture tests for NextiaBS CSV schema extraction.
 *
 * Normal run (make test-golden):
 *   Reads each input CSV from tests/fixtures/golden/nextiabs/inputs/,
 *   runs CSVBootstrap, serialises the resulting graph as sorted N-Triples,
 *   and asserts byte-equality with the stored expected file.
 *
 * Capture run (make test-golden-update):
 *   Same as above but WRITES the expected file instead of comparing.
 *   Run this once after any intentional change to CSVBootstrap output.
 *
 * System properties consumed:
 *   test.golden.dir  — absolute path to tests/fixtures/golden/ (required)
 *   update.golden    — if "true", regenerates expected files instead of comparing
 */
@Tag("golden")
class NextiaBSGoldenTest {

    private static final String[] FIXTURES = {
        "employees",
        "products",
        "orders",
        "cities",
        "sensors"
    };

    @ParameterizedTest(name = "nextiabs/{0}.csv")
    @ValueSource(strings = {"employees", "products", "orders", "cities", "sensors"})
    void csv_bootstrap_schema_matches_golden(String fixture) throws IOException {
        String goldenDir = System.getProperty("test.golden.dir");
        assumeTrue(goldenDir != null,
                "Skipping golden test: set -Dtest.golden.dir=<repo-root>/tests/fixtures/golden to run");

        Path inputPath = Paths.get(goldenDir, "nextiabs", "inputs", fixture + ".csv");
        Path expectedPath = Paths.get(goldenDir, "nextiabs", "expected", fixture + ".nt");

        assumeTrue(Files.exists(inputPath),
                "Input file missing: " + inputPath);

        String actual = bootstrapToSortedNTriples(fixture, inputPath.toString());

        if ("true".equals(System.getProperty("update.golden"))) {
            Files.createDirectories(expectedPath.getParent());
            Files.writeString(expectedPath, actual, StandardCharsets.UTF_8);
            System.out.println("Updated golden: " + expectedPath);
            return;
        }

        assumeTrue(Files.exists(expectedPath),
                "Golden file missing — run 'make test-golden-update' to generate: " + expectedPath);

        String expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        assertEquals(expected, actual,
                "Golden mismatch for " + fixture + ". If the change is intentional run 'make test-golden-update'.");
    }

    private String bootstrapToSortedNTriples(String id, String csvPath) {
        CSVBootstrap bootstrap = new CSVBootstrap(id, id, csvPath);
        Graph graph = bootstrap.bootstrapSchema(false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, graph.getGraph(), Lang.NTRIPLES);

        // Sort lines so output is deterministic regardless of Jena's internal ordering
        String[] lines = out.toString(StandardCharsets.UTF_8).split("\n");
        return Arrays.stream(lines)
                .filter(l -> !l.isBlank())
                .sorted()
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
