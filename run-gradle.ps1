param(
    [string[]]$Tasks = @("testDebugUnitTest"),
    [switch]$NoDaemon
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleUserHome = Join-Path $projectRoot ".gradle-user-cli"
$androidUserHome = Join-Path $projectRoot ".android-user"
$gradlew = Join-Path $projectRoot "gradlew.bat"
$androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"

if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat를 찾을 수 없습니다: $gradlew"
}

if (-not $env:JAVA_HOME) {
    if (Test-Path $androidStudioJbr) {
        $env:JAVA_HOME = $androidStudioJbr
    } else {
        throw "JAVA_HOME이 비어 있고 Android Studio JBR도 찾지 못했습니다. JAVA_HOME을 먼저 설정하세요."
    }
}

if (-not (Test-Path $gradleUserHome)) {
    New-Item -ItemType Directory -Path $gradleUserHome | Out-Null
}

$env:ANDROID_USER_HOME = $androidUserHome
$env:GRADLE_USER_HOME = $gradleUserHome

if (-not (Test-Path $androidUserHome)) {
    New-Item -ItemType Directory -Path $androidUserHome | Out-Null
}

$arguments = @()
if ($NoDaemon) {
    $arguments += "--no-daemon"
}
$arguments += "-Pkotlin.compiler.execution.strategy=in-process"
$arguments += $Tasks

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_USER_HOME=$env:ANDROID_USER_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "Gradle tasks: $($Tasks -join ', ')"

Push-Location $projectRoot
try {
    & $gradlew @arguments
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
