<#
.SYNOPSIS
    Fetches and displays active community bug reports, modification suggestions,
    and proving submissions directly from the GitHub repository for clinical analysis.
.USAGE
    .\tools\fetch-community-issues.ps1
    .\tools\fetch-community-issues.ps1 -IssueNumber 1
#>

param(
    [int]$IssueNumber = 0,
    [string]$State = "open"
)

$repo = "bkoustoov-oss/openhomeo"
$headers = @{
    'User-Agent' = 'OpenHomeo-Issue-Sync'
    'Accept' = 'application/vnd.github.v3+json'
}

# Optional auth token for higher rate limits
try {
    $token = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null | Select-String 'password=(.+)' | ForEach-Object { $_.Matches.Groups[1].Value }
    if ($token) {
        $headers['Authorization'] = "Bearer $token"
    }
} catch {}

if ($IssueNumber -gt 0) {
    Write-Host "[*] Fetching Issue #$IssueNumber from $repo..." -ForegroundColor Cyan
    try {
        $issue = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/issues/$IssueNumber" -Headers $headers
        Write-Host "================================================================================" -ForegroundColor Gray
        Write-Host "[$($issue.state.ToUpper())] #$($issue.number): $($issue.title)" -ForegroundColor Green
        Write-Host "Author: $($issue.user.login) | Created: $($issue.created_at) | Comments: $($issue.comments)" -ForegroundColor Yellow
        $labels = ($issue.labels | ForEach-Object { $_.name }) -join ", "
        if ($labels) { Write-Host "Labels: $labels" -ForegroundColor Magenta }
        Write-Host "--------------------------------------------------------------------------------" -ForegroundColor Gray
        Write-Host $issue.body
        Write-Host "================================================================================" -ForegroundColor Gray

        if ($issue.comments -gt 0) {
            Write-Host "`n[*] Comments:" -ForegroundColor Cyan
            $comments = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/issues/$IssueNumber/comments" -Headers $headers
            foreach ($c in $comments) {
                Write-Host "`n--- [$($c.user.login)] on $($c.created_at) ---" -ForegroundColor DarkCyan
                Write-Host $c.body
            }
        }
    } catch {
        Write-Host "[-] Failed to fetch issue: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "[*] Fetching active community issues and bug reports from $repo..." -ForegroundColor Cyan
    try {
        $issues = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/issues?state=$State&per_page=50" -Headers $headers
        if ($issues.Count -eq 0) {
            Write-Host "[+] No open community issues or bug reports currently logged." -ForegroundColor Green
            Write-Host "    Community issue tracker is live at: https://github.com/$repo/issues" -ForegroundColor Gray
            return
        }

        Write-Host "`nFound $($issues.Count) community issue(s):" -ForegroundColor Green
        $issues | ForEach-Object {
            $labels = ($_.labels | ForEach-Object { $_.name }) -join ", "
            [PSCustomObject]@{
                Number = $_.number
                Title = $_.title
                Author = $_.user.login
                Labels = $labels
                Comments = $_.comments
                Created = $_.created_at
            }
        } | Format-Table -AutoSize
    } catch {
        Write-Host "[-] Failed to list issues: $($_.Exception.Message)" -ForegroundColor Red
    }
}
