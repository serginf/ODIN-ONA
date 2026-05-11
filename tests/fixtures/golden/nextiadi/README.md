# Golden Fixtures — NextiaDI (Data Integration)

## What this module does

NextiaDI takes two bootstrapped RDF graphs (local schemas) plus a set of alignments
between their attributes, and produces an integrated global graph.

## Fixture structure (to be captured in Phase 0 capture run)

```
nextiadi/
  inputs/
    scenario_A/
      schema_employees.nt      — NextiaBS output for employees.csv
      schema_products.nt       — NextiaBS output for products.csv
      alignments.json          — manually specified attribute correspondences
    scenario_B/
      ...
  expected/
    scenario_A_integrated.nt   — sorted N-Triples of the integrated graph
    scenario_B_integrated.nt
```

## How to capture

Golden outputs for NextiaDI depend on having bootstrapped schemas from NextiaBS.
The `make test-golden-update` target runs NextiaBS first, then feeds the resulting
graphs into NextiaDI's integration pipeline.

Run from repo root:

```bash
make test-golden-update
```

This will:
1. Run NextiaBSGoldenTest in update mode → writes `nextiabs/expected/*.nt`
2. Use those N-Triples as inputs to NextiaDI integration scenarios
3. Write `nextiadi/expected/*.nt`

## Entry point

`backend/Modules/NextiaDI/src/main/java/edu/upc/essi/dtim/NextiaDI.java`

The integration method signature (simplified):
```java
IntegratedGraph integrate(LocalGraph graphA, LocalGraph graphB, List<Alignment> alignments)
```

## Phase note

Golden test code for NextiaDI is introduced once the inputs above are captured
(after `make test-golden-update` from Phase 0). The test class will live at:
`backend/Modules/NextiaDI/src/test/java/edu/upc/essi/dtim/nextiadi/golden/NextiaDIGoldenTest.java`
