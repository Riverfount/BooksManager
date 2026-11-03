.DEFAULT_GOAL := help

.PHONY: help run test verify image up down clean release

help: ## Lista os alvos disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-10s\033[0m %s\n", $$1, $$2}'

run: ## Sobe a API (sobe o PostgreSQL pelo compose.yaml; Docker precisa estar rodando)
	./mvnw spring-boot:run

test: ## Roda todos os testes
	./mvnw test

verify: ## Build completo com verificações (testes + ArchUnit)
	./mvnw verify

image: ## Constrói a imagem de contêiner da API (booksmanager)
	docker build --tag booksmanager .

up: ## Sobe o PostgreSQL via Docker Compose
	docker compose up -d

down: ## Derruba o PostgreSQL via Docker Compose
	docker compose down

clean: ## Limpa o build do Maven
	./mvnw clean

# make release: creates the GitHub release, which is what runs the Publish image workflow (see docs/implantacao.md, 6.1).
VERSION ?=
NOTES ?=
TITLE ?= Books Manager $(VERSION:v%=%)
PRERELEASE ?=
YES ?=
DRY_RUN ?=
RELEASE_VERSION_RE := ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$$
RELEASE_CMD = gh release create "$(VERSION)" --target main --title "$(TITLE)" --notes-file "$(NOTES)"$(if $(PRERELEASE), --prerelease)

release: ## Cria a release e publica a imagem: make release VERSION=v1.0.0 NOTES=notas.md [TITLE=...] [PRERELEASE=1] [YES=1] [DRY_RUN=1]
	@command -v gh >/dev/null || { echo "Erro: instale o GitHub CLI (gh)."; exit 1; }
	@test -n "$(VERSION)" || { echo "Erro: informe VERSION, por exemplo: make release VERSION=v1.0.0 NOTES=notas.md"; exit 1; }
	@echo "$(VERSION)" | grep -Eq '$(RELEASE_VERSION_RE)' || { echo "Erro: VERSION deve ser como v1.0.0 ou v1.0.0-rc.1 (recebido: $(VERSION))."; exit 1; }
	@test -n "$(NOTES)" || { echo "Erro: informe NOTES, o arquivo Markdown das notas da versão."; exit 1; }
	@test -f "$(NOTES)" || { echo "Erro: o arquivo de notas '$(NOTES)' não existe."; exit 1; }
	@test "$$(git branch --show-current)" = main || { echo "Erro: a release sai da main; você está em '$$(git branch --show-current)'."; exit 1; }
	@test -z "$$(git status --porcelain)" || { echo "Erro: há alterações não commitadas."; exit 1; }
	@git fetch --quiet origin main && test "$$(git rev-parse HEAD)" = "$$(git rev-parse origin/main)" || { echo "Erro: a main local não é igual à origin/main (faça git pull ou git push)."; exit 1; }
	@test -z "$$(git ls-remote --tags origin 'refs/tags/$(VERSION)')" || { echo "Erro: a tag $(VERSION) já existe no repositório remoto."; exit 1; }
	@if gh release view "$(VERSION)" >/dev/null 2>&1; then echo "Erro: a release $(VERSION) já existe."; exit 1; fi
	@echo "Release : $(VERSION)$(if $(PRERELEASE), (pré-versão))"
	@echo "Título  : $(TITLE)"
	@echo "Notas   : $(NOTES)"
	@echo "Commit  : $$(git rev-parse --short HEAD) (main)"
	@echo "Publicar a release dispara o workflow Publish image (imagem $(VERSION:v%=%) no ghcr.io)."
	@if [ -n "$(DRY_RUN)" ]; then echo '[dry-run] $(RELEASE_CMD)'; exit 0; fi; \
	if [ -z "$(YES)" ]; then read -p "Criar a release? [s/N] " ans || ans=n; case "$$ans" in s|S) ;; *) echo "Cancelado."; exit 1;; esac; fi; \
	$(RELEASE_CMD)
