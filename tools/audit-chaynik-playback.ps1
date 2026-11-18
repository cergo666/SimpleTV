[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Install', 'Report', 'Remove')]
    [string] $Action,
    [string] $Root = 'C:\SimpleTVx64_LITE for ChayNik',
    [string] $Csv
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).ProviderPath
$scripts = Join-Path $Root 'luaScr'
$startup = Join-Path $scripts 'user/startup/simpletv_playback_audit.lua'
$eventFile = Join-Path $scripts 'user/simpletv_playback_audit_event.lua'
$log = Join-Path $Root 'simpletv-playback-audit.tsv'
$marker = '-- SimpleTV playback audit hook'

if (-not (Test-Path -LiteralPath (Join-Path $Root 'tv.exe') -PathType Leaf) -or
    -not (Test-Path -LiteralPath (Join-Path $scripts 'user/startup') -PathType Container)) {
    throw "ChayNik tv.exe or its Lua startup directory was not found in $Root"
}

if ($Action -ne 'Report') {
    foreach ($file in @($startup, $eventFile)) {
        if ((Test-Path -LiteralPath $file) -and
            (Get-Content -LiteralPath $file -TotalCount 1) -ne $marker) {
            throw "Refusing to replace an unrelated file at $file"
        }
    }
}

if ($Action -eq 'Install') {
    $lua = @'
-- SimpleTV playback audit hook
local output = m_simpleTV.MainScriptDir .. "../simpletv-playback-audit.tsv"
local lastRow

local function label(value)
    if type(value) ~= "string" or value:find("://", 1, true)
       or value:find("$OPT", 1, true) then return "" end
    return (value:gsub("[%c]", " "):gsub("^%s+", ""):gsub("%s+$", ""))
end

local function host(address)
    if type(address) ~= "string" then return "" end
    local authority = address:match("^%s*https?://([^/%?#%s$]+)")
    if not authority then return "" end
    authority = authority:gsub("^.*@", "")
    local result = authority:match("^(%[[a-fA-F0-9:]+%])") or
                   authority:match("^([a-zA-Z0-9.-]+):?%d*$")
    return result and result:lower():gsub("%.$", "") or ""
end

local function sourceName(state, key)
    if key == "" or not tvs_core or type(tvs_core.tvs_GetSourceParam) ~= "function" then return "" end
    local params = state.tmp and state.tmp.source
    if type(params) ~= "table" then
        local ok, result = pcall(tvs_core.tvs_GetSourceParam)
        if not ok or type(result) ~= "table" then return "" end
        params = result
    end
    local source = params[(key:gsub("#.*$", ""))]
    return type(source) == "table" and label(source.name) or ""
end

local function flag(get)
    if type(get) ~= "function" then return "" end
    local ok, value = pcall(get)
    return ok and type(value) == "boolean" and (value and "yes" or "no") or ""
end

local function inputHost(state, key, address)
    if key ~= "" and type(state.CurrentAdr) == "string" then
        local id = state.CurrentAdr:match("TVSources &id='?(.-)'?%[")
        local channel = id and state.CurT and state.CurT[id]
        local source = channel and channel[key]
        local original = host(source and source.Url or state.CurrentAdr)
        if original ~= "" then return original end
    end
    return host(address)
end

function SimpleTVPlaybackAuditEvent()
    local c = m_simpleTV.Control
    local reason = c.Reason
    if reason == "Stopped" or reason == "EndReached" then lastRow = nil; return end
    if reason ~= "addressready" and reason ~= "Playing" and
       reason ~= "Timeout" and reason ~= "Error" then return end
    local state = TVSources_var
    local key = ""
    if state and (state.IsTVS or state.IsTVSsrc) then
        key = tostring(state.CurrentSource or ""):match("^([%w_#.-]+)$") or ""
    end
    local requestedHost = inputHost(state, key, c.CurrentAddress)
    local playedHost = host(c.RealAddress)
    local fields = {reason, key, sourceName(state, key), label(c.CurrentTitle_UTF8),
                    requestedHost, playedHost, flag(c.IsVideo), flag(c.IsAD), flag(c.GetScrambled)}
    local row = table.concat(fields, "\t")
    if row == lastRow then return end
    local file = io.open(output, "ab")
    if not file then return end
    file:write(os.date("%Y-%m-%dT%H:%M:%S"), "\t", row, "\n")
    file:close()
    lastRow = row
end

AddFileToExecute("events", m_simpleTV.MainScriptDir .. "user/simpletv_playback_audit_event.lua")
'@
    $eventLua = @'
-- SimpleTV playback audit hook
if SimpleTVPlaybackAuditEvent then pcall(SimpleTVPlaybackAuditEvent) end
'@
    Set-Content -LiteralPath $eventFile -Encoding Ascii -Value $eventLua
    Set-Content -LiteralPath $startup -Encoding Ascii -Value $lua
    Write-Host 'Hook installed. Fully exit and reopen ChayNik tv.exe, then play each provider manually.'
    Write-Host "The log will appear at: $log"
    return
}

if ($Action -eq 'Remove') {
    foreach ($file in @($startup, $eventFile)) {
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    Write-Host "Hook removed. Restart ChayNik. The report file is kept at: $log"
    return
}

if (-not (Test-Path -LiteralPath $log -PathType Leaf)) {
    throw "No log at $log. Install the hook, restart ChayNik and play a channel."
}

function Get-DeliveryHint([string] $StreamHost) {
    switch -Regex ($StreamHost) {
        '(^|\.)wink\.ru$' { return 'Wink domain' }
        '(^|\.)zabava-htlive(?:-lb)?\.cdn\.ngenix\.net$|(?:^|-)zabava-htlive(?:-lb)?\.cdn\.ngenix\.net$' { return 'Zabava CDN' }
        '(^|\.)ngenix\.net$' { return 'NGENIX CDN (origin unknown)' }
        '(^|\.)lfstrm\.tv$' { return 'LifeStream (shared platform)' }
        '(^|\.)mediavitrina\.ru$' { return 'MediaVitrina domain' }
        '^$|^localhost$|^127\.|^\[::1\]$' { return 'Upstream host unseen' }
        default { return 'Other host (origin unconfirmed)' }
    }
}

$columns = 'Time', 'Event', 'SourceKey', 'Provider', 'Channel', 'InputHost', 'PlayerHost', 'Video', 'Ad', 'Scrambled'
$rows = @(Get-Content -LiteralPath $log -Encoding UTF8 |
    Where-Object { ($_ -split "`t").Count -eq $columns.Count } |
    ConvertFrom-Csv -Delimiter "`t" -Header $columns |
    Select-Object *, @{ Name = 'Delivery'; Expression = { Get-DeliveryHint $_.PlayerHost } })
if ($rows.Count -eq 0) { throw "No complete playback events in $log" }

if ($Csv) {
    $rows | Export-Csv -LiteralPath $Csv -NoTypeInformation -Encoding UTF8
    Write-Host "Exported $($rows.Count) playback events to $Csv"
}
$rows | Format-Table Event, @{ Label = 'Provider'; Expression = {
    if ($_.Provider) { $_.Provider } else { $_.SourceKey }
} }, Channel, PlayerHost, Delivery -AutoSize -Wrap
