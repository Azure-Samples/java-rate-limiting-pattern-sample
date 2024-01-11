targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the the environment which is used to generate a short unique hash used in all resources.')
param environmentName string = 'rate-limiting-sample'

@minLength(1)
@description('Primary location for all resources')
param location string

@description('Name of the session enabled queue.')
param topicName string

@description('Name of the session enabled queue.')
param topicSubscriptionName string

param resourceGroupName string
var abbrs = loadJsonContent('./abbreviations.json')
var resourceToken = toLower(uniqueString(subscription().id, environmentName, location))
var tags = { 'azd-env-name': environmentName }

// Organize resources in a resource group
resource rg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: !empty(resourceGroupName) ? resourceGroupName : '${abbrs.resourcesResourceGroups}${environmentName}'
  location: location
  tags: tags
}

// Monitor using application insights
module monitoring './app/monitoring.bicep' = {
  name: 'monitoring'
  scope: rg
  params: {
    location: location
    tags: tags
    logAnalyticsName: '${abbrs.operationalInsightsWorkspaces}${resourceToken}'
    applicationInsightsName: '${abbrs.insightsComponents}${resourceToken}'
  }
}

// Service bus for messaging
module serviceBusResources './app/service-bus.bicep' = {
  name: 'sb-resources'
  scope: rg
  params: {
    location: location
    tags: tags
    resourceToken: resourceToken
    skuName: 'Standard'
    topicName: topicName
    subscriptionName: topicSubscriptionName
  }
}

// API Management
module apimResources './app/apim-api.bicep' = {
  name: 'apim-resources'
  scope: rg
  params: {
    name: '${abbrs.apiManagementService}${resourceToken}'
    location: location
    tags: tags
    applicationInsightsName: monitoring.outputs.applicationInsightsName
    serviceBusUrl: serviceBusResources.outputs.SERVICE_BUS_ENDPOINT
    topicName: topicName
    sku: 'Consumption'
  }
}

module managedIdentityAccess './app/access.bicep' = {
  name: 'managed-identity-access'
  scope: rg
  params: {
    location: location
    serviceBusName: serviceBusResources.outputs.SERVICE_BUS_NAME
    managedIdentityName: '${abbrs.managedIdentityUserAssignedIdentities}${resourceToken}'
    apimPrincipalId: apimResources.outputs.APIM_PRINCIPAL_ID
  }
}

output SERVICE_BUS_ENDPOINT string = serviceBusResources.outputs.SERVICE_BUS_ENDPOINT
output APIM_SERVICE_MESSAGES_API_URI string = apimResources.outputs.APIM_SERVICE_MESSAGES_API_URI
