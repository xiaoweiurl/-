# Read-only image-search eval. ASCII only: PowerShell 5.1 misreads UTF-8 without BOM.
# Top-level Recall stays the old per-image metric on the whole-image collection.
# byCollection.full / byCollection.crop add SAME_PRODUCT product recall and SIMILAR_REFERENCE image recall.
# Same launch path as scripts/hybrid-eval.ps1. Does not write Milvus or image_vector_index.
# Do not call mvnw exec:java -Dexec.args. Do not pass -Dmdep.pathSeparator=;.
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
        & $java "-Dfile.encoding=UTF-8" "-Dimage-search.enabled=true" "-cp" $classPath "com.imagemanager.imagesearch.ImageSearchEval" @args
        $exitCode = $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
exit $exitCode
