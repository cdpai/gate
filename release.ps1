# Build cdpgate (Windows exe, macOS arm64 and x86_64 binaries) and cdpg (Windows exe, universal macOS binary) and, only when asked, publish them as a GitHub release and write its update file.
# Run from this folder:
#
#     .\release.ps1 -Version 0.2.1-beta.1             build and checksum only; nothing leaves this machine
#     .\release.ps1 -Version 0.2.1-beta.1 -Publish    also publish the GitHub release and write the update file
#
# -Version X.Y.Z is a stable release; X.Y.Z-label (0.2.1-beta.1) is a pre-release on the beta channel, which
# the stable channel, followed by every installed X.Y.Z, never sees.
#
# The build is made from a copy of HEAD (git archive), in a temporary folder, so it matches a public commit
# and never touches target\ here: a running cdpgate reads its jar from there. Nothing is installed anywhere.
#
# The release is v<Version> in github.com/cdpai/gate with: cdpgate.exe, cdpgate-<sha8>.jar,
# version.txt, SHA256SUMS. The update file cdpgate.json is written into the site working copy
# ..\..\leasegate\leasegate.github.io\cdpgate\update\ only after the release exists and every asset url answers; the script
# never commits or pushes the site. $env:GH names the GitHub CLI to use (default: gh).

param([Parameter(Mandatory)][string]$Version, [switch]$Publish)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$gh = if ($env:GH) { $env:GH } else { 'gh' }
$name = 'cdpgate'
$repo = 'cdpai/gate'
$tag = "v$Version"
$pre = $Version -match '-'
$site = Join-Path $PSScriptRoot "..\..\leasegate\leasegate.github.io\cdpgate\update"
$update = Join-Path $site "$name.json"

# Only lines shaped like git's own output are trusted: a git wrapper that picks an identity may print a banner.
function Sha([string]$ref) { @(git rev-parse $ref) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

if ($Publish) {
    $dirty = @(git status --porcelain) | Where-Object { $_ -match '^[ MADRCU?!]{2} ' }
    if ($dirty) { throw "the working tree is not clean - commit first, so the release matches a public commit:`n$($dirty -join "`n")" }
    git fetch -q origin
    if ((Sha HEAD) -ne (Sha '@{u}')) { throw 'HEAD is not pushed - push first' }
    if (-not (Test-Path (Join-Path $PSScriptRoot '..\..\leasegate\leasegate.github.io\.git'))) { throw "no site working copy at ..\..\leasegate\leasegate.github.io - the update file goes there" }
}

$work = Join-Path ([IO.Path]::GetTempPath()) "$name-release-$Version-$PID"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Path $work | Out-Null
$tar = Join-Path $work 'src.tar'
# A dry run may build uncommitted changes to tracked files (git stash create makes a commit object for them
# without touching the tree); a publish is refused above unless the tree is clean, so it builds HEAD exactly.
$ref = 'HEAD'
if (-not $Publish) {
    # git prints a line-ending warning on stderr, which Windows PowerShell 5.1 turns into an error under 'Stop'
    $ErrorActionPreference = 'Continue'
    $wip = Sha (@(git stash create) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1)
    $ErrorActionPreference = 'Stop'
    if ($wip) { $ref = $wip; Write-Host 'dry run: building the uncommitted working tree' }
}
git archive --format=tar -o $tar $ref
if ($LASTEXITCODE -ne 0) { throw 'git archive failed' }
if ($LASTEXITCODE -ne 0) { throw 'git archive failed' }
$src = Join-Path $work 'src'
New-Item -ItemType Directory -Path $src | Out-Null
tar -xf $tar -C $src
if ($LASTEXITCODE -ne 0) { throw 'tar failed' }

# -Djrexe.version names the exe's own version; -Djrexe.channel keeps a beta off the stable channel;
# -Djr.updateMergeFrom keeps the releases and channels already in the site's update file.
$mvnArgs = @('-B', '-q', '-DskipTests', '-Djrexe', '-Djr.source=url', "-Djrexe.version=$Version")
if ($pre) { $mvnArgs += '-Djrexe.channel=beta' }
$cliName = 'cdpg'
$cliUpdate = Join-Path $site "$cliName.json"
function Build([string[]]$more) {
    Push-Location $src
    try {
        # The network here drops connections mid-download often enough that one retry is not enough.
        for ($i = 1; $i -le 4; $i++) {
            & mvn @mvnArgs @more clean package
            if ($LASTEXITCODE -eq 0) { return }
            Write-Host "build attempt $i failed, retrying"
        }
        throw "mvn $more failed"
    } finally { Pop-Location }
}
Write-Host "building $tag from $(Sha HEAD)"
$all = Join-Path $work 'release'
New-Item -ItemType Directory -Path $all | Out-Null
$appOut = Join-Path $src 'app\target\jr\release'
# 1. cdpgate.exe with the Windows jar, and cdpg (no natives: one jar, cdpg.exe and a universal macOS binary, cli/pom.xml).
$merge = @(if (Test-Path $update) { "-Djr.updateMergeFrom=$((Resolve-Path $update).Path)" })
$cliMerge = @(if (Test-Path $cliUpdate) { "-Djr.updateMergeFrom=$((Resolve-Path $cliUpdate).Path)" })
Build ($merge + @('-pl', 'app', '-am'))
Copy-Item (Join-Path $appOut '*') $all
Build ($cliMerge + @('-pl', 'cli', '-am'))
Copy-Item (Join-Path $src 'cli\target\jr\release\*') $all -Force
# 2. One run per Mac architecture, each with that architecture's JavaFX natives in its own jar (cdpgate-<sha8>.jar,
# a new name since the bytes differ) and merging the update file of the run before, so the last lists every platform.
# AOT off on the Mac (the cdpgate.aot property in the parent pom).
foreach ($m in @(@('arm64', 'mac-aarch64'), @('x86_64', 'mac'))) {
    Build @('-pl', 'app', '-am', "-Djavafx.platform=$($m[1])", '-Djr.platforms=macos', "-Djr.macosArchitecture=$($m[0])",
            '-Dcdpgate.aot=false', "-Djr.updateMergeFrom=$(Join-Path $all "$name.update.json")")
    Copy-Item (Join-Path $appOut '*') $all -Force
}
$out = $all
# SHA256SUMS over everything gathered (each run wrote one for its own files only); the update files are not assets.
$sums = Get-ChildItem $out -File | Where-Object { $_.Name -notin @('SHA256SUMS', "$name.update.json", "$cliName.update.json") } | Sort-Object Name |
    ForEach-Object { "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower())  $($_.Name)" }
[IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))
foreach ($f in @("$name.exe", "$name-macos-arm64", "$name-macos-x86_64", "$cliName.exe", $cliName, 'version.txt', 'SHA256SUMS', "$name.update.json", "$cliName.update.json")) {
    if (-not (Test-Path (Join-Path $out $f))) { throw "the release folder has no $f - see the jr:exe output above" }
}
if (@(Get-ChildItem $out -Filter "$name-????????.jar").Count -ne 3) { throw "expected three $name jars (Windows, Apple silicon, Intel Mac)" }
Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-34} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if (-not $Publish) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$assets = (Get-ChildItem $out -File | Where-Object { $_.Name -notin @("$name.update.json", "$cliName.update.json") }).FullName
$notes = "$name $Version. Windows: download $name.exe and run it; it fetches its jar from this release, checked against the sha256 it carries. Update later with Check for updates in its window." +
         "`n`nMac, in Terminal (uname -m picks Apple silicon or Intel): curl -fLo /tmp/$name https://github.com/$repo/releases/download/$tag/$name-macos-`$(uname -m) && chmod +x /tmp/$name && /tmp/$name -Xjr:install" +
         "`n`n$cliName (the command line): $cliName.exe on Windows; on a Mac curl -fLo ~/.local/bin/$cliName https://github.com/$repo/releases/download/$tag/$cliName && chmod +x ~/.local/bin/$cliName"
if ($pre) { $notes += " This is a pre-release: installed stable copies are not offered it." }
$notesFile = Join-Path $work 'release-notes.md'
[IO.File]::WriteAllText($notesFile, $notes + "`n")
# No --latest=true for a stable release: GitHub makes the newest non-pre-release the latest by itself, and passing the flag from here failed "gh release create" (as in cfucli's release.ps1).
$latest = if ($pre) { @('--prerelease', '--latest=false') } else { @() }
# gh prints an ACCOUNT notice on stderr, which Windows PowerShell 5.1 turns into an error under 'Stop'
$ErrorActionPreference = 'Continue'
& $gh release create $tag $assets --repo $repo --target (Sha HEAD) --title "$name $Version" --notes-file $notesFile @latest
$ghExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($ghExit -ne 0) { throw 'gh release create failed' }
Write-Host "published $tag"

# GET every asset url BEFORE the update file goes anywhere: GitHub has served an asset with HTTP 500 on its
# public url while the API had it (cfucli 0.5.1).
$base = "https://github.com/$repo/releases/download/$tag"
foreach ($a in $assets) {
    $n = Split-Path $a -Leaf
    $tmp = Join-Path $work "check-$n"
    & curl.exe -fsSL -o $tmp "$base/$n"
    if ($LASTEXITCODE -ne 0) { throw "GET $base/$n failed - do not publish the update file" }
    if ((Get-Item $tmp).Length -ne (Get-Item $a).Length) { throw "$n differs in size from what was uploaded" }
    Remove-Item $tmp
}
Write-Host 'every asset url answers'

New-Item -ItemType Directory -Force -Path $site | Out-Null
Copy-Item (Join-Path $out "$name.update.json") $update -Force
Copy-Item (Join-Path $out "$cliName.update.json") $cliUpdate -Force
Write-Host "update files written to $update and $cliUpdate - commit and push the site repo to offer $Version to Check for updates"
