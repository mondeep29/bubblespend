param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
function Invoke-Device {
    param([string[]]$DeviceArgs)
    $result = & $Adb -s $Serial @DeviceArgs
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $DeviceArgs" }
    return $result
}
function Get-ActiveFont {
    $dump = (Invoke-Device -DeviceArgs @('shell', 'dumpsys', 'activity')) -join [Environment]::NewLine
    $match = [regex]::Match($dump, 'mGlobalConfig=\{(?<scale>[0-9.]+).*?fontWeightAdjustment=(?<weight>-?[0-9]+)\}', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $match.Success) { throw 'Cannot verify the active Android font configuration.' }
    return @{ Scale = $match.Groups['scale'].Value; Weight = $match.Groups['weight'].Value }
}
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
$settingsToCheck = @(
    @('system', 'font_scale', '2.0'),
    @('secure', 'font_weight_adjustment', '300'),
    @('secure', 'high_text_contrast_enabled', '1'),
    @('global', 'animator_duration_scale', '0'),
    @('global', 'transition_animation_scale', '0'),
    @('global', 'window_animation_scale', '0'),
    @('system', 'screen_off_timeout', '600000')
)
$savedSettings = @()
$originalActiveFont = Get-ActiveFont
try {
    $qemu = Invoke-Device -DeviceArgs @('shell', 'getprop', 'ro.kernel.qemu')
    if ($qemu -eq '1') { throw 'A physical Android device is required.' }
    foreach ($setting in $settingsToCheck) {
        $original = (Invoke-Device -DeviceArgs @('shell', 'settings', 'get', $setting[0], $setting[1])).Trim()
        $savedSettings += ,@($setting[0], $setting[1], $original)
        Invoke-Device -DeviceArgs @('shell', 'settings', 'put', $setting[0], $setting[1], $setting[2])
        $actual = (Invoke-Device -DeviceArgs @('shell', 'settings', 'get', $setting[0], $setting[1])).Trim()
        if ($actual -ne $setting[2]) { throw "Display setting was not applied: $setting" }
        Write-Output "$($setting[1])=$actual"
    }
    Invoke-Device -DeviceArgs @('shell', 'input', 'keyevent', '224')
    & .\gradlew.bat spotlessCheck lint connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.bubblespend.app.setup.SetupSettingsJourneyTest' --offline --no-parallel
    if ($LASTEXITCODE -ne 0) { throw 'Setup display journey tests failed.' }
    $imageDirectory = Join-Path $projectRoot 'docs/proofs/setup-settings/images'
    New-Item -ItemType Directory -Path $imageDirectory -Force | Out-Null
    foreach ($name in @('setup-large', 'setup-validation-large', 'setup-retry-large', 'settings-large')) {
        $outputRoot = Join-Path $projectRoot 'app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected'
        $artifact = Get-ChildItem -LiteralPath $outputRoot -Filter "$name.png" -File -Recurse | Select-Object -First 1
        if ($null -eq $artifact) { throw "Missing display screenshot: $name" }
        Copy-Item -LiteralPath $artifact.FullName -Destination (Join-Path $imageDirectory "$name.png")
    }
}
finally {
    foreach ($setting in $savedSettings) {
        if ($setting[2] -eq 'null') {
            if ($setting[1] -eq 'font_weight_adjustment') {
                Invoke-Device -DeviceArgs @('shell', 'settings', 'put', $setting[0], $setting[1], $originalActiveFont.Weight)
            }
            Invoke-Device -DeviceArgs @('shell', 'settings', 'delete', $setting[0], $setting[1])
        } else {
            Invoke-Device -DeviceArgs @('shell', 'settings', 'put', $setting[0], $setting[1], $setting[2])
        }
        $restored = (Invoke-Device -DeviceArgs @('shell', 'settings', 'get', $setting[0], $setting[1])).Trim()
        if ($restored -ne $setting[2]) { throw "Display setting was not restored: $setting" }
    }
    $restoredActiveFont = Get-ActiveFont
    if ($restoredActiveFont.Scale -ne $originalActiveFont.Scale -or $restoredActiveFont.Weight -ne $originalActiveFont.Weight) {
        throw 'Android active font size or weight was not restored.'
    }
    Write-Output "Active font restored: scale=$($restoredActiveFont.Scale), weight=$($restoredActiveFont.Weight)"
    Pop-Location
}
