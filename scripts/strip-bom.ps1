param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")),
    [switch]$CheckOnly
)

$patterns = @("*.java", "*.kt", "*.kts", "*.gradle", "*.properties", "*.yml", "*.yaml", "*.sql", "*.md", "*.xml")
$excludeParts = @("\\.git\\", "\\.gradle\\", "\\build\\", "\\target\\")

$files = Get-ChildItem -Path $Root -Recurse -File -Include $patterns | Where-Object {
    $p = $_.FullName
    -not ($excludeParts | ForEach-Object { $p -like "*$_*" } | Where-Object { $_ })
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$changed = @()

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $changed += $file.FullName
        if (-not $CheckOnly) {
            $text = [System.Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3)
            [System.IO.File]::WriteAllText($file.FullName, $text, $utf8NoBom)
        }
    }
}

if ($changed.Count -eq 0) {
    Write-Output "No BOM files found."
    exit 0
}

Write-Output ("BOM files found: {0}" -f $changed.Count)
$changed | ForEach-Object { Write-Output $_ }

if ($CheckOnly) {
    exit 1
}