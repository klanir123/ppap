# msg CX PPAP Connector – developer commands. Run `make help`.
#
#   make up                 build + start OEM and Tier-1 (incl. EDCs, Keycloak, storage)
#   make up PROFILE=tier2   additionally start Tier-2
#   make demo               reset application data and reload seed data
#   make test               backend + frontend tests;  make smoke  EDC end-to-end check

SHELL        := /bin/bash
PROFILE      ?=
COMPOSE      := docker compose -f infra/compose.yaml --env-file $(if $(wildcard infra/.env),infra/.env,infra/.env.example) $(if $(PROFILE),--profile $(PROFILE),)
# Java 25 is required (enforcer). Override with JDK25_HOME=... if your default JAVA_HOME is older.
JDK25_HOME   ?= $(if $(wildcard /opt/jdk-25),/opt/jdk-25,$(JAVA_HOME))
MVN          := JAVA_HOME=$(JDK25_HOME) ./mvnw -B
IMAGE        ?= ppap-connector:local
APPS         := oem-app tier1-app $(if $(filter tier2,$(PROFILE)),tier2-app,)
SKIP_BUILD   ?=

.PHONY: help up down clean restart ps logs build build-frontend build-backend image test test-backend test-frontend smoke demo realms stats wait seed-check

help: ## show this help
	@grep -hE '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-16s\033[0m %s\n",$$1,$$2}'

up: $(if $(SKIP_BUILD),,build) ## build and start the dataspace (PROFILE=tier2 for Tier-2)
	$(COMPOSE) up -d
	@$(MAKE) --no-print-directory wait PROFILE=$(PROFILE)
	@echo ""
	@echo "  OEM     http://localhost:8081   (oem.quality / demo)"
	@echo "  Tier-1  http://localhost:8082   (tier1.quality / demo)"
	@$(if $(filter tier2,$(PROFILE)),echo "  Tier-2  http://localhost:8083   (tier2.quality / demo)",true)
	@echo "  Keycloak admin http://localhost:8180 (admin / admin)"

wait: ## wait until all application containers are healthy
	@for app in $(APPS); do \
	  printf "waiting for %s " $$app; \
	  for i in $$(seq 1 90); do \
	    s=$$($(COMPOSE) ps --format '{{.Health}}' $$app 2>/dev/null); \
	    [ "$$s" = "healthy" ] && { echo "✓"; break; }; printf "."; sleep 2; \
	    [ $$i = 90 ] && { echo " ✗ (see: make logs)"; exit 1; }; \
	  done; \
	done

down: ## stop all containers (data is kept)
	$(COMPOSE) --profile tier2 down

clean: ## stop and delete all containers AND volumes
	$(COMPOSE) --profile tier2 down -v

restart: ## restart application containers (after `make build`)
	$(COMPOSE) up -d --force-recreate $(APPS)
	@$(MAKE) --no-print-directory wait PROFILE=$(PROFILE)

ps: ## container status
	$(COMPOSE) ps -a --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'

logs: ## follow logs (SERVICE=oem-app to filter)
	$(COMPOSE) logs -f --tail=200 $(SERVICE)

build: build-frontend build-backend image ## build frontend, backend jar and container image

build-frontend:
	cd frontend && npm ci --no-audit --no-fund && npm run build

build-backend:
	$(MVN) -q -DskipTests package

image:
	docker build -t $(IMAGE) .

test: seed-check test-backend test-frontend ## run all tests

test-backend:
	$(MVN) verify

test-frontend:
	cd frontend && npm run lint && npm run typecheck && npm run test && npm run api:check

smoke: ## EDC end-to-end check: catalog, negotiation, EDR, data plane (needs `make up`)
	python3 infra/scripts/edc-smoke.py --provider tier1 --consumer oem
	$(if $(filter tier2,$(PROFILE)),python3 infra/scripts/edc-smoke.py --provider tier2 --consumer tier1,)

demo: ## reset application databases to the demo state (EDC/Keycloak untouched)
	$(COMPOSE) stop $(APPS)
	@for p in oem tier1 tier2; do \
	  $(COMPOSE) exec -T postgres psql -q -U postgres -c "DROP DATABASE IF EXISTS $${p}_app WITH (FORCE);" -c "CREATE DATABASE $${p}_app OWNER app_$${p};"; \
	done
	$(COMPOSE) up -d $(APPS)
	@$(MAKE) --no-print-directory wait PROFILE=$(PROFILE)
	@echo "demo state restored (seed data reloaded)"

seed-check: ## verify seed data (generators up to date, mirrored mappings, integrity)
	python3 seed/check_seed.py

realms: ## regenerate Keycloak realm imports
	python3 infra/keycloak/generate-realms.py

stats: ## memory usage per container
	docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
