# Run in the foreground; Ctrl+C stops this backend. Database settings come from
# the existing application configuration or DB_* environment variables.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$previousProfile = $env:SPRING_PROFILES_ACTIVE
try {
    $env:SPRING_PROFILES_ACTIVE = 'llamafactory'
    Push-Location $projectRoot
    try {
        & (Join-Path $projectRoot 'mvnw.cmd') -f backend/pom.xml spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    $env:SPRING_PROFILES_ACTIVE = $previousProfile
}
