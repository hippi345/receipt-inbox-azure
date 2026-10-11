targetScope = 'resourceGroup'

// Container Apps for the API and worker. Pass the names from your own main and Redis deployments.
// Connection strings are read at deploy time. This file does not contain a key.
@description('Region for the registry, identity, and Container Apps environment.')
param location string = 'eastus'

@description('Globally unique registry name. 5-50 alphanumeric characters.')
param acrName string

@description('Storage account name from the main deployment.')
param storageAccountName string

@description('Log Analytics workspace name from the main deployment.')
param workspaceName string = 'receipt-inbox-logs'

@description('Application Insights name from the main deployment.')
param appInsightsName string = 'receipt-inbox-insights'

@description('Azure Managed Redis cluster name from the Redis deployment.')
param redisName string

@description('Create the API and worker only after their images exist in the registry.')
param deployApps bool = false

@description('Image tag built by az acr build.')
param imageTag string = '1'

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' existing = {
  name: storageAccountName
}

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' existing = {
  name: workspaceName
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' existing = {
  name: appInsightsName
}

resource redis 'Microsoft.Cache/redisEnterprise@2025-04-01' existing = {
  name: redisName
}

resource redisDatabase 'Microsoft.Cache/redisEnterprise/databases@2025-04-01' existing = {
  parent: redis
  name: 'default'
}

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
  }
}

resource puller 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'receipt-inbox-acr-pull'
  location: location
}

var acrPullRole = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, puller.id, acrPullRole)
  scope: registry
  properties: {
    roleDefinitionId: acrPullRole
    principalId: puller.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource containerEnv 'Microsoft.App/managedEnvironments@2026-07-01' = {
  name: 'receipt-inbox-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: workspace.properties.customerId
        sharedKey: workspace.listKeys().primarySharedKey
      }
    }
  }
}

var storageConnection = 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'

var sharedSecrets = [
  {
    name: 'storage-connection'
    value: storageConnection
  }
  {
    name: 'insights-connection'
    value: appInsights.properties.ConnectionString
  }
  {
    name: 'redis-password'
    value: redisDatabase.listKeys().primaryKey
  }
]

var sharedEnv = [
  {
    name: 'AZURE_STORAGE_CONNECTION_STRING'
    secretRef: 'storage-connection'
  }
  {
    name: 'APPLICATIONINSIGHTS_CONNECTION_STRING'
    secretRef: 'insights-connection'
  }
  {
    name: 'REDIS_ENABLED'
    value: 'true'
  }
  {
    name: 'REDIS_HOST'
    value: redis.properties.hostName
  }
  {
    name: 'REDIS_PORT'
    value: '10000'
  }
  {
    name: 'REDIS_PASSWORD'
    secretRef: 'redis-password'
  }
]

resource api 'Microsoft.App/containerApps@2026-07-01' = if (deployApps) {
  name: 'receipt-api'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${puller.id}': {}
    }
  }
  properties: {
    environmentId: containerEnv.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: [
        {
          server: registry.properties.loginServer
          identity: puller.id
        }
      ]
      secrets: sharedSecrets
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: 'receipt-api'
          image: '${registry.properties.loginServer}/receipt-api:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: sharedEnv
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: 8080
                scheme: 'HTTP'
              }
              initialDelaySeconds: 30
              periodSeconds: 10
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health/readiness'
                port: 8080
                scheme: 'HTTP'
              }
              initialDelaySeconds: 15
              periodSeconds: 10
            }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

resource worker 'Microsoft.App/containerApps@2026-07-01' = if (deployApps) {
  name: 'receipt-worker'
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${puller.id}': {}
    }
  }
  properties: {
    environmentId: containerEnv.id
    configuration: {
      activeRevisionsMode: 'Single'
      registries: [
        {
          server: registry.properties.loginServer
          identity: puller.id
        }
      ]
      secrets: sharedSecrets
    }
    template: {
      containers: [
        {
          name: 'receipt-worker'
          image: '${registry.properties.loginServer}/receipt-worker:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: sharedEnv
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

output registryLoginServer string = registry.properties.loginServer
