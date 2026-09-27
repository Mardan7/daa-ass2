param([string]$JavaHome = $env:JAVA_HOME, [switch]$TestsOnly)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$JavaHome -and !(Get-Command javac -ErrorAction SilentlyContinue)) {
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    $candidate = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($candidate) { $JavaHome = $candidate.FullName }
}
$compiler = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$runtime = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
New-Item -ItemType Directory -Path build -Force | Out-Null
$sources = Get-ChildItem -LiteralPath src -Filter '*.java' | ForEach-Object { $_.FullName }
& $compiler -encoding UTF-8 -d build $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& $runtime -cp build Tests
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
if (!$TestsOnly) {
    & $runtime -Xms256m -Xmx1g -cp build Benchmark
    if ($LASTEXITCODE -ne 0) { throw 'Benchmarks failed' }
}
