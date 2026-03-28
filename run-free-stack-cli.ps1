param(
    [string]$DbUrl = "",
    [string]$DbUser = "",
    [string]$DbPassword = "",
    [string]$GoogleClientId = "285893124141-7chp3u04870firvqu08pv1k0dmva9s1g.apps.googleusercontent.com",
    [string]$TwilioSid = "",
    [string]$TwilioToken = "",
    [string]$TwilioPhone = ""
)

$ErrorActionPreference = 'Stop'

function Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

function Ensure-Value($value, $prompt, $secret=$false) {
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    if ($secret) {
        $secure = Read-Host $prompt -AsSecureString
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { return [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    }
    return (Read-Host $prompt)
}

Set-Location "D:\agrishield"

Step "Collect configuration"
$DbUrl = Ensure-Value $DbUrl "DB_URL (Supabase JDBC or local postgres URL)"
$DbUser = Ensure-Value $DbUser "DB_USER"
$DbPassword = Ensure-Value $DbPassword "DB_PASSWORD" $true
$GoogleClientId = Ensure-Value $GoogleClientId "GOOGLE_CLIENT_ID"
$TwilioSid = Ensure-Value $TwilioSid "TWILIO_ACCOUNT_SID"
$TwilioToken = Ensure-Value $TwilioToken "TWILIO_AUTH_TOKEN" $true
$TwilioPhone = Ensure-Value $TwilioPhone "TWILIO_PHONE_NUMBER (e.g. +123456789)"

Step "Start backend locally (TomEE)"
$backendCommand = @"
Set-Location 'D:\agrishield\agrishield';
`$env:JAVA_HOME='C:\Program Files\Java\jdk-25';
`$env:Path='C:\Program Files\Java\jdk-25\bin;' + `$env:Path;
`$env:DB_URL='$DbUrl';
`$env:DB_USER='$DbUser';
`$env:DB_PASSWORD='$DbPassword';
`$env:GOOGLE_CLIENT_ID='$GoogleClientId';
`$env:TWILIO_ENABLED='true';
`$env:TWILIO_ACCOUNT_SID='$TwilioSid';
`$env:TWILIO_AUTH_TOKEN='$TwilioToken';
`$env:TWILIO_PHONE_NUMBER='$TwilioPhone';
`$env:AUTH_SOCIAL_MODE='live';
`$env:AUTH_DEBUG_RESET_CODE='false';
mvn -q clean package -DskipTests;
mvn -q tomee:run
"@

Start-Process powershell -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $backendCommand -WindowStyle Minimized | Out-Null
Start-Sleep -Seconds 12

Step "Start Cloudflare tunnel"
$logFile = "D:\agrishield\cloudflared.log"
if (Test-Path $logFile) { Remove-Item $logFile -Force }

$cloudflaredPath = "C:\Program Files\cloudflared\cloudflared.exe"
if (-not (Test-Path $cloudflaredPath)) {
    $cloudflaredPath = "cloudflared"
}

Start-Process $cloudflaredPath -ArgumentList "tunnel", "--url", "http://localhost:8080", "--logfile", $logFile, "--no-autoupdate" -WindowStyle Minimized | Out-Null

$publicUrl = ""
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Path $logFile) {
        $match = Select-String -Path $logFile -Pattern "https://[a-zA-Z0-9-]+\.trycloudflare\.com" -AllMatches | Select-Object -Last 1
        if ($match) {
            $publicUrl = $match.Matches[0].Value
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($publicUrl)) {
    throw "Could not detect Cloudflare tunnel URL. Check cloudflared.log"
}

Write-Host "Backend public URL: $publicUrl" -ForegroundColor Green

Step "Deploy frontend to Vercel with backend URL"
Set-Location "D:\agrishield\agrishield\frontend"
"VITE_AUTH_MODE=api`nVITE_API_BASE_URL=$publicUrl/api" | Out-File -Encoding utf8 ".env.production"
vercel --prod --yes

Step "Completed"
Write-Host "Frontend deployed and connected to live backend tunnel." -ForegroundColor Green
Write-Host "Keep this machine running to keep backend online (free mode)." -ForegroundColor Yellow
Write-Host "Backend URL: $publicUrl" -ForegroundColor Green
