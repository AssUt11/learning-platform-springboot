param(
    [string]$Python = 'C:\Users\lenovo\miniconda3\envs\llamafactory\python.exe',
    [string]$FactoryRoot = 'D:\LLaMA-Factory\LlamaFactory',
    [string]$Config = (Join-Path $PSScriptRoot '..\deploy\llamafactory\zhidu-local.yaml'),
    [ValidateRange(1024,65535)][int]$Port = 8000,
    [string]$ModelName = 'zhidu-qwen3-4b-lora'
)
$ErrorActionPreference = 'Stop'
foreach ($requiredPath in @($Python, $FactoryRoot, $Config)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) { throw "Missing path: $requiredPath" }
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is already in use. Inspect the existing service or choose -Port."
}
$configPath = (Resolve-Path -LiteralPath $Config).Path
$runtimeDir = Join-Path $PSScriptRoot '..\.runtime\llamafactory'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$runtimeDir = (Resolve-Path -LiteralPath $runtimeDir).Path
$savedEnvironment = @{}
$environmentOverrides = @{
    API_HOST = '127.0.0.1'; API_PORT = "$Port"; API_MODEL_NAME = $ModelName;
    API_VERBOSE = '0'; HF_HUB_OFFLINE = '1'; TRANSFORMERS_OFFLINE = '1';
    PATH = "$(Split-Path $Python);$(Split-Path $Python)\Library\bin;$(Split-Path $Python)\Scripts;$env:PATH"
}
try {
    foreach ($key in $environmentOverrides.Keys) {
        $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $environmentOverrides[$key], 'Process')
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdout = Join-Path $runtimeDir "$stamp.stdout.log"
    $stderr = Join-Path $runtimeDir "$stamp.stderr.log"
    $process = Start-Process -FilePath $Python -ArgumentList @('-m', 'llamafactory.cli', 'api', "`"$configPath`"") `
        -WorkingDirectory $FactoryRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $process.Id | Set-Content -LiteralPath (Join-Path $runtimeDir 'server.pid')
    Write-Output "Started PID $($process.Id). Model loading is asynchronous; check http://127.0.0.1:$Port/v1/models."
    Write-Output "Logs: $stdout / $stderr"
} finally {
    foreach ($key in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process')
    }
}
