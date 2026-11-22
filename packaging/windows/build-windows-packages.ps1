param(
    [string]$Version = "",
    [string]$OutputDir = "out",
    [int]$BuildNumber = 1,
    [string]$MpvSha256 = "30d3f9fa121ce24638610a9ae52f8957743f9dea416dd37dc4ff7e43589ef843"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $ProjectRoot

if ([string]::IsNullOrWhiteSpace($Version)) {
    $VersionFile = Join-Path $ProjectRoot "version.properties"
    if (!(Test-Path $VersionFile)) { throw "version.properties not found: $VersionFile" }
    $match = Select-String -Path $VersionFile -Pattern '^versionName=(.+)$' | Select-Object -First 1
    if (!$match) { throw "versionName is missing from $VersionFile" }
    $Version = $match.Matches[0].Groups[1].Value.Trim()
}
$Version = $Version -creplace '^v', ''
if ($Version -cnotmatch '^(?:\d+\.\d+\.\d+|dev-\d+)$') {
    throw "Invalid package version: $Version"
}

Write-Host "=== Building SimpleTV Windows Packages (Version: $Version) ===" -ForegroundColor Cyan

$DistDir = "$ProjectRoot\desktop\build\windows\x64\runner\Release"
$SymbolsDir = "$ProjectRoot\desktop\build\release-symbols\windows\$Version-$BuildNumber"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Write-Host "[1/3] Building Flutter Windows application in release mode..." -ForegroundColor Yellow
Push-Location "$ProjectRoot\desktop"
try {
    $buildArgs = @('--obfuscate', "--split-debug-info=$SymbolsDir", '--build-number', "$BuildNumber")
    if ($Version -notlike 'dev-*') {
        $buildArgs += @('--build-name', $Version)
    }
    & flutter build windows --release @buildArgs
    if ($LASTEXITCODE -ne 0) { throw "Flutter build failed ($LASTEXITCODE)." }
    if (!(Get-ChildItem -LiteralPath $SymbolsDir -File | Where-Object { $_.Extension -in '.pdb', '.symbols' })) {
        throw "Flutter did not produce split debug information."
    }
    & dart tool/pack_release_playlists.dart "$DistDir\data\flutter_assets"
    if ($LASTEXITCODE -ne 0) { throw "Playlist packing failed ($LASTEXITCODE)." }
} finally {
    Pop-Location
}
if (Get-ChildItem -LiteralPath $DistDir -File -Recurse |
    Where-Object { $_.Extension -in '.pdb', '.symbols', '.debug', '.map' }) {
    throw "Debug symbols or maps found inside the Windows distribution."
}

# Ensure modern crash-free libmpv-2.dll (with DASH CENC ClearKey fix)
$LocalNewMpv = "$ProjectRoot\out\new_mpv\libmpv-2.dll"
$TargetMpv = "$DistDir\libmpv-2.dll"
if (Test-Path $LocalNewMpv) {
    Write-Host "Updating libmpv-2.dll from local modern build..." -ForegroundColor Cyan
    Copy-Item $LocalNewMpv $TargetMpv -Force
} else {
    $needsUpdate = $true
    if (Test-Path $TargetMpv) {
        $len = (Get-Item $TargetMpv).Length
        if ($len -gt 50000000) { $needsUpdate = $false }
    }
    if ($needsUpdate) {
        Write-Host "Downloading modern libmpv-2.dll (2026 build with DASH fix)..." -ForegroundColor Cyan
        $TempZip = "$ProjectRoot\out\mpv-modern.7z"
        $TempExtract = "$ProjectRoot\out\mpv-temp"
        New-Item -ItemType Directory -Force -Path $TempExtract | Out-Null
        Invoke-WebRequest -Uri "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261101/mpv-dev-x86_64-20261101-git-48fca90e8c.7z" -OutFile $TempZip
        $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $TempZip).Hash.ToLowerInvariant()
        if ($actualHash -ne $MpvSha256.ToLowerInvariant()) {
            throw "libmpv archive checksum mismatch: expected $MpvSha256, got $actualHash"
        }
        $7zExe = if (Test-Path "C:\Program Files\7-Zip\7z.exe") { "C:\Program Files\7-Zip\7z.exe" } else { "7z" }
        & $7zExe e -y -o"$TempExtract" "$TempZip" "libmpv-2.dll" | Out-Null
        if (Test-Path "$TempExtract\libmpv-2.dll") {
            New-Item -ItemType Directory -Force -Path "$ProjectRoot\out\new_mpv" | Out-Null
            Copy-Item "$TempExtract\libmpv-2.dll" "$ProjectRoot\out\new_mpv\libmpv-2.dll" -Force
            Copy-Item "$TempExtract\libmpv-2.dll" $TargetMpv -Force
        }
        Remove-Item -LiteralPath "$TempExtract\libmpv-2.dll", $TempZip -Force -ErrorAction SilentlyContinue
    }
}

# Include the x64 C++ runtime alongside Flutter's DLLs.
$VsWhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$VsPath = & $VsWhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
$Crt = Get-ChildItem -Path "$VsPath\VC\Redist\MSVC\*\x64\Microsoft.VC*.CRT" -Directory |
    Sort-Object { [version]$_.Parent.Parent.Name } -Descending | Select-Object -First 1
if (!$Crt) { throw "The Visual Studio x64 C++ redistributable files were not found." }
Get-ChildItem -LiteralPath $Crt.FullName -Filter '*.dll' -File |
    Copy-Item -Destination $DistDir -Force

# 2. Build Inno Setup Installer (.exe)
Write-Host "[2/3] Building Inno Setup installer..." -ForegroundColor Yellow
$IsccPaths = @(
    "iscc",
    "C:\Program Files (x86)\Inno Setup 6\iscc.exe",
    "C:\Program Files\Inno Setup 6\iscc.exe",
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\iscc.exe"
)

$IsccExe = $null
foreach ($path in $IsccPaths) {
    if (Get-Command $path -ErrorAction SilentlyContinue) {
        $IsccExe = $path
        break
    } elseif (Test-Path $path) {
        $IsccExe = $path
        break
    }
}

$SetupFilename = "SimpleTV-$Version-Setup"
$FullOutputDir = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot $OutputDir))
$FullDistDir = [System.IO.Path]::GetFullPath($DistDir)

if ($IsccExe) {
    Write-Host "Found Inno Setup compiler: $IsccExe"
    & $IsccExe "/DAppVersion=$Version" "/DSourceDir=$FullDistDir" "/DOutputDir=$FullOutputDir" "/DOutputBaseFilename=$SetupFilename" "$PSScriptRoot\SimpleTV.iss"
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed ($LASTEXITCODE)." }
    Write-Host "Inno Setup installer created: $OutputDir\$SetupFilename.exe" -ForegroundColor Green
} else {
    Write-Warning "Inno Setup (iscc.exe) not found. Skipping installer compilation."
}

# Both portable formats use the same release payload.
Write-Host "[3/3] Building portable EXE and ZIP..." -ForegroundColor Yellow
$ZipTarget = Join-Path $FullOutputDir "SimpleTV-$Version-Windows-Portable.zip"
$SingleExeTarget = Join-Path $FullOutputDir "SimpleTV-$Version-Portable.exe"
$PendingZip = Join-Path $FullOutputDir (".portable-" + [guid]::NewGuid().ToString("N") + ".zip")
$PendingExe = [System.IO.Path]::ChangeExtension($PendingZip, ".exe")
$Compiler = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (!(Test-Path $Compiler)) {
    throw "C# compiler not found: $Compiler. Install .NET Framework 4.x or build the portable launcher with the .NET SDK."
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $DistDir, $PendingZip, [System.IO.Compression.CompressionLevel]::Optimal, $false
    )
    $compilerArgs = @(
        '/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/codepage:65001',
        '/reference:System.IO.Compression.dll', '/reference:System.IO.Compression.FileSystem.dll',
        '/reference:System.Windows.Forms.dll', "/win32icon:$PSScriptRoot\..\icon.ico",
        "/resource:$PendingZip,SimpleTV.zip", "/out:$PendingExe", "$PSScriptRoot\PortableLauncher.cs"
    )
    & $Compiler @compilerArgs
    if ($LASTEXITCODE -ne 0) { throw "Portable launcher compilation failed ($LASTEXITCODE)." }

    Move-Item -LiteralPath $PendingExe -Destination $SingleExeTarget -Force
    Move-Item -LiteralPath $PendingZip -Destination $ZipTarget -Force
} finally {
    Remove-Item -LiteralPath $PendingZip, $PendingExe -Force -ErrorAction SilentlyContinue
}
Get-Item -LiteralPath $SingleExeTarget, $ZipTarget
