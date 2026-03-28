param(
    [string]$ServiceName = "agrishield-backend",
    [string]$Domain = "api.agrishield.com"
)

$ErrorActionPreference = 'Stop'

function Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

Set-Location "D:\agrishield"

Step "Precheck"
if (-not (Get-Command railway -ErrorAction SilentlyContinue)) {
    throw "Railway CLI is not installed. Run: npm install -g @railway/cli"
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker not found locally. Railway can still build remotely from Dockerfile." -ForegroundColor Yellow
}

Step "Railway login"
railway login

Step "Create/link project"
Write-Host "If you already have a Railway project, choose Link Existing in the prompt." -ForegroundColor Yellow
railway init

Step "Configure service variables"
Write-Host "Set required environment variables in Railway (interactive):" -ForegroundColor Yellow
Write-Host "- GOOGLE_CLIENT_ID" -ForegroundColor Yellow
Write-Host "- TWILIO_ENABLED=true" -ForegroundColor Yellow
Write-Host "- TWILIO_ACCOUNT_SID" -ForegroundColor Yellow
Write-Host "- TWILIO_AUTH_TOKEN" -ForegroundColor Yellow
Write-Host "- TWILIO_PHONE_NUMBER" -ForegroundColor Yellow
Write-Host "- AUTH_SOCIAL_MODE=live" -ForegroundColor Yellow
Write-Host "- AUTH_DEBUG_RESET_CODE=false" -ForegroundColor Yellow

railway variables

Step "Deploy backend"
railway up

Step "Show deployment info"
railway status
railway domain

Write-Host "`nIf custom domain is not set yet, attach: $Domain" -ForegroundColor Green
Write-Host "Then point DNS CNAME for $Domain to Railway-provided target." -ForegroundColor Green
