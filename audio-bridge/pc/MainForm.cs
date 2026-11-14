using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;
using NAudio.CoreAudioApi;

namespace AudioBridge
{
    sealed class MainForm : Form
    {
        sealed class Dev
        {
            public string Id, Name;
            public override string ToString() => Name;
        }

        readonly Label status = new Label { AutoSize = true, Font = new Font("Segoe UI", 12f, FontStyle.Bold), Text = "Stopped" };
        readonly Label stats = new Label { AutoSize = true, ForeColor = Color.DimGray, Text = " " };
        readonly CheckBox sendSound = new CheckBox { AutoSize = true, Text = "Send PC sound to my phone", Checked = true };
        readonly CheckBox muteSpeakers = new CheckBox { AutoSize = true, Text = "Mute the PC speakers while the phone is connected (sound only on your headphones)", Checked = true };
        MMDevice mutedDevice;
        bool mutedBefore;
        readonly ComboBox captureBox = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 420 };
        readonly CheckBox useMic = new CheckBox { AutoSize = true, Text = "Use my phone / headphone mic as a PC microphone", Checked = true };
        readonly ComboBox micBox = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 420 };
        readonly LinkLabel cableLink = new LinkLabel { AutoSize = true, Text = "Needs the free VB-CABLE driver (one-time install) — click to download" };
        readonly TextBox ipBox = new TextBox { Width = 160, PlaceholderText = "automatic" };
        readonly Button startBtn = new Button { Text = "Start", Width = 140, Height = 36, Font = new Font("Segoe UI", 10f, FontStyle.Bold) };
        readonly Button beepBtn = new Button { Text = "Send test beep to phone", AutoSize = true, Enabled = false };
        readonly TextBox log = new TextBox { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Width = 440, Height = 110 };
        readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer { Interval = 500 };
        readonly CheckBox autoStart = new CheckBox { AutoSize = true, Text = "Connect automatically when the app opens", Checked = true };
        readonly CheckBox winStart = new CheckBox { AutoSize = true, Text = "Open Audio Bridge when Windows starts (hidden in the tray)" };
        readonly NotifyIcon tray = new NotifyIcon { Text = "Audio Bridge", Visible = true };
        readonly ToolStripMenuItem trayToggle = new ToolStripMenuItem("Start");
        readonly bool startHidden;
        bool trayHintShown;
        const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

        Bridge bridge;
        static readonly string SettingsFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "AudioBridge", "settings.txt");

        public MainForm(bool startHidden, EventWaitHandle showSignal)
        {
            this.startHidden = startHidden;
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            tray.Icon = Icon ?? SystemIcons.Application;
            var menu = new ContextMenuStrip();
            menu.Items.Add("Open", null, (s, e) => ShowFromTray());
            menu.Items.Add(trayToggle);
            menu.Items.Add("Exit", null, (s, e) => Close());
            trayToggle.Click += (s, e) => { if (bridge == null) StartBridge(); else StopBridge(); };
            tray.ContextMenuStrip = menu;
            tray.DoubleClick += (s, e) => ShowFromTray();
            new Thread(() => { while (showSignal.WaitOne()) { try { BeginInvoke(new Action(ShowFromTray)); } catch { return; } } }) { IsBackground = true }.Start();

            Text = "Audio Bridge " + Application.ProductVersion.Split('+')[0];
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            AutoSize = true;
            AutoSizeMode = AutoSizeMode.GrowAndShrink;
            Font = new Font("Segoe UI", 9f);
            StartPosition = FormStartPosition.CenterScreen;

            var root = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.TopDown, WrapContents = false, AutoSize = true,
                Padding = new Padding(14), Dock = DockStyle.Fill,
            };
            Controls.Add(root);

            root.Controls.Add(status);
            root.Controls.Add(stats);
            root.Controls.Add(Gap());

            root.Controls.Add(sendSound);
            root.Controls.Add(Hint("Sound from this output device goes to the phone:"));
            root.Controls.Add(captureBox);
            root.Controls.Add(muteSpeakers);
            root.Controls.Add(Gap());

            root.Controls.Add(useMic);
            root.Controls.Add(Hint("Play the phone's mic into (pick \"CABLE Input\"):"));
            root.Controls.Add(micBox);
            root.Controls.Add(Hint("Then in Discord / games / Windows pick \"CABLE Output\" as your microphone."));
            root.Controls.Add(cableLink);
            root.Controls.Add(Gap());

            var ipRow = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0) };
            ipRow.Controls.Add(new Label { Text = "Phone IP:", AutoSize = true, Margin = new Padding(0, 6, 6, 0) });
            ipRow.Controls.Add(ipBox);
            ipRow.Controls.Add(new Label { Text = "(leave empty unless it can't find the phone)", AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(6, 6, 0, 0) });
            root.Controls.Add(ipRow);
            root.Controls.Add(Gap());

            var btnRow = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = new Padding(0) };
            btnRow.Controls.Add(startBtn);
            btnRow.Controls.Add(beepBtn);
            beepBtn.Margin = new Padding(10, 6, 0, 0);
            root.Controls.Add(btnRow);
            root.Controls.Add(autoStart);
            root.Controls.Add(winStart);
            root.Controls.Add(log);

            cableLink.LinkClicked += (s, e) => Process.Start(new ProcessStartInfo("https://vb-audio.com/Cable/") { UseShellExecute = true });
            beepBtn.Click += (s, e) => { bridge?.SendTestTone(); AddLog("Sent a test beep. You should hear it in your headphones now."); };
            startBtn.Click += (s, e) => { if (bridge == null) StartBridge(); else StopBridge(); };
            muteSpeakers.CheckedChanged += (s, e) => { if (bridge?.Phone != null) ApplyMute(); else RestoreMute(); SaveSettings(); };
            sendSound.CheckedChanged += (s, e) => captureBox.Enabled = sendSound.Checked && bridge == null;
            useMic.CheckedChanged += (s, e) => micBox.Enabled = useMic.Checked && bridge == null;
            timer.Tick += (s, e) => UpdateStatus();
            FormClosing += (s, e) => { SaveSettings(); StopBridge(); tray.Visible = false; };
            Resize += (s, e) =>
            {
                if (WindowState != FormWindowState.Minimized) return;
                Hide();
                if (!trayHintShown) { trayHintShown = true; tray.ShowBalloonTip(3000, "Audio Bridge", "Still running here. Double-click to open, right-click to exit.", ToolTipIcon.Info); }
            };
            Shown += (s, e) =>
            {
                if (autoStart.Checked && bridge == null) StartBridge();
                if (this.startHidden) BeginInvoke(new Action(() => { WindowState = FormWindowState.Minimized; }));
            };
            winStart.CheckedChanged += (s, e) => SetRunAtLogin(winStart.Checked);
            autoStart.CheckedChanged += (s, e) => SaveSettings();

            LoadDevices();
            LoadSettings();
            winStart.Checked = IsRunAtLogin();
            AddLog("1) Plug the phone in with USB and turn on USB tethering.  2) Open Audio Bridge on the phone and press Start.  3) Press Start here.");
        }

        static Label Hint(string text) => new Label { Text = text, AutoSize = true, ForeColor = Color.DimGray, Margin = new Padding(3, 4, 3, 2) };
        static Control Gap() => new Panel { Height = 6, Width = 1 };

        void LoadDevices()
        {
            var en = new MMDeviceEnumerator();
            var outs = en.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active)
                         .Select(d => new Dev { Id = d.ID, Name = d.FriendlyName }).ToList();
            string defId = null;
            try { defId = en.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia).ID; } catch { }

            captureBox.Items.Clear(); micBox.Items.Clear();
            foreach (var d in outs) { captureBox.Items.Add(d); micBox.Items.Add(d); }
            captureBox.SelectedItem = outs.FirstOrDefault(d => d.Id == defId) ?? outs.FirstOrDefault();
            micBox.SelectedItem = outs.FirstOrDefault(d => d.Name.IndexOf("CABLE Input", StringComparison.OrdinalIgnoreCase) >= 0);
            cableLink.Visible = micBox.SelectedItem == null;
            if (outs.Count == 0) AddLog("No sound output devices found. Windows needs at least one (VB-CABLE counts).");
        }

        void StartBridge()
        {
            var cap = sendSound.Checked ? captureBox.SelectedItem as Dev : null;
            var mic = useMic.Checked ? micBox.SelectedItem as Dev : null;
            if (sendSound.Checked && cap == null) { AddLog("Pick an output device to send."); return; }
            if (useMic.Checked && mic == null) { AddLog("Pick where the mic should play (install VB-CABLE, then restart this app)."); return; }
            if (cap != null && mic != null && cap.Id == mic.Id)
            {
                MessageBox.Show(this, "The mic and the PC sound use the same device, so you'd hear yourself echo. Pick \"CABLE Input\" for the mic and your normal speakers/headphones for PC sound.", "Audio Bridge");
                return;
            }
            IPAddress ip = null;
            if (ipBox.Text.Trim().Length > 0 && !IPAddress.TryParse(ipBox.Text.Trim(), out ip)) { AddLog("That phone IP doesn't look right."); return; }

            try
            {
                bridge = new Bridge(cap?.Id, mic?.Id, ip);
                bridge.Log += m => BeginInvoke(new Action(() => AddLog(m)));
                bridge.Start();
            }
            catch (Exception ex)
            {
                AddLog("Could not start: " + ex.Message);
                try { bridge?.Dispose(); } catch { }
                bridge = null;
                return;
            }
            startBtn.Text = "Stop"; trayToggle.Text = "Stop";
            captureBox.Enabled = micBox.Enabled = sendSound.Enabled = useMic.Enabled = ipBox.Enabled = false;
            timer.Start();
            SaveSettings();
            UpdateStatus();
        }

        void StopBridge()
        {
            timer.Stop();
            var b = bridge; bridge = null;
            b?.Dispose();
            RestoreMute();
            if (IsDisposed) return;
            startBtn.Text = "Start"; trayToggle.Text = "Start";
            tray.Text = "Audio Bridge (stopped)";
            sendSound.Enabled = useMic.Enabled = ipBox.Enabled = true;
            captureBox.Enabled = sendSound.Checked;
            micBox.Enabled = useMic.Checked;
            status.Text = "Stopped"; status.ForeColor = SystemColors.ControlText;
            stats.Text = " ";
            beepBtn.Enabled = false;
        }

        void UpdateStatus()
        {
            var b = bridge;
            if (b == null) return;
            if (b.Phone != null)
            {
                status.Text = "Connected to " + (string.IsNullOrWhiteSpace(b.PhoneName) ? "phone" : b.PhoneName);
                status.ForeColor = Color.ForestGreen;
                tray.Text = "Audio Bridge: connected";
                ApplyMute();
                int lvl = (int)Math.Round(Math.Min(1f, b.Level) * 100);
                string lvlText = !sendSound.Checked ? "off" : lvl == 0 ? "silent (nothing playing?)" : lvl + "%";
                stats.Text = $"Phone {b.Phone.Address}   ·   PC sound level: {lvlText}\nsound packets sent {b.SentPackets}   ·   mic packets {b.MicPackets}" + (b.MicDropped > 0 ? $" (skipped {b.MicDropped} to stay in sync)" : "");
                beepBtn.Enabled = true;
            }
            else
            {
                status.Text = "Looking for the phone…";
                status.ForeColor = Color.DarkOrange;
                tray.Text = "Audio Bridge: looking for the phone";
                RestoreMute();
                stats.Text = "Is USB tethering on, and did you press Start in the phone app?";
                beepBtn.Enabled = false;
            }
        }

        void AddLog(string m)
        {
            if (IsDisposed) return;
            log.AppendText($"[{DateTime.Now:HH:mm:ss}] {m}\r\n");
        }

        void LoadSettings()
        {
            try
            {
                if (!File.Exists(SettingsFile)) return;
                var kv = File.ReadAllLines(SettingsFile).Select(l => l.Split(new[] { '=' }, 2)).Where(p => p.Length == 2).ToDictionary(p => p[0], p => p[1]);
                if (kv.TryGetValue("mute", out var mu)) muteSpeakers.Checked = mu == "1";
                if (kv.TryGetValue("send", out var s)) sendSound.Checked = s == "1";
                if (kv.TryGetValue("mic", out var m)) useMic.Checked = m == "1";
                if (kv.TryGetValue("auto", out var au)) autoStart.Checked = au == "1";
                if (kv.TryGetValue("ip", out var ip)) ipBox.Text = ip;
                if (kv.TryGetValue("capture", out var c)) Select(captureBox, c);
                if (kv.TryGetValue("micdev", out var md)) Select(micBox, md);
            }
            catch { }
        }

        /// <summary>Mutes the captured output device. Windows still hands the sound to Audio Bridge, so only the phone plays it.</summary>
        void ApplyMute()
        {
            var cap = sendSound.Checked ? captureBox.SelectedItem as Dev : null;
            if (!muteSpeakers.Checked || cap == null) { RestoreMute(); return; }
            if (mutedDevice != null) return;
            try
            {
                mutedDevice = new MMDeviceEnumerator().GetDevice(cap.Id);
                mutedBefore = mutedDevice.AudioEndpointVolume.Mute;
                mutedDevice.AudioEndpointVolume.Mute = true;
                AddLog("PC speakers muted (they come back when the phone disconnects or you press Stop). If your headphones go silent too, untick \"Mute the PC speakers\".");
            }
            catch (Exception ex) { AddLog("Could not mute the speakers: " + ex.Message); mutedDevice = null; }
        }

        void RestoreMute()
        {
            var d = mutedDevice; mutedDevice = null;
            if (d == null) return;
            try { d.AudioEndpointVolume.Mute = mutedBefore; } catch { }
        }

        void ShowFromTray()
        {
            Show();
            WindowState = FormWindowState.Normal;
            Activate();
        }

        static bool IsRunAtLogin()
        {
            try { using var k = Registry.CurrentUser.OpenSubKey(RunKey); return k?.GetValue("AudioBridge") != null; } catch { return false; }
        }

        void SetRunAtLogin(bool on)
        {
            try
            {
                using var k = Registry.CurrentUser.CreateSubKey(RunKey);
                if (on) k.SetValue("AudioBridge", "\"" + Application.ExecutablePath + "\" --tray");
                else k.DeleteValue("AudioBridge", false);
            }
            catch (Exception ex) { AddLog("Could not change the Windows startup setting: " + ex.Message); }
        }

        static void Select(ComboBox box, string id)
        {
            foreach (Dev d in box.Items) if (d.Id == id) { box.SelectedItem = d; return; }
        }

        void SaveSettings()
        {
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(SettingsFile));
                var lines = new List<string>
                {
                    "send=" + (sendSound.Checked ? "1" : "0"),
                    "mute=" + (muteSpeakers.Checked ? "1" : "0"),
                    "mic=" + (useMic.Checked ? "1" : "0"),
                    "ip=" + ipBox.Text.Trim(),
                    "auto=" + (autoStart.Checked ? "1" : "0"),
                    "capture=" + ((captureBox.SelectedItem as Dev)?.Id ?? ""),
                    "micdev=" + ((micBox.SelectedItem as Dev)?.Id ?? ""),
                };
                File.WriteAllLines(SettingsFile, lines);
            }
            catch { }
        }
    }
}
