param()

$ErrorActionPreference = 'Stop'

function Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

Set-Location "D:\agrishield\agrishield\frontend"

Step "Collect Supabase + Auth settings"
$supabaseUrl = Read-Host "SUPABASE_URL (https://<project-ref>.supabase.co)"
$supabaseServiceRole = Read-Host "SUPABASE_SERVICE_ROLE_KEY"
$sessionSecret = Read-Host "SESSION_SECRET (long random string)"
$googleClientId = Read-Host "GOOGLE_CLIENT_ID"
$twilioEnabled = Read-Host "TWILIO_ENABLED (true/false)"
$twilioSid = Read-Host "TWILIO_ACCOUNT_SID"
$twilioToken = Read-Host "TWILIO_AUTH_TOKEN"
$twilioPhone = Read-Host "TWILIO_PHONE_NUMBER"
$debugReset = Read-Host "AUTH_DEBUG_RESET_CODE (true/false, prod=false)"

Step "Set Vercel env vars (production)"
$supabaseUrl | vercel env add SUPABASE_URL production
$supabaseServiceRole | vercel env add SUPABASE_SERVICE_ROLE_KEY production
$sessionSecret | vercel env add SESSION_SECRET production
$googleClientId | vercel env add GOOGLE_CLIENT_ID production
$twilioEnabled | vercel env add TWILIO_ENABLED production
$twilioSid | vercel env add TWILIO_ACCOUNT_SID production
$twilioToken | vercel env add TWILIO_AUTH_TOKEN production
$twilioPhone | vercel env add TWILIO_PHONE_NUMBER production
$debugReset | vercel env add AUTH_DEBUG_RESET_CODE production

Step "Set frontend auth mode"
"VITE_AUTH_MODE=api`nVITE_API_BASE_URL=/api" | Out-File -Encoding utf8 .env.production

Step "Deploy to Vercel"
vercel --prod --yes

Step "Done"
Write-Host "Run SQL in Supabase SQL Editor: D:\agrishield\agrishield\frontend\supabase\schema.sql" -ForegroundColor Green
Write-Host "App is deployed with Vercel API routes backed by Supabase." -ForegroundColor Green
