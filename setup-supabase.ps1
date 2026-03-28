param(
    [switch]$RunMigration
)

$ErrorActionPreference = 'Stop'

function Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

Set-Location "D:\agrishield"

Step "Supabase configuration"
Write-Host "Get these from Supabase > Project Settings > Database" -ForegroundColor Yellow
$dbUrl = Read-Host "Enter JDBC URL (example: jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require)"
$dbUser = Read-Host "Enter DB user (example: postgres.<ref>)"
$dbPassword = Read-Host "Enter DB password"

if ([string]::IsNullOrWhiteSpace($dbUrl) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
    throw "DB_URL, DB_USER and DB_PASSWORD are required."
}

Step "Write local environment file"
$envContent = @"
DB_URL=$dbUrl
DB_USER=$dbUser
DB_PASSWORD=$dbPassword
"@
$envContent | Out-File -Encoding utf8 ".env.supabase"
Write-Host "Saved D:\agrishield\.env.supabase" -ForegroundColor Green

Step "Set environment for current shell"
$env:DB_URL = $dbUrl
$env:DB_USER = $dbUser
$env:DB_PASSWORD = $dbPassword

Write-Host "DB variables loaded in this terminal session." -ForegroundColor Green

if ($RunMigration) {
    Step "Run Flyway migration on Supabase"
    Set-Location "D:\agrishield\agrishield"
    mvn -q flyway:migrate -Ddb.url="$dbUrl" -Ddb.user="$dbUser" -Ddb.password="$dbPassword"
    Write-Host "Flyway migration completed." -ForegroundColor Green
}

Step "Next deployment variables"
Write-Host "Use the same values in your backend host environment variables:" -ForegroundColor Yellow
Write-Host "DB_URL=$dbUrl"
Write-Host "DB_USER=$dbUser"
Write-Host "DB_PASSWORD=***"
Write-Host ""
Write-Host "If deploying backend later on Railway/Render/Fly, add these there too." -ForegroundColor Yellow
