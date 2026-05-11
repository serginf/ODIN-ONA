# Golden Fixtures — NextiaJD (Join Discovery)

## What this module does

NextiaJD discovers join-able attribute pairs between datasets by computing
statistical profiles for each column and calculating distances between profiles.
Given two CSV datasets, it returns a ranked list of candidate alignments.

## Fixture structure (to be captured in Phase 0 capture run)

```
nextiajd/
  inputs/
    employees.csv       — copy from nextiabs/inputs/
    orders.csv          — copy from nextiabs/inputs/ (shares customer_id with employees.id)
    products.csv        — copy from nextiabs/inputs/
  profiles/
    employees.json      — DuckDB-computed column profiles
    orders.json
    products.json
  expected/
    employees_orders_alignments.json   — sorted JSON list of discovered alignments
    employees_products_alignments.json
```

## Profile generation

Profiles are computed by NextiaJD's `Profile` class using DuckDB:
```java
Profile p = new Profile(DuckDB.getConnection());
p.createProfile(csvPath, profileOutputPath);
```

## How to capture

```bash
make test-golden-update
```

The Makefile target runs profile generation and then alignment discovery for each
dataset pair, writing sorted JSON to `nextiajd/expected/`.

## Determinism note

Discovery distances use floating-point arithmetic. Golden output comparison uses
a tolerance of ±1e-9 on numeric fields. The ranked ordering is the stable contract;
exact distance values are informational.

## Phase note

Golden test code: `backend/Modules/NextiaJD/src/test/java/edu/upc/essi/dtim/NextiaJD/golden/NextiaJDGoldenTest.java`
(to be created during Phase 0 capture run)
