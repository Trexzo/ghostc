#requires -Version 5.1
<#
.SYNOPSIS
  Verify a GhostC CI Forge bundle and optionally prepare one Minecraft profile.
.DESCRIPTION
  No downloads, game launch, private-log access or gameplay certification.
  Verify mode is read-only. Requires the exact CI source revision.
#>
[CmdletBinding()]
param(
    [ValidateSet('Verify','Prepare')][string]$Mode = 'Verify',
    [Parameter(Mandatory=$true)][string]$BundleDir,
    [Parameter(Mandatory=$true)][string]$ExpectedSourceRevision,
    [string]$GameDir
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Require-Regular([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing expected bundle file: $([IO.Path]::GetFileName($Path))"
    }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Refusing linked bundle file: $($item.Name)"
    }
}
function Require-Directory([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Container)) {
        throw 'Expected a real bundle directory'
    }
    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Refusing linked bundle directory'
    }
}
if ($ExpectedSourceRevision -cnotmatch '^[a-f0-9]{40}$') {
    throw 'ExpectedSourceRevision must be the exact lowercase GitHub commit SHA'
}
if ($Mode -eq 'Prepare' -and [string]::IsNullOrWhiteSpace($GameDir)) {
    throw 'Prepare requires an explicitly selected Minecraft -GameDir'
}
$root = [IO.Path]::GetFullPath($BundleDir)
Require-Directory $root
$libs = Join-Path $root 'forge189/build/libs'
if (!(Test-Path -LiteralPath $libs -PathType Container)) {
    $libs = $root
} else {
    Require-Directory (Join-Path $root 'forge189')
    Require-Directory (Join-Path $root 'forge189/build')
}
Require-Directory $libs
$jars = @(Get-ChildItem -LiteralPath $libs -File | Where-Object {
    $_.Name -cmatch '^ghostc-forge189-[0-9]+\.[0-9]+\.[0-9]+\.jar$'
})
if ($jars.Count -ne 1) {
    throw "Bundle must contain exactly one versioned GhostC Forge JAR; found $($jars.Count)"
}
$jar = $jars[0]
$manifestPath = Join-Path $libs 'GHOSTC_BUILD_MANIFEST.json'
$sumsPath = Join-Path $libs 'SHA256SUMS.txt'
Require-Regular $jar.FullName
Require-Regular $manifestPath
Require-Regular $sumsPath
if ((Get-Item -LiteralPath $manifestPath).Length -gt 16384 -or
    (Get-Item -LiteralPath $sumsPath).Length -gt 512) {
    throw 'Build metadata exceeds safe size budget'
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedFields = @(
    'artifact','artifact_bytes','artifact_sha256','java_class_major',
    'minecraft','platform','schema','source_revision',
    'source_revision_kind','status','verified_java_classes'
)
$fields = @($manifest.PSObject.Properties.Name | Sort-Object)
if (($fields -join '|') -cne (($expectedFields | Sort-Object) -join '|')) {
    throw 'Unexpected build manifest fields'
}
if ($manifest.schema -ne 1 -or
    $manifest.status -cne 'CI_PACKAGE_ONLY_NOT_TESTED_IN_MINECRAFT' -or
    $manifest.source_revision_kind -cne 'CI_CHECKED_OUT_COMMIT' -or
    $manifest.source_revision -cne $ExpectedSourceRevision -or
    $manifest.platform -cne 'windows' -or
    $manifest.minecraft -cne '1.8.9' -or
    $manifest.java_class_major -ne 52 -or
    $manifest.verified_java_classes -lt 1 -or
    $manifest.artifact -cne $jar.Name -or
    ($manifest.artifact_bytes -isnot [long] -and
     $manifest.artifact_bytes -isnot [int])) {
    throw 'Forge bundle provenance does not match the requested Windows build'
}
if ([long]$manifest.artifact_bytes -ne $jar.Length -or
    $manifest.artifact_sha256 -cnotmatch '^[a-f0-9]{64}$') {
    throw 'Forge bundle size or digest metadata is invalid'
}
$sha = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sha -cne $manifest.artifact_sha256) {
    throw 'Forge JAR bytes differ from CI manifest; profile unchanged'
}
$sums = @(Get-Content -LiteralPath $sumsPath -Encoding Ascii)
if ($sums.Count -ne 1 -or $sums[0] -cne "$sha  $($jar.Name)") {
    throw 'SHA256SUMS and Forge JAR differ; profile unchanged'
}
$version = [regex]::Match($jar.Name,
    '^ghostc-forge189-([0-9]+\.[0-9]+\.[0-9]+)\.jar$').Groups[1].Value
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    foreach ($entryName in @(
        'dev/ghostfusion/forge189/GhostCMod.class',
        'dev/ghostfusion/forge189/ForgeEventAdapter.class',
        'dev/ghostfusion/forge189/GhostClickGui.class',
        'dev/ghostfusion/host/HostRuntime.class'
    )) {
        if ($null -eq $zip.GetEntry($entryName)) {
            throw "Missing Forge class: $entryName"
        }
    }
    $metadata = $zip.GetEntry('mcmod.info')
    if ($null -eq $metadata -or $metadata.Length -gt 8192) {
        throw 'Missing or oversized Forge mod metadata'
    }
    $reader = New-Object IO.StreamReader($metadata.Open())
    try { $modsInfo = @($reader.ReadToEnd() | ConvertFrom-Json) }
    finally { $reader.Dispose() }
    if ($modsInfo.Count -ne 1 -or
        $modsInfo[0].modid -cne 'ghostc' -or
        $modsInfo[0].mcversion -cne '1.8.9' -or
        $modsInfo[0].version -cne $version) {
        throw 'Forge mod metadata and artifact version differ'
    }
}
finally { $zip.Dispose() }
$helper = Join-Path $root 'tools/Invoke-GhostCAcceptance.ps1'
if (!(Test-Path -LiteralPath $helper -PathType Leaf)) {
    $helper = Join-Path $root 'Invoke-GhostCAcceptance.ps1'
}
Require-Regular $helper
Write-Output "GHOSTC_NATIVE_PREFLIGHT_PASS version=$version revision=$ExpectedSourceRevision sha256=$sha status=CI_PACKAGE_ONLY_NOT_TESTED_IN_MINECRAFT"
if ($Mode -eq 'Verify') {
    Write-Output 'GHOSTC_VERIFY_ONLY: No Minecraft profile changed. No gameplay verified.'
    exit 0
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $helper -Mode Prepare -GameDir $GameDir -JarPath $jar.FullName -ExpectedSha256 $sha
if ($LASTEXITCODE -ne 0) {
    throw 'Transactional GhostC acceptance Prepare failed'
}
Write-Output 'GHOSTC_NATIVE_PREPARE_COMPLETE: Installation only; launch and inspect Minecraft manually.'
