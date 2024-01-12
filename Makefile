SHELL := /bin/bash

.PHONY: help
.DEFAULT_GOAL := help
.ONESHELL: # Applies to every target in the file https://www.gnu.org/software/make/manual/html_node/One-Shell.html
MAKEFLAGS += --silent # https://www.gnu.org/software/make/manual/html_node/Silent.html

help: ## 💬 This help message :)
	grep -E '[a-zA-Z_-]+:.*?## .*$$' $(firstword $(MAKEFILE_LIST)) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-23s\033[0m %s\n\n", $$1, $$2}'

provision: ## 🌁 provision Azure resources
	echo "Starting provisioning"
	echo "Location: ${LOCATION}, Resource Group: ${RESOURCE_GROUP_NAME}"
	az deployment sub create --template-file ./infra/main.bicep --parameters ./infra/main.bicepparam --output table
	echo "Done."

# $$ references to environment variables this shell running in
# https://www.gnu.org/software/make/manual/html_node/Environment.html
setup-local-env: ## 🛠️ setup local environment variables
	echo "Starting local environment setup"; \
	echo "Getting Service Bus Namespace from Resource Group: $$RESOURCE_GROUP_NAME"; \
	SERVICE_BUS_NAMESPACE_1=$$(az servicebus namespace list --resource-group $$RESOURCE_GROUP_NAME --query "[0].name" --output tsv); \
	echo "Service Bus Namespace: $$SERVICE_BUS_NAMESPACE_1"; \
	echo "Get the connection string for the Service Bus: $$SERVICE_BUS_NAMESPACE_1"; \
	CONNECTION_STRING=$$(az servicebus namespace authorization-rule keys list --resource-group $$RESOURCE_GROUP_NAME --namespace-name $$SERVICE_BUS_NAMESPACE_1 --name RootManageSharedAccessKey | jq -r '.primaryConnectionString'); \
	echo "Service Bus Connection String: ***"; \
	rm -f .env; \
	echo "Setting up local environment variables on .env file"; \
	echo "SERVICE_BUS_CONNECTION_STRING=$$CONNECTION_STRING" >> .env; \
	echo "SERVICE_BUS_TOPIC_NAME=$$TOPIC_NAME" >> .env; \
	echo "SERVICE_BUS_SUBSCRIPTION_NAME=$$TOPIC_SUBSCRIPTION_NAME" >> .env; \
	echo "Done.";

setup-local-cluster: ## 🛠️ setup local cluster
	echo "Starting local cluster setup"
	minikube start -p rate-limiting-sample

	echo "Deploying Redis"
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm install redis -n default --set architecture=standalone bitnami/redis
	echo "Done."

setup-local-cluster-secret: ## 🛠️ setup local cluster secret
	echo "Creating secret for the service bus connection string"
	kubectl create secret generic service-bus-secrets --from-env-file=.env
	echo "Done."

deploy-local-cluster: ## 🛳️  deploy local cluster
	echo "Starting local cluster deployment"
	skaffold run --cache-artifacts=false
	echo "Done."

load-test-local: ## 📈 run load test
	echo "Starting load test"; \
	echo "Getting APIM URL from Resource Group: $$RESOURCE_GROUP_NAME"; \
	APIM_URL_QUERY=$$(az apim list --resource-group $$RESOURCE_GROUP_NAME --query "[0].gatewayUrl" --output tsv); \
	echo "APIM URL: $$APIM_URL_QUERY is set for the load testing"; \
	k6 run ./test/load-testing/topic-load-script.js -e APIM_URL="$$APIM_URL_QUERY"; \
	echo "Done."

run-local: setup-local-env setup-local-cluster setup-local-cluster-secret deploy-local-cluster ## 🏡 run on local cluster