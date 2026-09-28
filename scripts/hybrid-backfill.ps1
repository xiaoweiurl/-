# Copy salesperson_docs into the hybrid shadow collection.
# Do not call mvnw.cmd exec:java -Dexec.args. PowerShell and cmd both split that
# argument, and Maven prints its usage instead of running the main class.
#
# Build the classpath the way that works on Windows PowerShell 5.1:
#   cmd /c 'mvnw.cmd -q ... -Dmdep.outputFile=cp.txt'
#   $cp = "target\classes;" + (Get-Content cp.txt -Raw).Trim()
# Do not pass -Dmdep.pathSeparator=;. Calling mvnw.cmd from PowerShell hands the
# line to cmd.exe, and ";" ends the command, so Maven writes the jars with an
# empty separator. java then loads the main class from target\classes and throws
# NoClassDefFoundError for io.milvus. Also do not read cp.txt with
# [IO.File]::ReadAllText: on PowerShell 5.1 that is UTF-8, while Maven writes
# the system ANSI code page.
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
    cmd /c 'mvnw.cmd -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=cp.txt'
    if ($LASTEXITCODE -ne 0) {
        $exitCode = $LASTEXITCODE
    } else {
        $deps = (Get-Content -LiteralPath "cp.txt" -Raw).Trim()
        if ([string]::IsNullOrWhiteSpace($deps)) {
            Write-Error "Maven wrote an empty cp.txt."
        }
        $milvusJar = $deps.Split(';') | Where-Object { $_ -match '[\\/]milvus-sdk-java-[^\\/;]*\.jar$' } | Select-Object -First 1
        if (-not $milvusJar) {
            Write-Error "cp.txt is not a Windows ';' classpath containing milvus-sdk-java."
        }
        $classPath = "target\classes;" + $deps
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
