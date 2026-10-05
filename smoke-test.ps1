# Exercises all four saga paths against the running order-service and polls final status.
# Run after 'docker compose up -d' and '.\run-all.ps1' (give the services ~20s to start).
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081/orders"

function New-Order([string]$productId, [int]$qty, [decimal]$amount) {
    $body = @{ productId = $productId; quantity = $qty; amount = $amount } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri $base -Method Post -ContentType "application/json" -Body $body
    return $resp.orderId
}

function Get-Status([string]$id) {
    try { (Invoke-RestMethod -Uri "$base/$id" -Method Get).status } catch { "UNKNOWN" }
}

Write-Host "Placing orders for each scenario..." -ForegroundColor Cyan
$happy   = New-Order "SKU-1"   2 42.00     # expect CONFIRMED
$decline = New-Order "SKU-1"   1 12.99     # expect CANCELLED (payment declined -> inventory released)
$noStock = New-Order "SKU-OUT" 1 30.00     # expect CANCELLED (reservation fails)
$outage  = New-Order "SKU-1"   1 5000.00   # expect PENDING (PSP outage -> retried -> DLQ; no reply)

$ids = [ordered]@{ "happy (SKU-1 x2)"=$happy; "decline (12.99)"=$decline; "no-stock (SKU-OUT)"=$noStock; "outage (5000)"=$outage }

Write-Host "Waiting for the sagas to settle..." -ForegroundColor Cyan
Start-Sleep -Seconds 8

foreach ($k in $ids.Keys) {
    $id = $ids[$k]
    "{0,-22} {1,-38} -> {2}" -f $k, $id, (Get-Status $id) | Write-Host
}

Write-Host ""
Write-Host "Expected: CONFIRMED / CANCELLED / CANCELLED / PENDING (outage lands in payment.process.cmd.DLT)." -ForegroundColor Yellow
Write-Host "Inspect topics & the DLT at http://localhost:8080 ; breaker at http://localhost:8083/actuator/circuitbreakers"
