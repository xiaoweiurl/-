# Image vector backfill. ASCII only: PowerShell 5.1 misreads UTF-8 without BOM.
# Same launch path as scripts/hybrid-backfill.ps1:
#   cmd /c writes backend/cp.txt
#   Get-Content -Raw builds a ';' classpath
#   java -cp runs the main class
# Do not call mvnw exec:java -Dexec.args. PowerShell 5.1 splits that.
# Do not pass -Dmdep.pathSeparator=; because cmd.exe treats ';' as a command break.
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
        $java = "java"
        if ($env:JAVA_HOME) {
            $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
            if (Test-Path -LiteralPath $candidate) {
                $java = $candidate
            }
        }
        & $java "-Dfile.encoding=UTF-8" "-Dimage-search.enabled=true" "-cp" $classPath "com.imagemanager.imagesearch.ImageSearchBackfill" @args
        $exitCode = $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
exit $exitCode
