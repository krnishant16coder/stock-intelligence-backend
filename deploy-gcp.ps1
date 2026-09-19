# GCP deploy for stock-intelligence-backend (Cloud Run + Neon + Artifact Registry)
# Mail is OPTIONAL. No-gmail mode (default when -MailUsername omitted or -NoMail):
#   .\deploy-gcp.ps1 -ProjectId "my-project-123" -NeonUrl "jdbc:postgresql://ep-xxx-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require" -NeonUser "neondb_owner" -NoMail
# With Gmail later:
#   .\deploy-gcp.ps1 -ProjectId "my-project-123" -NeonUrl "..." -NeonUser "neondb_owner" -MailUsername "you@gmail.com" -MailTo "you@gmail.com"
param(
  [Parameter(Mandatory=$true)][string]$ProjectId,
  [Parameter(Mandatory=$true)][string]$NeonUrl,
  [Parameter(Mandatory=$true)][string]$NeonUser,
  [string]$MailUsername = "",
  [string]$MailTo = "",
  [string]$MailFrom = "",
  [string]$Region = "asia-south1",
  [string]$Repo = "stockintel",
  [string]$Service = "stockintel-backend",
  [switch]$NoMail,
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$MailEnabled = (-not $NoMail) -and ($MailUsername -ne "")
if ($MailEnabled) {
  if ($MailTo -eq "") { $MailTo = $MailUsername }
  if ($MailFrom -eq "") { $MailFrom = $MailUsername }
}

$Image = "$Region-docker.pkg.dev/$ProjectId/$Repo/backend:latest"

Write-Host "==> Project: $ProjectId | Region: $Region | Service: $Service" -ForegroundColor Cyan
Write-Host "==> Neon: $NeonUrl"
if ($MailEnabled) {
  Write-Host "==> Mail: $MailUsername -> $MailTo"
} else {
  Write-Host "==> Mail: DISABLED (notifications off, no Gmail needed)" -ForegroundColor Yellow
}

# 1. Base config (no Cloud SQL needed - Neon is external)
gcloud config set project $ProjectId
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com cloudbuild.googleapis.com

# 2. Artifact Registry (idempotent)
try {
  gcloud artifacts repositories describe $Repo --location=$Region | Out-Null
  Write-Host "Artifact Registry repo '$Repo' exists, skipping create."
} catch {
  gcloud artifacts repositories create $Repo --repository-format=docker --location=$Region --description="stockintel images"
}

# 3. Build + push (multi-stage Dockerfile builds jar inside Cloud Build)
if (-not $SkipBuild) {
  Write-Host "==> Building + pushing $Image ..." -ForegroundColor Cyan
  gcloud builds submit --region=$Region --tag $Image .
} else {
  Write-Host "Skipping build (--SkipBuild), using existing image $Image"
}

# 4. Deploy to Cloud Run
# NOTE: create these secrets once before first deploy (no-mail mode needs only 4):
#   echo -n "<neon-password>" | gcloud secrets create db-password --data-file=-
#   echo -n "YOUR_KEY" | gcloud secrets create alphavantage-key --data-file=-
#   echo -n "YOUR_KEY" | gcloud secrets create newsdata-key --data-file=-
#   echo -n "YOUR_KEY" | gcloud secrets create ai-key --data-file=-
# With Gmail later, also: echo -n "<16-char-app-password>" | gcloud secrets create mail-password --data-file=-
# Neon URL: Neon dashboard > Connection Details > Pooled, must include ?sslmode=require

$EnvVars = @(
  "SPRING_DATASOURCE_URL=$NeonUrl",
  "SPRING_DATASOURCE_USERNAME=$NeonUser",
  "APP_MARKET_DATA_PROVIDER=alphavantage",
  "APP_NEWS_PROVIDER=newsdata",
  "APP_AI_PROVIDER=openai",
  "AI_BASE_URL=https://api.openai.com/v1",
  "AI_MODEL=gpt-4o-mini",
  "AI_MAX_NEWS_FOR_AI=6",
  "AI_MIN_INTERVAL_MS=12000",
  "AI_MAX_TOKENS=500",
  "APP_SCHEDULING_ENABLED=true"
)

$Secrets = "SPRING_DATASOURCE_PASSWORD=db-password:latest,ALPHAVANTAGE_API_KEY=alphavantage-key:latest,NEWSDATA_API_KEY=newsdata-key:latest,AI_API_KEY=ai-key:latest"

if ($MailEnabled) {
  $EnvVars += "APP_NOTIFICATIONS_ENABLED=true"
  $EnvVars += "SPRING_MAIL_HOST=smtp.gmail.com"
  $EnvVars += "SPRING_MAIL_PORT=587"
  $EnvVars += "SPRING_MAIL_USERNAME=$MailUsername"
  $EnvVars += "SPRING_MAIL_AUTH=true"
  $EnvVars += "SPRING_MAIL_STARTTLS=true"
  $EnvVars += "NOTIFICATION_EMAIL_TO=$MailTo"
  $EnvVars += "NOTIFICATION_EMAIL_FROM=$MailFrom"
  $Secrets += ",SPRING_MAIL_PASSWORD=mail-password:latest"
} else {
  # Safe no-mail mode: NotificationService logs SKIPPED, no SMTP attempt (see NotificationService.java:63-68)
  $EnvVars += "APP_NOTIFICATIONS_ENABLED=false"
}

$EnvString = $EnvVars -join ","

Write-Host "==> Deploying to Cloud Run ..." -ForegroundColor Cyan
gcloud run deploy $Service `
  --image=$Image `
  --region=$Region --platform=managed --allow-unauthenticated `
  --port=8080 --memory=1Gi --cpu=1 --min-instances=0 --max-instances=2 `
  --set-env-vars="$EnvString" `
  --set-secrets="$Secrets"

Write-Host ""
Write-Host "Done. Verify:" -ForegroundColor Green
Write-Host "  gcloud run logs read $Service --region=$Region"
$Url = gcloud run services describe $Service --region=$Region --format="value(status.url)"
Write-Host "  $Url/actuator/health"
Write-Host "  $Url/swagger-ui.html"
