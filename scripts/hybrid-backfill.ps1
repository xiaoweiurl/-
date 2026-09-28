# Copy salesperson_docs into the hybrid shadow collection.
# Do not call mvnw.cmd exec:java -Dexec.args. PowerShell and cmd both split that
# argument, and Maven prints its usage instead of running the main class.
# This script compiles, writes a classpath file, then runs java -cp.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\hybrid-backfill.ps1 -DryRun
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\hybrid-backfill.ps1
param(
    [switch]$DryRun,
    [string]$MilvusHost = "localhost",
    [int]$Port = 19530,
    [string]$Source = "salesperson_docs",
    [string]$Target = "salesperson_docs_hybrid",
    [int]$Dim = 1024,
    [int]$Batch = 200
)

$ErrorActionPreference = "Stop"
$Backend = (Resolve-Path (Join-Path $PSScriptRoot "..\backend")).Path
$Mvnw = Join-Path $Backend "mvnw.cmd"
if (-not (Test-Path -LiteralPath $Mvnw)) {
    Write-Error "mvnw.cmd not found: $Mvnw"
}

$exitCode = 1
Push-Location $Backend
try {
    & $Mvnw -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target\hybrid-cp.txt" "-Dmdep.pathSeparator=;"
    if ($LASTEXITCODE -ne 0) {
        $exitCode = $LASTEXITCODE
    } else {
        $deps = [System.IO.File]::ReadAllText((Join-Path $Backend "target\hybrid-cp.txt")).Trim()
        if ([string]::IsNullOrWhiteSpace($deps)) {
            Write-Error "Maven wrote an empty classpath."
        }
        $classPath = "$(Join-Path $Backend 'target\classes');$deps"
        $appArgs = @(
            "--host=$MilvusHost",
            "--port=$Port",
            "--source=$Source",
            "--target=$Target",
            "--dim=$Dim",
            "--batch=$Batch"
        )
        if ($DryRun) {
            $appArgs += "--dry-run"
        }

        $java = "java"
        if ($env:JAVA_HOME) {
            $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
            if (Test-Path -LiteralPath $candidate) {
                $java = $candidate
            }
        }

        & $java -cp $classPath "com.imagemanager.milvus.tools.HybridBackfillMain" @appArgs
        $exitCode = $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
exit $exitCode
