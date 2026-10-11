@description('Azure region for the Azure Managed Redis cluster.')
param location string = 'eastus'

@description('Globally unique Azure Managed Redis cluster name.')
param redisName string = 'rc${uniqueString(resourceGroup().id)}'

@description('Resource id of the Log Analytics workspace from the main deployment.')
param workspaceResourceId string

resource redis 'Microsoft.Cache/redisEnterprise@2025-04-01' = {
  name: redisName
  location: location
  sku: {
    name: 'Balanced_B0'
  }
  properties: {
    minimumTlsVersion: '1.2'
    highAvailability: 'Disabled'
  }
}

resource database 'Microsoft.Cache/redisEnterprise/databases@2025-04-01' = {
  parent: redis
  name: 'default'
  properties: {
    clientProtocol: 'Encrypted'
    port: 10000
    clusteringPolicy: 'OSSCluster'
    evictionPolicy: 'VolatileLRU'
    accessKeysAuthentication: 'Enabled'
  }
}

resource redisLogs 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'redis-connection-logs'
  scope: database
  properties: {
    workspaceId: workspaceResourceId
    logs: [
      {
        category: 'ConnectionEvents'
        enabled: true
      }
    ]
  }
}

output redisHost string = redis.properties.hostName
