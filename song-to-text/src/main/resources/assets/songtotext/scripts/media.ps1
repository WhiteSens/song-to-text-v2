# Довгоживучий процес: 4 рази на секунду друкує один JSON-рядок про активну сесію медіапанелі Windows.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
while ($true) {
  try {
    $best = $null
    foreach ($s in $mgr.GetSessions()) {
      $st = $s.GetPlaybackInfo().PlaybackStatus
      if ($st -eq 'Playing') { $best = $s; break }
      if (-not $best) { $best = $s }
    }
    if ($best) {
      $p = Await ($best.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
      $tl = $best.GetTimelineProperties()
      $ago = ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
      [pscustomobject]@{
        app = $best.SourceAppUserModelId; title = $p.Title; artist = $p.Artist
        playing = ($best.GetPlaybackInfo().PlaybackStatus -eq 'Playing')
        pos = [int64]$tl.Position.TotalMilliseconds; end = [int64]$tl.EndTime.TotalMilliseconds
        ago = [int64][Math]::Max(0, $ago)
      } | ConvertTo-Json -Compress
    } else { '{}' }
  } catch { '{}' }
  [Console]::Out.Flush()
  Start-Sleep -Milliseconds 250
}
