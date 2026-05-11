# Golden Fixtures — NextiaMG (Mappings Generation)

## What this module does

NextiaMG takes an integrated graph (output of NextiaDI) and generates R2RML mappings
that describe how to transform local data sources into the global schema.

## Fixture structure (to be captured after nextiadi fixtures exist)

```
nextiamg/
  inputs/
    scenario_A/
      integrated.nt            — from nextiadi/expected/scenario_A_integrated.nt
      global_graph.nt          — the derived global schema
      alignments.json          — the alignments used during integration
  expected/
    scenario_A_mappings.ttl    — R2RML mapping document (sorted Turtle triples as N-Triples)
```

## How to capture

```bash
make test-golden-update
```

NextiaMG fixtures are captured last because they depend on NextiaDI outputs.
The Makefile runs the full pipeline: NextiaBS → NextiaDI → NextiaMG.

## Entry point

`backend/Modules/NextiaMG/src/main/java/edu/upc/essi/dtim/NextiaMG/Main.java`

## Phase note

Golden test code: `backend/Modules/NextiaMG/src/test/java/edu/upc/essi/dtim/NextiaMG/golden/NextiaMGGoldenTest.java`
(to be created during Phase 0 capture run once nextiadi fixtures are available)
