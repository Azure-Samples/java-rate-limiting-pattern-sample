param managedIdentityName string
param serviceBusName string
param apimPrincipalId string
param location string

// See https://learn.microsoft.com/en-us/azure/role-based-access-control/built-in-roles#azure-service-bus-data-sender
var roleIdServiceBusSender = '69a216fc-b8fb-44d8-bc22-1f3c2cd27a39' // Azure Service Bus Data Sender

// user assigned managed identity to use throughout
resource managedIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: managedIdentityName
  location: location
}

resource serviceBus 'Microsoft.ServiceBus/namespaces@2021-11-01' existing = {
  name: serviceBusName
}

// Grant permissions to the managedIdentity to specific role to servicebus
// Assign the managed identity to the applications (app service, function app, etc) that need to access the service bus
resource roleAssignmentServiceBusSender 'Microsoft.Authorization/roleAssignments@2020-04-01-preview' = {
  name: guid(serviceBus.id, roleIdServiceBusSender, managedIdentityName)
  scope: serviceBus
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleIdServiceBusSender)
    principalId: managedIdentity.properties.principalId
    principalType: 'ServicePrincipal' // managed identity is a form of service principal
  }
  dependsOn: [
    serviceBus
  ]
}

// Grant permissions to the APIM to servicebus
// Using managed identity, APIM can access the service bus without having to store credentials
resource roleAssignmentAPIMServiceBusSender 'Microsoft.Authorization/roleAssignments@2020-04-01-preview' = {
  name: guid(serviceBus.id, roleIdServiceBusSender, apimPrincipalId)
  scope: serviceBus
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleIdServiceBusSender)
    principalId: apimPrincipalId
    principalType: 'ServicePrincipal' // managed identity is a form of service principal
  }
  dependsOn: [
    serviceBus
  ]
}


output managedIdentityPrincipalId string = managedIdentity.properties.principalId
output managedIdentityClientlId string = managedIdentity.properties.clientId
output managedIdentityId string = managedIdentity.id
output managedIdentityName string = managedIdentity.name
