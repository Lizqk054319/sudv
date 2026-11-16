package pl.holovis;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Spotify na komputerze, bez logowania.
 * Windows: Media Session (SMTC) przez PowerShell - tytuł, pozycja, długość, pauza, skip, PRZEWIJANIE.
 * Gdy to nie zadziała: tytuł okna + klawisze multimedialne (bez przewijania).
 * macOS / Linux: osascript / playerctl (bez przewijania).
 */
public final class Spotify {
    private Spotify() {}

    public static volatile String track = "", artist = "", title = "";
    public static volatile boolean playing = false, running = false, hasTimeline = false;
    public static volatile long durMs = 0;
    /** Głośność aplikacji Spotify w mikserze Windows (0..1), -1 = nieznana. */
    public static volatile float volume = -1f;
    /** true, gdy użytkownik przesuwa suwak (nie nadpisujemy wtedy wartości z Windowsa). */
    public static volatile boolean volDragging;
    private static volatile long posMs = 0, posStamp = System.nanoTime();

    private static final String OS = System.getProperty("os.name", "").toLowerCase();
    private static final boolean WIN = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private static final Path BASE = HoloSounds.DIR.getParent();
    private static final Path BRIDGE_PS = BASE.resolve("spotify_bridge.ps1");
    private static final Path MEDIA_PS = BASE.resolve("spotify_media.ps1");
    private static final Path CMD = BASE.resolve("spotify_cmd.txt");

    private static Process bridge, legacyPoller, mediaCtl;
    private static boolean active, hook, legacy;
    private static int gen;

    // ------------------------------------------------------------------
    public static long position() {
        long p = posMs + (playing ? (System.nanoTime() - posStamp) / 1_000_000L : 0);
        long d = durMs;
        return Math.max(0, d > 0 ? Math.min(p, d) : p);
    }

    public static synchronized void sync() {
        if (!hook) {
            hook = true;
            Runtime.getRuntime().addShutdownHook(new Thread(Spotify::shutdown));
        }
        HoloConfig c = HoloConfig.get();
        boolean want = c.spotify || c.ytMusic;
        if (want && active && !bridgeMode().equals(runningMode)) stop();
        if (want) start(); else stop();
    }

    /** Źródło muzyki dla mostu Windows: tylko Spotify, tylko przeglądarka (YT Music) albo oba. */
    public static String bridgeMode() {
        HoloConfig c = HoloConfig.get();
        if (c.spotify && c.ytMusic) return "auto";
        return c.ytMusic ? "browser" : "spotify";
    }

    /** true, gdy aktualna muzyka pochodzi z przeglądarki (YT Music). */
    public static volatile boolean fromBrowser;
    private static String runningMode = "";

    public static void next() { command("next"); }
    public static void previous() { command("prev"); }
    public static void toggle() { command("toggle"); }

    /** Ustawia głośność Spotify (0..1) - tylko Windows. */
    public static void setVolume(float v) {
        v = Math.max(0f, Math.min(1f, v));
        volume = v;
        if (WIN && !legacy) command("vol " + String.format(java.util.Locale.ROOT, "%.3f", v));
    }

    public static void seek(long ms) {
        if (!WIN || legacy || !hasTimeline) return;
        posMs = ms;
        posStamp = System.nanoTime();
        command("seek " + ms);
    }

    // ------------------------------------------------------------------
    private static void start() {
        if (active) return;
        active = true;
        runningMode = bridgeMode();
        final int my = ++gen;
        Thread t = new Thread(() -> {
            if (WIN) {
                boolean ok = runBridge(my);
                if (!ok && my == gen) { legacy = true; pollLegacy(my); }
            } else {
                pollOther(my);
            }
        }, "holovis-spotify");
        t.setDaemon(true);
        t.start();
    }

    private static void stop() {
        active = false;
        gen++;
        if (bridge != null) { bridge.destroy(); bridge = null; }
        if (legacyPoller != null) { legacyPoller.destroy(); legacyPoller = null; }
        running = false;
        playing = false;
        hasTimeline = false;
        track = "";
    }

    private static void shutdown() {
        try { if (bridge != null) bridge.destroy(); } catch (Exception ignored) {}
        try { if (legacyPoller != null) legacyPoller.destroy(); } catch (Exception ignored) {}
        try { if (mediaCtl != null) mediaCtl.destroy(); } catch (Exception ignored) {}
    }

    private static synchronized void command(String cmd) {
        if (WIN) {
            if (!legacy) {
                if (!active) start();
                try {
                    Files.createDirectories(BASE);
                    Files.writeString(CMD, cmd);
                } catch (Exception e) {
                    System.err.println("[KosaVisuals] Spotify: nie udało się wysłać polecenia: " + e);
                }
                return;
            }
            mediaKey(cmd);
            return;
        }
        String[] c;
        if (MAC) {
            String a = cmd.equals("next") ? "next track" : cmd.equals("prev") ? "previous track" : "playpause";
            c = new String[]{"osascript", "-e", "tell application \"Spotify\" to " + a};
        } else {
            String a = cmd.equals("next") ? "next" : cmd.equals("prev") ? "previous" : "play-pause";
            c = new String[]{"playerctl", "-p", "spotify", a};
        }
        Thread t = new Thread(() -> run(c), "holovis-spotify-cmd");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------
    //  Windows: Media Session (SMTC)
    // ------------------------------------------------------------------
    private static final String BRIDGE_SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            $cmdFile = $args[0]
            $mode = $args[1]
            $inv = [Globalization.CultureInfo]::InvariantCulture
            $volOk = $false
            try {
            Add-Type -TypeDefinition @"
            using System;
            using System.Diagnostics;
            using System.Collections.Generic;
            using System.Runtime.InteropServices;
            [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class KvMMDeviceEnumerator {}
            [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IKvMMDeviceEnumerator { int NotImpl1(); [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IKvMMDevice dev); }
            [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IKvMMDevice { [PreserveSig] int Activate(ref Guid iid, int ctx, IntPtr p, [MarshalAs(UnmanagedType.IUnknown)] out object o); }
            [Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IKvSessionManager2 { int NotImpl1(); int NotImpl2(); [PreserveSig] int GetSessionEnumerator(out IKvSessionEnumerator e); }
            [Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IKvSessionEnumerator { [PreserveSig] int GetCount(out int n); [PreserveSig] int GetSession(int i, out IKvSessionControl2 s); }
            [Guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IKvSessionControl2 { int N0(); int N1(); int N2(); int N3(); int N4(); int N5(); int N6(); int N7(); int N8(); int N9(); int N10(); [PreserveSig] int GetProcessId(out int pid); }
            [Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IKvSimpleVolume { [PreserveSig] int SetMasterVolume(float v, ref Guid ctx); [PreserveSig] int GetMasterVolume(out float v); }
            public static class KvSpotifyVolume {
              public static string Names = "spotify";
              static List<IKvSimpleVolume> Find() {
                var res = new List<IKvSimpleVolume>();
                var en = (IKvMMDeviceEnumerator)(new KvMMDeviceEnumerator());
                IKvMMDevice dev; if (en.GetDefaultAudioEndpoint(0, 1, out dev) != 0) return res;
                Guid iid = typeof(IKvSessionManager2).GUID; object o;
                if (dev.Activate(ref iid, 23, IntPtr.Zero, out o) != 0) return res;
                var mgr = (IKvSessionManager2)o; IKvSessionEnumerator se;
                if (mgr.GetSessionEnumerator(out se) != 0) return res;
                int n; se.GetCount(out n);
                for (int i = 0; i < n; i++) {
                  IKvSessionControl2 c; if (se.GetSession(i, out c) != 0) continue;
                  int pid; c.GetProcessId(out pid);
                  try { string pn = Process.GetProcessById(pid).ProcessName.ToLower(); foreach (var nm in Names.Split(',')) if (nm.Length > 0 && pn.Contains(nm)) { res.Add((IKvSimpleVolume)c); break; } } catch { }
                }
                return res;
              }
              public static float Get() { var l = Find(); if (l.Count == 0) return -1f; float v; l[0].GetMasterVolume(out v); return v; }
              public static void Set(float v) { Guid g = Guid.Empty; foreach (var s in Find()) s.SetMasterVolume(v, ref g); }
            }
            "@
            $volOk = $true
            } catch { }
            $lastKey = ''
            $coverN = 0
            [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
            $mgr = $null
            try {
              Add-Type -AssemblyName System.Runtime.WindowsRuntime
              $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
              $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
              $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
            } catch { }
            function Await($op, $type) {
              $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
              $t.Wait(-1) | Out-Null
              $t.Result
            }
            try { $mgr = Await ($mgrType::RequestAsync()) $mgrType } catch { $mgr = $null }
            if ($null -eq $mgr) { [Console]::Out.WriteLine('ERR'); [Console]::Out.Flush(); exit 1 }
            [Console]::Out.WriteLine('READY'); [Console]::Out.Flush()
            $sep = [char]9
            function IsBrowser($id) {
              $l = ('' + $id).ToLower()
              return ($l -like '*brave*' -or $l -like '*opera*' -or $l -like '*chrome*' -or $l -like '*msedge*' -or $l -like '*firefox*' -or $l -like '*vivaldi*')
            }
            function FindSession() {
              $sp = $null; $br = $null; $brOn = $null
              foreach ($s in $mgr.GetSessions()) {
                $id = '' + $s.SourceAppUserModelId
                if ($id -like '*Spotify*') { $sp = $s }
                elseif (IsBrowser $id) {
                  if ($null -eq $br) { $br = $s }
                  if ($null -eq $brOn -and [int]$s.GetPlaybackInfo().PlaybackStatus -eq 4) { $brOn = $s }
                }
              }
              if ($brOn) { $br = $brOn }
              if ($mode -eq 'spotify') { return $sp }
              if ($mode -eq 'browser') { return $br }
              if ($sp -and [int]$sp.GetPlaybackInfo().PlaybackStatus -eq 4) { return $sp }
              if ($br) { return $br }
              return $sp
            }
            function Emit($s) {
              if ($null -eq $s) { [Console]::Out.WriteLine('S' + $sep + '0'); [Console]::Out.Flush(); return }
              $props = Await ($s.TryGetMediaPropertiesAsync()) $propType
              $tl = $s.GetTimelineProperties()
              $st = [int]$s.GetPlaybackInfo().PlaybackStatus
              $pos = [int64]$tl.Position.TotalMilliseconds
              if ($st -eq 4) { $pos = $pos + [int64]([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
              $dur = [int64]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
              if ($pos -lt 0) { $pos = 0 }
              if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
              $src = '0'
              if (IsBrowser $s.SourceAppUserModelId) { $src = '1'; if ($volOk) { [KvSpotifyVolume]::Names = 'brave,opera,chrome,msedge,firefox,vivaldi' } }
              elseif ($volOk) { [KvSpotifyVolume]::Names = 'spotify' }
              [Console]::Out.WriteLine('S' + $sep + '1' + $sep + $st + $sep + $pos + $sep + $dur + $sep + $props.Artist + $sep + $props.Title + $sep + $src)
              if ($volOk) { try { [Console]::Out.WriteLine('V' + $sep + ([KvSpotifyVolume]::Get()).ToString($inv)) } catch { } }
              [Console]::Out.Flush()
            }
            function RunCmd($s, $c) {
              if ($c -eq 'next') { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null }
              elseif ($c -eq 'prev') { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }
              elseif ($c -eq 'toggle') { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
              elseif ($c.StartsWith('vol')) { if ($volOk) { [KvSpotifyVolume]::Set([float]::Parse($c.Substring(3).Trim(), $inv)) } }
              elseif ($c.StartsWith('seek')) { $ms = [int64]($c.Substring(4).Trim()); Await ($s.TryChangePlaybackPositionAsync($ms * 10000)) ([bool]) | Out-Null }
            }
            $n = 0
            while ($true) {
              $sp = FindSession
              if (Test-Path $cmdFile) {
                $c = ''
                try { $c = (Get-Content $cmdFile -Raw).Trim() } catch { }
                Remove-Item $cmdFile -Force
                if ($sp -and $c) { RunCmd $sp $c; Start-Sleep -Milliseconds 150; $n = 0 }
              }
              if ($n -le 0) { Emit $sp; $n = 5 } else { $n = $n - 1 }
              Start-Sleep -Milliseconds 200
            }
            """;

    /** @return true, jeśli most SMTC działa i podał choć jeden stan */
    private static boolean runBridge(int my) {
        boolean got = false;
        try {
            Files.createDirectories(BASE);
            Files.writeString(BRIDGE_PS, BRIDGE_SCRIPT);
            Files.deleteIfExists(CMD);
            Process pr = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", BRIDGE_PS.toAbsolutePath().toString(), CMD.toAbsolutePath().toString(), bridgeMode())
                    .redirectErrorStream(true).start();
            synchronized (Spotify.class) {
                if (my != gen) { pr.destroy(); return true; }
                bridge = pr;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (my == gen && (line = r.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                    if (line.startsWith("ERR")) { pr.destroy(); return false; }
                    if (line.startsWith("S\t")) { handleBridge(line); got = true; }
                    else if (line.startsWith("V\t")) {
                        try { if (!volDragging) volume = Float.parseFloat(line.substring(2).trim()); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Spotify: błąd mostu SMTC: " + e);
        }
        return got || my != gen;
    }

    private static void handleBridge(String line) {
        String[] f = line.split("\t", 8);
        if (f.length >= 2 && f[1].equals("0")) {
            running = false;
            playing = false;
            hasTimeline = false;
            return;
        }
        if (f.length < 7) return;
        try {
            int st = Integer.parseInt(f[2].trim());
            long pos = Long.parseLong(f[3].trim());
            long dur = Long.parseLong(f[4].trim());
            String ar = f[5].trim(), ti = f[6].trim();
            fromBrowser = f.length >= 8 && f[7].trim().equals("1");
            if (fromBrowser && ar.endsWith(" - Topic")) ar = ar.substring(0, ar.length() - 8);
            running = true;
            hasTimeline = dur > 0;
            playing = st == 4;
            durMs = dur;
            posMs = pos;
            posStamp = System.nanoTime();
            artist = ar;
            title = ti;
            track = ar.isEmpty() ? ti : ar + " - " + ti;
            if (!ti.isEmpty()) Lyrics.request(ar, ti, dur);
        } catch (NumberFormatException ignored) {}
    }

    // ------------------------------------------------------------------
    //  Windows: zapasowo - tytuł okna + klawisze multimedialne
    // ------------------------------------------------------------------
    private static final String MEDIA_SCRIPT = """
            Add-Type -TypeDefinition @"
            using System;
            using System.Runtime.InteropServices;
            public class HK { [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra); }
            "@
            while (($l = [Console]::In.ReadLine()) -ne $null) {
              $vk = 0
              if ($l -eq 'next') { $vk = 176 } elseif ($l -eq 'prev') { $vk = 177 } elseif ($l -eq 'toggle') { $vk = 179 }
              if ($vk -ne 0) {
                [HK]::keybd_event([byte]$vk, 0, 1, [UIntPtr]::Zero)
                [HK]::keybd_event([byte]$vk, 0, 3, [UIntPtr]::Zero)
              }
            }
            """;

    private static void mediaKey(String cmd) {
        if (cmd.startsWith("seek")) return;
        try {
            if (mediaCtl == null || !mediaCtl.isAlive()) {
                Files.createDirectories(BASE);
                Files.writeString(MEDIA_PS, MEDIA_SCRIPT);
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-File", MEDIA_PS.toAbsolutePath().toString()).redirectErrorStream(true).start();
                mediaCtl = p;
                Thread d = new Thread(() -> {
                    try { p.getInputStream().transferTo(OutputStream.nullOutputStream()); } catch (Exception ignored) {}
                }, "holovis-spotify-drain");
                d.setDaemon(true);
                d.start();
            }
            OutputStream os = mediaCtl.getOutputStream();
            os.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Spotify: klawisze multimedialne nie zadziałały: " + e);
        }
    }

    private static void pollLegacy(int my) {
        String script = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false; "
                + "while($true){ "
                + "$p = Get-Process -Name Spotify -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1; "
                + "if($p){ Write-Output ('T|' + $p.MainWindowTitle) } else { Write-Output 'N|' }; "
                + "Start-Sleep -Milliseconds 1000 }";
        try {
            Process pr = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script).redirectErrorStream(true).start();
            synchronized (Spotify.class) {
                if (my != gen) { pr.destroy(); return; }
                legacyPoller = pr;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (my == gen && (line = r.readLine()) != null) {
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                    if (line.startsWith("T|")) {
                        String t = line.substring(2).trim();
                        running = true;
                        hasTimeline = false;
                        if (t.matches("(?i)^spotify( (free|premium))?$")) playing = false;
                        else { playing = true; track = t; }
                    } else if (line.startsWith("N|")) {
                        running = false;
                        playing = false;
                        track = "";
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Spotify: błąd odczytu: " + e);
        }
    }

    // ------------------------------------------------------------------
    //  macOS / Linux
    // ------------------------------------------------------------------
    private static void pollOther(int my) {
        String[] cmd = MAC
                ? new String[]{"osascript", "-e",
                "if application \"Spotify\" is running then tell application \"Spotify\" to return (artist of current track) & \" - \" & (name of current track) & \"|\" & (player state as string)"}
                : new String[]{"playerctl", "-p", "spotify", "metadata", "--format", "{{artist}} - {{title}}|{{status}}"};
        while (my == gen) {
            String out = run(cmd);
            int i = out.lastIndexOf('|');
            if (out.isEmpty() || i < 0) {
                running = false;
                playing = false;
            } else {
                running = true;
                hasTimeline = false;
                track = out.substring(0, i).trim();
                playing = out.substring(i + 1).toLowerCase().contains("play");
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    }

    private static String run(String[] cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(3, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return "";
        }
    }
}
