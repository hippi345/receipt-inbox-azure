winget install --id Microsoft.AzureCLI -e --accept-package-agreements --accept-source-agreements
winget install --id Microsoft.Azure.AZCopy.10 -e --accept-package-agreements --accept-source-agreements
winget install --id Microsoft.Azure.StorageExplorer -e --accept-package-agreements --accept-source-agreements

Write-Output @'
Run these commands in a new terminal so az is on the path:

az login
az account show --query "{name:name, id:id}" -o table
az extension add --name redisenterprise
az extension add --name application-insights
az extension add --name log-analytics
az bicep install
'@
