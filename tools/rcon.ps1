param(
    [Parameter(Mandatory = $true)][string]$Command,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 25575,
    [Parameter(Mandatory = $true)][string]$Password,
    [int]$TimeoutMs = 15000
)

$ErrorActionPreference = 'Stop'

function Send-Packet {
    param($Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $payload = New-Object System.Collections.Generic.List[byte]
    $payload.AddRange([BitConverter]::GetBytes([int]($bodyBytes.Length + 10)))
    $payload.AddRange([BitConverter]::GetBytes([int]$Id))
    $payload.AddRange([BitConverter]::GetBytes([int]$Type))
    $payload.AddRange($bodyBytes)
    $payload.Add(0)
    $payload.Add(0)
    $Stream.Write($payload.ToArray(), 0, $payload.Count)
    $Stream.Flush()
}

function Read-Packet {
    param($Stream)
    $lenBytes = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $Stream.Read($lenBytes, $read, 4 - $read)
        if ($n -le 0) { throw '连接被关闭' }
        $read += $n
    }
    $len = [BitConverter]::ToInt32($lenBytes, 0)
    $buf = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $Stream.Read($buf, $read, $len - $read)
        if ($n -le 0) { throw '连接被关闭' }
        $read += $n
    }
    $id = [BitConverter]::ToInt32($buf, 0)
    $type = [BitConverter]::ToInt32($buf, 4)
    $text = [Text.Encoding]::UTF8.GetString($buf, 8, $len - 10)
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $text }
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($HostName, $Port)
$client.ReceiveTimeout = $TimeoutMs
$stream = $client.GetStream()

Send-Packet -Stream $stream -Id 1 -Type 3 -Body $Password
$auth = Read-Packet -Stream $stream
if ($auth.Id -eq -1) { $client.Close(); throw 'RCON 密码错误' }

Send-Packet -Stream $stream -Id 2 -Type 2 -Body $Command
$response = Read-Packet -Stream $stream
$client.Close()
$response.Body
