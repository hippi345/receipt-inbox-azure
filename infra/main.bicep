targetScope = 'resourceGroup'

@description('Azure region for storage, Log Analytics, Application Insights, and the failure alert.')
param location string = 'eastus'

@description('Globally unique storage account name. 3-24 lowercase letters and numbers.')
@minLength(3)
@maxLength(24)
param storageName string = 'st${uniqueString(resourceGroup().id)}'

@description('Log Analytics workspace name.')
param workspaceName string = 'receipt-inbox-logs'

@description('Application Insights component name.')
param appInsightsName string = 'receipt-inbox-insights'

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageName
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    accessTier: 'Hot'
    allowBlobPublicAccess: false
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
  }
}

resource blobServices 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource receiptsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobServices
  name: 'receipts'
  properties: {
    publicAccess: 'None'
  }
}

resource tableServices 'Microsoft.Storage/storageAccounts/tableServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource receiptsTable 'Microsoft.Storage/storageAccounts/tableServices/tables@2023-05-01' = {
  parent: tableServices
  name: 'receipts'
}

resource queueServices 'Microsoft.Storage/storageAccounts/queueServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource receiptJobs 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-05-01' = {
  parent: queueServices
  name: 'receipt-jobs'
}

resource receiptJobsPoison 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-05-01' = {
  parent: queueServices
  name: 'receipt-jobs-poison'
}

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: workspaceName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    workspaceCapping: {
      dailyQuotaGb: json('0.1')
    }
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: appInsightsName
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    IngestionMode: 'LogAnalytics'
    WorkspaceResourceId: workspace.id
  }
}

resource receiptsFailed 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'receipts-failed'
  location: location
  properties: {
    displayName: 'receipts-failed'
    description: 'Fires when receipts.failed is at least 1 in a 5-minute window.'
    severity: 3
    enabled: true
    scopes: [
      workspace.id
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT5M'
    autoMitigate: true
    skipQueryValidation: true
    criteria: {
      allOf: [
        {
          query: '''
customMetrics
| where name == "receipts.failed"
| summarize AggregatedValue = sum(value)
'''
          timeAggregation: 'Total'
          metricMeasureColumn: 'AggregatedValue'
          operator: 'GreaterThanOrEqual'
          threshold: 1
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
  }
}

var serviceLogs = [
  { category: 'StorageRead', enabled: true }
  { category: 'StorageWrite', enabled: true }
  { category: 'StorageDelete', enabled: true }
]

resource blobLogs 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'blob-logs'
  scope: blobServices
  properties: {
    workspaceId: workspace.id
    logs: serviceLogs
  }
}

resource queueLogs 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'queue-logs'
  scope: queueServices
  properties: {
    workspaceId: workspace.id
    logs: serviceLogs
  }
}

resource tableLogs 'Microsoft.Insights/diagnosticSettings@2021-05-01-preview' = {
  name: 'table-logs'
  scope: tableServices
  properties: {
    workspaceId: workspace.id
    logs: serviceLogs
  }
}

output storageConnectionString string = 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'
output appInsightsConnectionString string = appInsights.properties.ConnectionString
