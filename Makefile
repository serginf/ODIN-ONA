REPO_ROOT := $(shell pwd)
GOLDEN_DIR := $(REPO_ROOT)/tests/fixtures/golden

# Gradle 8.5 does not support JDK 25+. Pin to JDK 11 if available; fall back to
# whatever JAVA_HOME is set to in the environment (works in CI where JDK 11 is
# explicitly selected by the workflow).
JAVA11     := $(shell /usr/libexec/java_home -v 11 2>/dev/null || echo "")
ifneq ($(JAVA11),)
    export JAVA_HOME := $(JAVA11)
endif

GRADLEW    := cd backend && ./gradlew

.PHONY: build test test-golden test-golden-update docker-up docker-down help

help:
	@echo "Usage:"
	@echo "  make build               Compile all modules and run unit tests (no golden)"
	@echo "  make test                Alias for build"
	@echo "  make test-golden         Run golden-fixture regression tests (read-only)"
	@echo "  make test-golden-update  Regenerate golden expected files from current code"
	@echo "  make docker-up           Start the full stack via docker compose"
	@echo "  make docker-down         Stop and remove containers"

build:
	$(GRADLEW) build

test: build

# ---------------------------------------------------------------------------
# Golden-fixture targets
# ---------------------------------------------------------------------------

# Read-only: compare current output against stored golden files.
# Tests skip (not fail) if no golden dir system property is provided, so
# the first time you run this you must run test-golden-update first.
test-golden:
	$(GRADLEW) :Modules:NextiaBS:goldenTest \
	    -Dtest.golden.dir=$(GOLDEN_DIR)

# Write mode: run NextiaBS bootstrap on every input fixture and store the
# resulting sorted N-Triples as the new canonical golden output.
# Run this once after any intentional change to CSVBootstrap behaviour.
test-golden-update:
	$(GRADLEW) :Modules:NextiaBS:goldenTest \
	    -Dtest.golden.dir=$(GOLDEN_DIR) \
	    -Dupdate.golden=true

# ---------------------------------------------------------------------------
# Docker targets
# ---------------------------------------------------------------------------

docker-up:
	docker compose up --build -d

docker-down:
	docker compose down
