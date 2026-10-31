$ErrorActionPreference = 'Stop'

$projectRoot = (Get-Location).Path
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'pom.xml'))) {
    throw 'Run this script from the banking-system project root (the folder containing pom.xml).'
}

# All edits are prepared and validated in memory first. If any expected code anchor
# is absent or ambiguous, the script stops before writing any source files.
$script:Updates = @{}
$script:NewFiles = @{}

function Decode-Text {
    param([Parameter(Mandatory)][string]$Encoded)
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Encoded))
}

function Get-UpdateEntry {
    param([Parameter(Mandatory)][string]$RelativePath)
    if ($script:Updates.ContainsKey($RelativePath)) {
        return $script:Updates[$RelativePath]
    }
    $fullPath = Join-Path $projectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Expected existing file was not found: $RelativePath"
    }
    $raw = [System.IO.File]::ReadAllText($fullPath)
    $lineEnding = if ($raw.Contains("`r`n")) { "`r`n" } else { "`n" }
    $normalized = $raw.Replace("`r`n", "`n")
    $entry = [pscustomobject]@{
        RelativePath = $RelativePath
        FullPath = $fullPath
        Text = $normalized
        LineEnding = $lineEnding
    }
    $script:Updates[$RelativePath] = $entry
    return $entry
}

function Replace-Once {
    param(
        [Parameter(Mandatory)][string]$RelativePath,
        [Parameter(Mandatory)][string]$Old,
        [Parameter(Mandatory)][AllowEmptyString()][string]$New
    )
    $entry = Get-UpdateEntry $RelativePath
    $oldText = $Old.Replace("`r`n", "`n")
    $newText = $New.Replace("`r`n", "`n")
    $first = $entry.Text.IndexOf($oldText, [System.StringComparison]::Ordinal)
    if ($first -lt 0) {
        throw "Could not find the expected code anchor in $RelativePath. No source files have been written."
    }
    $second = $entry.Text.IndexOf($oldText, $first + $oldText.Length, [System.StringComparison]::Ordinal)
    if ($second -ge 0) {
        throw "Code anchor is ambiguous in $RelativePath. No source files have been written."
    }
    $entry.Text = $entry.Text.Substring(0, $first) + $newText + $entry.Text.Substring($first + $oldText.Length)
}

function Add-NewFile {
    param([Parameter(Mandatory)][string]$RelativePath, [Parameter(Mandatory)][string]$Content)
    $fullPath = Join-Path $projectRoot $RelativePath
    if (Test-Path -LiteralPath $fullPath) {
        throw "Refusing to overwrite an existing file: $RelativePath"
    }
    $script:NewFiles[$RelativePath] = $Content.Replace("`r`n", "`n")
}

# 1) Make POST submit durable work and return immediately with its request code/status.
Replace-Once 'src/main/java/com/example/banking/transaction/controller/TransactionController.java' (Decode-Text 'ICAgICAgICBwcm9jZXNzb3IucHJvY2Vzc1JlcXVlc3QocmVzdWx0LnJlcXVlc3QoKS5nZXRJZCgpKTsK') (Decode-Text '')
Replace-Once 'src/main/java/com/example/banking/transaction/controller/TransactionController.java' 'HttpStatus.CREATED' 'HttpStatus.ACCEPTED'

# 2) Repository queue queries for pending requests and expired leases.
Replace-Once 'src/main/java/com/example/banking/transaction/repository/TransactionRequestRepository.java' (Decode-Text 'aW1wb3J0IGNvbS5leGFtcGxlLmJhbmtpbmcudHJhbnNhY3Rpb24uZW50aXR5LlRyYW5zYWN0aW9uUmVxdWVzdDsK') (Decode-Text 'aW1wb3J0IGNvbS5leGFtcGxlLmJhbmtpbmcudHJhbnNhY3Rpb24uZW50aXR5LlRyYW5zYWN0aW9uUmVxdWVzdDsKaW1wb3J0IGNvbS5leGFtcGxlLmJhbmtpbmcudHJhbnNhY3Rpb24uZW50aXR5LlRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1czsK')
Replace-Once 'src/main/java/com/example/banking/transaction/repository/TransactionRequestRepository.java' (Decode-Text 'aW1wb3J0IGphdmEudXRpbC5PcHRpb25hbDsK') (Decode-Text 'aW1wb3J0IGphdmEudGltZS5PZmZzZXREYXRlVGltZTsKaW1wb3J0IGphdmEudXRpbC5MaXN0OwppbXBvcnQgamF2YS51dGlsLk9wdGlvbmFsOwo=')
Replace-Once 'src/main/java/com/example/banking/transaction/repository/TransactionRequestRepository.java' (Decode-Text 'ICAgIE9wdGlvbmFsPFRyYW5zYWN0aW9uUmVxdWVzdD4gZmluZEJ5UmVxdWVzdENvZGUoU3RyaW5nIHJlcXVlc3RDb2RlKTsK') (Decode-Text 'ICAgIE9wdGlvbmFsPFRyYW5zYWN0aW9uUmVxdWVzdD4gZmluZEJ5UmVxdWVzdENvZGUoU3RyaW5nIHJlcXVlc3RDb2RlKTsKCiAgICBMaXN0PFRyYW5zYWN0aW9uUmVxdWVzdD4gZmluZFRvcDIwQnlTdGF0dXNBbmROZXh0QXR0ZW1wdEF0TGVzc1RoYW5FcXVhbE9yZGVyQnlDcmVhdGVkQXRBc2MoCiAgICAgICAgICAgIFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cyBzdGF0dXMsIE9mZnNldERhdGVUaW1lIG5vdyk7CgogICAgTGlzdDxUcmFuc2FjdGlvblJlcXVlc3Q+IGZpbmRUb3AyMEJ5U3RhdHVzQW5kTGVhc2VVbnRpbExlc3NUaGFuRXF1YWxPcmRlckJ5Q3JlYXRlZEF0QXNjKAogICAgICAgICAgICBUcmFuc2FjdGlvblJlcXVlc3RTdGF0dXMgc3RhdHVzLCBPZmZzZXREYXRlVGltZSBub3cpOwo=')

# 3) Persist retry scheduling data on the request entity.
Replace-Once 'src/main/java/com/example/banking/transaction/entity/TransactionRequest.java' (Decode-Text 'ICAgIHB1YmxpYyB2b2lkIG1hcmtQcm9jZXNzaW5nKFN0cmluZyB3b3JrZXIpIHsKICAgICAgICBzdGF0dXMgPSBUcmFuc2FjdGlvblJlcXVlc3RTdGF0dXMuUFJPQ0VTU0lORzsKICAgICAgICBhdHRlbXB0Q291bnQrKzsKICAgICAgICB3b3JrZXJJZCA9IHdvcmtlcjsKICAgICAgICBsZWFzZVVudGlsID0gT2Zmc2V0RGF0ZVRpbWUubm93KFpvbmVPZmZzZXQuVVRDKS5wbHVzTWludXRlcygyKTsKICAgICAgICBmYWlsdXJlQ29kZSA9IG51bGw7CiAgICB9Cg==') (Decode-Text 'ICAgIHB1YmxpYyB2b2lkIG1hcmtQcm9jZXNzaW5nKFN0cmluZyB3b3JrZXIpIHsKICAgICAgICBzdGF0dXMgPSBUcmFuc2FjdGlvblJlcXVlc3RTdGF0dXMuUFJPQ0VTU0lORzsKICAgICAgICBhdHRlbXB0Q291bnQrKzsKICAgICAgICB3b3JrZXJJZCA9IHdvcmtlcjsKICAgICAgICBsZWFzZVVudGlsID0gT2Zmc2V0RGF0ZVRpbWUubm93KFpvbmVPZmZzZXQuVVRDKS5wbHVzTWludXRlcygyKTsKICAgICAgICBmYWlsdXJlQ29kZSA9IG51bGw7CiAgICB9CgogICAgcHVibGljIHZvaWQgc2NoZWR1bGVSZXRyeShPZmZzZXREYXRlVGltZSByZXRyeUF0LCBTdHJpbmcgY29kZSkgewogICAgICAgIHN0YXR1cyA9IFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cy5QRU5ESU5HOwogICAgICAgIG5leHRBdHRlbXB0QXQgPSByZXRyeUF0OwogICAgICAgIHdvcmtlcklkID0gbnVsbDsKICAgICAgICBsZWFzZVVudGlsID0gbnVsbDsKICAgICAgICBmYWlsdXJlQ29kZSA9IGNvZGU7CiAgICB9Cg==')

# 4) Claim work in a short, separate transaction; persist retries with exponential backoff.
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionRequestService.java' (Decode-Text 'aW1wb3J0IG9yZy5zcHJpbmdmcmFtZXdvcmsuc3RlcmVvdHlwZS5TZXJ2aWNlOwo=') (Decode-Text 'aW1wb3J0IG9yZy5zcHJpbmdmcmFtZXdvcmsuYmVhbnMuZmFjdG9yeS5hbm5vdGF0aW9uLlZhbHVlOwppbXBvcnQgb3JnLnNwcmluZ2ZyYW1ld29yay5zdGVyZW90eXBlLlNlcnZpY2U7Cg==')
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionRequestService.java' (Decode-Text 'aW1wb3J0IG9yZy5zcHJpbmdmcmFtZXdvcmsudHJhbnNhY3Rpb24uYW5ub3RhdGlvbi5UcmFuc2FjdGlvbmFsOwo=') (Decode-Text 'aW1wb3J0IG9yZy5zcHJpbmdmcmFtZXdvcmsudHJhbnNhY3Rpb24uYW5ub3RhdGlvbi5UcmFuc2FjdGlvbmFsOwoKaW1wb3J0IGphdmEudGltZS5PZmZzZXREYXRlVGltZTsKaW1wb3J0IGphdmEudGltZS5ab25lT2Zmc2V0Owo=')
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionRequestService.java' (Decode-Text 'ICAgIHByaXZhdGUgZmluYWwgVHJhbnNhY3Rpb25SZXF1ZXN0UmVwb3NpdG9yeSByZXBvc2l0b3J5Owo=') (Decode-Text 'ICAgIHByaXZhdGUgZmluYWwgVHJhbnNhY3Rpb25SZXF1ZXN0UmVwb3NpdG9yeSByZXBvc2l0b3J5OwoKICAgIEBWYWx1ZSgiJHtiYW5raW5nLndvcmtlci5tYXgtYXR0ZW1wdHM6NX0iKQogICAgcHJpdmF0ZSBpbnQgbWF4QXR0ZW1wdHM7CgogICAgQFZhbHVlKCIke2Jhbmtpbmcud29ya2VyLmJhc2UtYmFja29mZi1zZWNvbmRzOjJ9IikKICAgIHByaXZhdGUgbG9uZyBiYXNlQmFja29mZlNlY29uZHM7Cg==')
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionRequestService.java' (Decode-Text 'ICAgIHB1YmxpYyBUcmFuc2FjdGlvblJlcXVlc3RTZXJ2aWNlKFRyYW5zYWN0aW9uUmVxdWVzdFJlcG9zaXRvcnkgcmVwb3NpdG9yeSkgewogICAgICAgIHRoaXMucmVwb3NpdG9yeSA9IHJlcG9zaXRvcnk7CiAgICB9Cg==') (Decode-Text 'ICAgIHB1YmxpYyBUcmFuc2FjdGlvblJlcXVlc3RTZXJ2aWNlKFRyYW5zYWN0aW9uUmVxdWVzdFJlcG9zaXRvcnkgcmVwb3NpdG9yeSkgewogICAgICAgIHRoaXMucmVwb3NpdG9yeSA9IHJlcG9zaXRvcnk7CiAgICB9CgogICAgQFRyYW5zYWN0aW9uYWwKICAgIHB1YmxpYyBib29sZWFuIGNsYWltRm9yUHJvY2Vzc2luZyhMb25nIHJlcXVlc3RJZCwgU3RyaW5nIHdvcmtlcklkKSB7CiAgICAgICAgVHJhbnNhY3Rpb25SZXF1ZXN0IHJlcXVlc3QgPSByZXBvc2l0b3J5LmZpbmRCeUlkRm9yVXBkYXRlKHJlcXVlc3RJZCkKICAgICAgICAgICAgICAgIC5vckVsc2VUaHJvdygoKSAtPiBuZXcgUmVzb3VyY2VOb3RGb3VuZEV4Y2VwdGlvbigiVHJhbnNhY3Rpb24gcmVxdWVzdCBub3QgZm91bmQuIikpOwogICAgICAgIFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cyBzdGF0dXMgPSByZXF1ZXN0LmdldFN0YXR1cygpOwogICAgICAgIGlmIChzdGF0dXMgPT0gVHJhbnNhY3Rpb25SZXF1ZXN0U3RhdHVzLkNPTVBMRVRFRAogICAgICAgICAgICAgICAgfHwgc3RhdHVzID09IFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cy5GQUlMRUQpIHsKICAgICAgICAgICAgcmV0dXJuIGZhbHNlOwogICAgICAgIH0KCiAgICAgICAgT2Zmc2V0RGF0ZVRpbWUgbm93ID0gT2Zmc2V0RGF0ZVRpbWUubm93KFpvbmVPZmZzZXQuVVRDKTsKICAgICAgICBpZiAoc3RhdHVzID09IFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cy5QRU5ESU5HCiAgICAgICAgICAgICAgICAmJiByZXF1ZXN0LmdldE5leHRBdHRlbXB0QXQoKSAhPSBudWxsCiAgICAgICAgICAgICAgICAmJiByZXF1ZXN0LmdldE5leHRBdHRlbXB0QXQoKS5pc0FmdGVyKG5vdykpIHsKICAgICAgICAgICAgcmV0dXJuIGZhbHNlOwogICAgICAgIH0KICAgICAgICBpZiAoc3RhdHVzID09IFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cy5QUk9DRVNTSU5HCiAgICAgICAgICAgICAgICAmJiByZXF1ZXN0LmdldExlYXNlVW50aWwoKSAhPSBudWxsCiAgICAgICAgICAgICAgICAmJiByZXF1ZXN0LmdldExlYXNlVW50aWwoKS5pc0FmdGVyKG5vdykpIHsKICAgICAgICAgICAgcmV0dXJuIGZhbHNlOwogICAgICAgIH0KICAgICAgICBpZiAoc3RhdHVzICE9IFRyYW5zYWN0aW9uUmVxdWVzdFN0YXR1cy5QRU5ESU5HCiAgICAgICAgICAgICAgICAmJiBzdGF0dXMgIT0gVHJhbnNhY3Rpb25SZXF1ZXN0U3RhdHVzLlBST0NFU1NJTkcpIHsKICAgICAgICAgICAgcmV0dXJuIGZhbHNlOwogICAgICAgIH0KCiAgICAgICAgcmVxdWVzdC5tYXJrUHJvY2Vzc2luZyh3b3JrZXJJZCk7CiAgICAgICAgcmV0dXJuIHRydWU7CiAgICB9Cg==')
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionRequestService.java' (Decode-Text 'ICAgICAgICByZXF1ZXN0Lm1hcmtGYWlsZWQoZmFpbHVyZUNvZGUpOwogICAgfQp9Cg==') (Decode-Text 'ICAgICAgICByZXF1ZXN0Lm1hcmtGYWlsZWQoZmFpbHVyZUNvZGUpOwogICAgfQoKICAgIEBUcmFuc2FjdGlvbmFsCiAgICBwdWJsaWMgdm9pZCBzY2hlZHVsZVJldHJ5KExvbmcgcmVxdWVzdElkLCBTdHJpbmcgZmFpbHVyZUNvZGUpIHsKICAgICAgICBUcmFuc2FjdGlvblJlcXVlc3QgcmVxdWVzdCA9IHJlcG9zaXRvcnkuZmluZEJ5SWRGb3JVcGRhdGUocmVxdWVzdElkKQogICAgICAgICAgICAgICAgLm9yRWxzZVRocm93KCgpIC0+IG5ldyBSZXNvdXJjZU5vdEZvdW5kRXhjZXB0aW9uKCJUcmFuc2FjdGlvbiByZXF1ZXN0IG5vdCBmb3VuZC4iKSk7CiAgICAgICAgaWYgKHJlcXVlc3QuZ2V0U3RhdHVzKCkgPT0gVHJhbnNhY3Rpb25SZXF1ZXN0U3RhdHVzLkNPTVBMRVRFRAogICAgICAgICAgICAgICAgfHwgcmVxdWVzdC5nZXRTdGF0dXMoKSA9PSBUcmFuc2FjdGlvblJlcXVlc3RTdGF0dXMuRkFJTEVEKSB7CiAgICAgICAgICAgIHJldHVybjsKICAgICAgICB9CgogICAgICAgIGlmIChyZXF1ZXN0LmdldEF0dGVtcHRDb3VudCgpID49IG1heEF0dGVtcHRzKSB7CiAgICAgICAgICAgIHJlcXVlc3QubWFya0ZhaWxlZChmYWlsdXJlQ29kZSk7CiAgICAgICAgICAgIHJldHVybjsKICAgICAgICB9CgogICAgICAgIGludCBleHBvbmVudCA9IE1hdGgubWluKE1hdGgubWF4KHJlcXVlc3QuZ2V0QXR0ZW1wdENvdW50KCkgLSAxLCAwKSwgMTApOwogICAgICAgIGxvbmcgYmFzZURlbGF5ID0gTWF0aC5taW4oMzAwTCwgTWF0aC5tYXgoMUwsIGJhc2VCYWNrb2ZmU2Vjb25kcykpOwogICAgICAgIGxvbmcgZGVsYXlTZWNvbmRzID0gTWF0aC5taW4oMzAwTCwgYmFzZURlbGF5ICogKDFMIDw8IGV4cG9uZW50KSk7CiAgICAgICAgT2Zmc2V0RGF0ZVRpbWUgcmV0cnlBdCA9IE9mZnNldERhdGVUaW1lLm5vdyhab25lT2Zmc2V0LlVUQykucGx1c1NlY29uZHMoZGVsYXlTZWNvbmRzKTsKICAgICAgICByZXF1ZXN0LnNjaGVkdWxlUmV0cnkocmV0cnlBdCwgZmFpbHVyZUNvZGUpOwogICAgfQp9Cg==')

# 5) Have processor claim durable work, mark business failures terminal, and back off transient errors.
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionProcessor.java' (Decode-Text 'aW1wb3J0IG9yZy5zcHJpbmdmcmFtZXdvcmsuc3RlcmVvdHlwZS5TZXJ2aWNlOwo=') (Decode-Text 'aW1wb3J0IG9yZy5zbGY0ai5Mb2dnZXI7CmltcG9ydCBvcmcuc2xmNGouTG9nZ2VyRmFjdG9yeTsKaW1wb3J0IG9yZy5zcHJpbmdmcmFtZXdvcmsuc3RlcmVvdHlwZS5TZXJ2aWNlOwoKaW1wb3J0IGphdmEudXRpbC5VVUlEOwo=')
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionProcessor.java' (Decode-Text 'cHVibGljIGNsYXNzIFRyYW5zYWN0aW9uUHJvY2Vzc29yIHsKCg==') (Decode-Text 'cHVibGljIGNsYXNzIFRyYW5zYWN0aW9uUHJvY2Vzc29yIHsKCiAgICBwcml2YXRlIHN0YXRpYyBmaW5hbCBMb2dnZXIgbG9nID0gTG9nZ2VyRmFjdG9yeS5nZXRMb2dnZXIoVHJhbnNhY3Rpb25Qcm9jZXNzb3IuY2xhc3MpOwoK')
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionProcessor.java' (Decode-Text 'ICAgIC8vIERlbGliZXJhdGVseSBub24tdHJhbnNhY3Rpb25hbDogZmFpbGVkIGZpbmFuY2lhbCB3b3JrIHJvbGxzIGJhY2sgZmlyc3QsCiAgICAvLyB0aGVuIEZBSUxFRCBzdGF0dXMgaXMgcmVjb3JkZWQgaW4gaXRzIG93biB0cmFuc2FjdGlvbi4KICAgIHB1YmxpYyB2b2lkIHByb2Nlc3NSZXF1ZXN0KExvbmcgcmVxdWVzdElkKSB7CiAgICAgICAgdHJ5IHsKICAgICAgICAgICAgZXhlY3V0aW9uU2VydmljZS5leGVjdXRlKHJlcXVlc3RJZCk7CiAgICAgICAgfSBjYXRjaCAoRmluYW5jaWFsT3BlcmF0aW9uRXhjZXB0aW9uIGV4Y2VwdGlvbikgewogICAgICAgICAgICByZXF1ZXN0U2VydmljZS5tYXJrRmFpbGVkKHJlcXVlc3RJZCwgZXhjZXB0aW9uLmdldEZhaWx1cmVDb2RlKCkpOwogICAgICAgIH0KICAgIH0K') (Decode-Text 'ICAgIC8vIENsYWltaW5nIGlzIGNvbW1pdHRlZCBzZXBhcmF0ZWx5IHNvIHJldHJ5IG1ldGFkYXRhIHN1cnZpdmVzIGEgZmFpbGVkIGZpbmFuY2lhbCB0cmFuc2FjdGlvbi4KICAgIHB1YmxpYyB2b2lkIHByb2Nlc3NSZXF1ZXN0KExvbmcgcmVxdWVzdElkKSB7CiAgICAgICAgU3RyaW5nIHdvcmtlcklkID0gImJhbmtpbmctd29ya2VyLSIgKyBVVUlELnJhbmRvbVVVSUQoKTsKICAgICAgICBpZiAoIXJlcXVlc3RTZXJ2aWNlLmNsYWltRm9yUHJvY2Vzc2luZyhyZXF1ZXN0SWQsIHdvcmtlcklkKSkgewogICAgICAgICAgICByZXR1cm47CiAgICAgICAgfQoKICAgICAgICB0cnkgewogICAgICAgICAgICBleGVjdXRpb25TZXJ2aWNlLmV4ZWN1dGUocmVxdWVzdElkKTsKICAgICAgICB9IGNhdGNoIChGaW5hbmNpYWxPcGVyYXRpb25FeGNlcHRpb24gZXhjZXB0aW9uKSB7CiAgICAgICAgICAgIC8vIEJ1c2luZXNzIGVycm9ycyAoZm9yIGV4YW1wbGUsIGluc3VmZmljaWVudCBmdW5kcykgYXJlIG5vdCB0cmFuc2llbnQuCiAgICAgICAgICAgIHJlcXVlc3RTZXJ2aWNlLm1hcmtGYWlsZWQocmVxdWVzdElkLCBleGNlcHRpb24uZ2V0RmFpbHVyZUNvZGUoKSk7CiAgICAgICAgfSBjYXRjaCAoUnVudGltZUV4Y2VwdGlvbiBleGNlcHRpb24pIHsKICAgICAgICAgICAgbG9nLmVycm9yKCJVbmV4cGVjdGVkIGVycm9yIHByb2Nlc3NpbmcgdHJhbnNhY3Rpb24gcmVxdWVzdCB7fSIsIHJlcXVlc3RJZCwgZXhjZXB0aW9uKTsKICAgICAgICAgICAgcmVxdWVzdFNlcnZpY2Uuc2NoZWR1bGVSZXRyeShyZXF1ZXN0SWQsICJVTkVYUEVDVEVEX1BST0NFU1NJTkdfRVJST1IiKTsKICAgICAgICB9CiAgICB9Cg==')

# 6) Execution expects the separately committed PROCESSING claim.
Replace-Once 'src/main/java/com/example/banking/transaction/service/TransactionExecutionService.java' (Decode-Text 'ICAgICAgICBpZiAocmVxdWVzdC5nZXRTdGF0dXMoKSAhPSBUcmFuc2FjdGlvblJlcXVlc3RTdGF0dXMuUEVORElORykgewogICAgICAgICAgICByZXR1cm47CiAgICAgICAgfQogICAgICAgIHJlcXVlc3QubWFya1Byb2Nlc3NpbmcoInN5bmNocm9ub3VzLWFwaSIpOwo=') (Decode-Text 'ICAgICAgICBpZiAocmVxdWVzdC5nZXRTdGF0dXMoKSAhPSBUcmFuc2FjdGlvblJlcXVlc3RTdGF0dXMuUFJPQ0VTU0lORykgewogICAgICAgICAgICByZXR1cm47CiAgICAgICAgfQo=')

# 7) Expose worker switches via environment-backed configuration.
Replace-Once 'src/main/resources/application.properties' (Decode-Text 'YmFua2luZy5zZWN1cml0eS5wYXNzd29yZD0ke0JBTktJTkdfQVBJX1BBU1NXT1JEOn0K') (Decode-Text 'YmFua2luZy5zZWN1cml0eS5wYXNzd29yZD0ke0JBTktJTkdfQVBJX1BBU1NXT1JEOn0KCiMgRHVyYWJsZSBkYXRhYmFzZS1iYWNrZWQgdHJhbnNhY3Rpb24gd29ya2VyLgpiYW5raW5nLndvcmtlci5lbmFibGVkPSR7QkFOS0lOR19XT1JLRVJfRU5BQkxFRDp0cnVlfQpiYW5raW5nLndvcmtlci5wb2xsLWludGVydmFsLW1zPSR7QkFOS0lOR19XT1JLRVJfUE9MTF9JTlRFUlZBTF9NUzoxMDAwfQpiYW5raW5nLndvcmtlci5tYXgtYXR0ZW1wdHM9JHtCQU5LSU5HX1dPUktFUl9NQVhfQVRURU1QVFM6NX0KYmFua2luZy53b3JrZXIuYmFzZS1iYWNrb2ZmLXNlY29uZHM9JHtCQU5LSU5HX1dPUktFUl9CQVNFX0JBQ0tPRkZfU0VDT05EUzoyfQo=')

# Prevent the scheduled worker racing the tests that explicitly call TransactionProcessor.
Replace-Once 'src/test/java/com/example/banking/transaction/OracleFinancialIntegrationTest.java' (Decode-Text 'QFRlc3RQcm9wZXJ0eVNvdXJjZShwcm9wZXJ0aWVzID0geyJiYW5raW5nLnNlY3VyaXR5LnVzZXJuYW1lPXRlc3QtYWRtaW4iLCAiYmFua2luZy5zZWN1cml0eS5wYXNzd29yZD10ZXN0LXBhc3N3b3JkIn0pCg==') (Decode-Text 'QFRlc3RQcm9wZXJ0eVNvdXJjZShwcm9wZXJ0aWVzID0geyJiYW5raW5nLnNlY3VyaXR5LnVzZXJuYW1lPXRlc3QtYWRtaW4iLCAiYmFua2luZy5zZWN1cml0eS5wYXNzd29yZD10ZXN0LXBhc3N3b3JkIiwgImJhbmtpbmcud29ya2VyLmVuYWJsZWQ9ZmFsc2UifSkK')

# 8) Add the scheduled poller and enable scheduling in the Spring application context.
Add-NewFile 'src/main/java/com/example/banking/transaction/service/TransactionWorker.java' @'
package com.example.banking.transaction.service;

import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "banking.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionWorker {

    private static final Logger log = LoggerFactory.getLogger(TransactionWorker.class);

    private final TransactionRequestRepository requestRepository;
    private final TransactionProcessor processor;

    public TransactionWorker(TransactionRequestRepository requestRepository,
                             TransactionProcessor processor) {
        this.requestRepository = requestRepository;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${banking.worker.poll-interval-ms:1000}")
    public void pollQueue() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Set<Long> candidateIds = new LinkedHashSet<>();

        List<TransactionRequest> dueRequests = requestRepository
                .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        TransactionRequestStatus.PENDING, now);
        dueRequests.stream().map(TransactionRequest::getId).forEach(candidateIds::add);

        // Recover requests whose worker crashed after committing the claim.
        List<TransactionRequest> expiredLeases = requestRepository
                .findTop20ByStatusAndLeaseUntilLessThanEqualOrderByCreatedAtAsc(
                        TransactionRequestStatus.PROCESSING, now);
        expiredLeases.stream().map(TransactionRequest::getId).forEach(candidateIds::add);

        for (Long requestId : candidateIds) {
            try {
                processor.processRequest(requestId);
            } catch (RuntimeException exception) {
                log.error("Could not dispatch transaction request {}", requestId, exception);
            }
        }
    }
}
'@
Add-NewFile 'src/main/java/com/example/banking/transaction/config/TransactionWorkerSchedulingConfiguration.java' @'
package com.example.banking.transaction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class TransactionWorkerSchedulingConfiguration {
}
'@

# Commit to disk only after all anchors passed validation.
foreach ($relativePath in $script:Updates.Keys) {
    $entry = $script:Updates[$relativePath]
    $output = $entry.Text.Replace("`n", $entry.LineEnding)
    [System.IO.File]::WriteAllText($entry.FullPath, $output, [System.Text.UTF8Encoding]::new($false))
}
foreach ($relativePath in $script:NewFiles.Keys) {
    $fullPath = Join-Path $projectRoot $relativePath
    $directory = Split-Path -Parent $fullPath
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($fullPath, $script:NewFiles[$relativePath].Replace("`n", "`r`n"), [System.Text.UTF8Encoding]::new($false))
}

Write-Host 'Worker changes applied.' -ForegroundColor Green
Write-Host 'No commit or push was performed.' -ForegroundColor Yellow
Write-Host 'Next run: git diff --check; .\mvnw.cmd clean compile' -ForegroundColor Cyan
