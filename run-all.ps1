# Builds all modules once, then launches each Spring Boot service in its own PowerShell window.
# Prereqs: JDK 17 on PATH, Maven on PATH (or run 'mvn -N wrapper:wrapper' to add a wrapper),
#          and the infra stack up (docker compose up -d).
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$mvn = "mvn"
if (Test-Path (Join-Path $root "mvnw.cmd")) { $mvn = Join-Path $root "mvnw.cmd" }

Write-Host "==> Building all modules (skipping tests)..." -ForegroundColor Cyan
& $mvn -q -DskipTests install

$services = @("order-service","inventory-service","payment-service","notification-service")
foreach ($s in $services) {
    Write-Host "==> Starting $s..." -ForegroundColor Green
    $cmd = "cd `"$root`"; & `"$mvn`" -q -pl $s spring-boot:run"
    Start-Process powershell -ArgumentList "-NoExit","-Command",$cmd
    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "All services launching in separate windows." -ForegroundColor Cyan
Write-Host "  order        -> http://localhost:8081"
Write-Host "  inventory    -> http://localhost:8082"
Write-Host "  payment      -> http://localhost:8083 (actuator: /actuator/circuitbreakers)"
Write-Host "  notification -> http://localhost:8084"
Write-Host "  kafka-ui     -> http://localhost:8080"
