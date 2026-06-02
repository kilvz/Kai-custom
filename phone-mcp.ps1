param([string]$cmd = "status", [string]$a1 = "", [string]$a2 = "", [string]$a3 = "")

$MCP = "http://127.0.0.1:18316/mcp"

function rpc {
    param([string]$json)
    $f = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($f, $json)
    curl.exe -s -X POST $MCP -d "@$f" 2>&1
    Remove-Item $f -EA 0
}

if ($cmd -eq "health") {
    curl.exe -s http://127.0.0.1:18316/health 2>&1
    exit
}

if ($cmd -eq "tools") {
    rpc '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
    exit
}

if ($cmd -eq "status") {
    rpc '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_status","arguments":{}}}'
    exit
}

if ($cmd -eq "search") {
    $q = $a1; $m = $a2; $n = $a3
    if ($q -eq "") { Write-Host "usage: phone-mcp search `"query`" [mode=vector] [n_results=5]"; exit 1 }
    if ($m -eq "") { $m = "vector" }
    if ($n -eq "") { $n = "5" }
    $body = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search","arguments":{"query":"' + $q + '","mode":"' + $m + '","n_results":' + $n + '}}}'
    rpc $body
    exit
}

if ($cmd -eq "add") {
    $body = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"batch_add_entities","arguments":{"entities":[{"realm":"persona_kai","domain":"memories","content":"User loves Chinese food, orders from Golden Dragon on weekends"},{"realm":"persona_kai","domain":"memories","content":"User likes Kotlin and works on Android development"},{"realm":"persona_kai","domain":"memories","content":"User lives in Pacific timezone, prefers mornings"},{"realm":"persona_kai","domain":"memories","content":"User has a 3-year-old cat named Luna who plays with yarn"}]}}}'
    rpc $body
    exit
}

if ($cmd -eq "reindex") {
    $m = if ($a1 -eq "") { "numpy" } else { $a1 }
    $body = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"set_embedder","arguments":{"model":"' + $m + '","reindex":true}}}'
    rpc $body
    exit
}

if ($cmd -eq "call") {
    $body = '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"' + $a1 + '","arguments":{}}}'
    rpc $body
    exit
}

Write-Host "commands: health, tools, status, search, add, reindex, call"
