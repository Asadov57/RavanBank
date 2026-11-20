$workspace = "C:\Users\user\Desktop\Bank Final Ravan"
Set-Location $workspace

# 1. Check if 8080 is already responding
$running = $false
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/login" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
    if ($res.StatusCode -eq 200) {
        $running = $true
    }
} catch {
    $running = $false
}

# 2. If not running, start it
if (-not $running) {
    # Free port 8080 if lingering process exists
    $conns = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($c in $conns) {
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }

    $java = "C:\Users\user\.jdks\ms-21.0.12\bin\java.exe"
    $jar = Join-Path $workspace "target\financeapp-0.0.1-SNAPSHOT.jar"

    Start-Process -FilePath $java -ArgumentList "-Dfile.encoding=UTF-8", "-jar", "`"$jar`"" -WorkingDirectory $workspace -WindowStyle Hidden

    # Wait until it's ready (up to 30 seconds)
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        try {
            $check = Invoke-WebRequest -Uri "http://localhost:8080/login" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($check.StatusCode -eq 200) {
                break
            }
        } catch {}
    }
}

# 3. Open browser with the page
Start-Process "http://localhost:8080"
