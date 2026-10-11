param(
    [Parameter(Mandatory = $true)]
    [string]$ResourceGroup
)

az resource list --resource-group $ResourceGroup -o table
Write-Output "This deletes storage, queues, tables, blobs, Log Analytics, Application Insights, the failure alert, and Redis."
az group delete --name $ResourceGroup --yes --no-wait
