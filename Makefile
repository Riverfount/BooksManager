.DEFAULT_GOAL := help

.PHONY: help run test verify up down clean

help: ## Lista os alvos disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-10s\033[0m %s\n", $$1, $$2}'

run: ## Sobe a API (sobe o PostgreSQL pelo compose.yaml; Docker precisa estar rodando)
	./mvnw spring-boot:run

test: ## Roda todos os testes
	./mvnw test

verify: ## Build completo com verificações (testes + ArchUnit)
	./mvnw verify

up: ## Sobe o PostgreSQL via Docker Compose
	docker compose up -d

down: ## Derruba o PostgreSQL via Docker Compose
	docker compose down

clean: ## Limpa o build do Maven
	./mvnw clean
