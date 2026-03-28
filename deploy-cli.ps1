param(
    [string]$GitHubRepo,
    [string]$BackendApiUrl,
    [switch]$SkipVercel
)

$ErrorActionPreference = 'Stop'

function Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

Set-Location "D:\agrishield"

Step "Precheck"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "git is not installed. Install Git first."
}
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "node is not installed. Install Node.js first."
}

$inside = git rev-parse --is-inside-work-tree 2>$null
if ($inside -ne 'true') {
    throw "Current directory is not a git repository."
}

if (-not $GitHubRepo) {
    $GitHubRepo = Read-Host "Enter GitHub HTTPS repo URL (example: https://github.com/USER/agrishield.git)"
}

if (-not $BackendApiUrl) {
    $BackendApiUrl = Read-Host "Enter backend base URL for frontend (example: https://api.agrishield.com)"
}

if (-not $BackendApiUrl.StartsWith('http')) {
    throw "BackendApiUrl must start with http:// or https://"
}

Step "Configure git remote"
$originExists = (git remote) -contains 'origin'
if ($originExists) {
    git remote set-url origin $GitHubRepo
} else {
    git remote add origin $GitHubRepo
}

git branch -M main

Step "Commit latest changes"
git add .
$hasChanges = git diff --cached --name-only
if ($hasChanges) {
    git commit -m "chore: cli deployment wiring and production config"
} else {
    Write-Host "No new changes to commit." -ForegroundColor Yellow
}

Step "Push to GitHub"
Write-Host "If prompted, authenticate via browser/token." -ForegroundColor Yellow
git push -u origin main

if ($SkipVercel) {
    Write-Host "Skipped Vercel deployment." -ForegroundColor Yellow
    exit 0
}

Step "Deploy frontend to Vercel"
if (-not (Get-Command vercel -ErrorAction SilentlyContinue)) {
    Write-Host "Installing Vercel CLI..." -ForegroundColor Yellow
    npm install -g vercel
}

Set-Location "D:\agrishield\agrishield\frontend"

if (-not (Test-Path ".env.production")) {
    "VITE_AUTH_MODE=api`nVITE_API_BASE_URL=$BackendApiUrl" | Out-File -Encoding utf8 ".env.production"
} else {
    $content = Get-Content ".env.production" -Raw
    if ($content -notmatch "VITE_AUTH_MODE") { Add-Content ".env.production" "`nVITE_AUTH_MODE=api" }
    if ($content -match "VITE_API_BASE_URL=") {
        $content = $content -replace "VITE_API_BASE_URL=.*", "VITE_API_BASE_URL=$BackendApiUrl"
        Set-Content ".env.production" $content
    } else {
        Add-Content ".env.production" "VITE_API_BASE_URL=$BackendApiUrl"
    }
}

Write-Host "Vercel will ask you to login/link project if first run." -ForegroundColor Yellow
vercel --prod

Step "Done"
Write-Host "GitHub push complete and Vercel production deploy submitted." -ForegroundColor Green
Write-Host "Frontend directory: D:\agrishield\agrishield\frontend" -ForegroundColor Green
Write-Host "Backend API configured as: $BackendApiUrl" -ForegroundColor Green
