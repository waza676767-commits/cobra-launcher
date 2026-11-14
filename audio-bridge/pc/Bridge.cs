using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

namespace AudioBridge
{
    /// <summary>Records what an output device is playing (WASAPI loopback) with a small 10 ms buffer.</summary>
    sealed class LoopbackCapture : WasapiCapture
    {
        public LoopbackCapture(MMDevice device) : base(device, false, 10) { }
        protected override AudioClientStreamFlags GetAudioClientStreamFlags() => AudioClientStreamFlags.Loopback;
    }

    /// <summary>
    /// Talks to the phone app over UDP (the USB-tethering network).
    /// Packet = 12-byte header ("AB", type, channels, sampleRate LE32, seq LE32) + 16-bit PCM.
    /// </summary>
    sealed class Bridge : IDisposable
    {
        public const int Port = 50505;
        const byte THello = 1, TAck = 2, TAudio = 3, TMic = 4, TBye = 5;
        const int Hdr = 12;
        const int FramesPerPacket = 240;   // 5 ms at 48 kHz
        const int MicMaxBufferMs = 60;     // never let the mic lag more than this
        static readonly Guid FloatSubtype = new Guid("00000003-0000-0010-8000-00aa00389b71");

        readonly string captureDeviceId;   // null = don't send PC sound
        readonly string micDeviceId;       // null = don't play the phone mic
        readonly IPAddress manualIp;       // null = find the phone automatically

        Socket sock;
        Thread rxThread, helloThread;
        volatile bool stopping;
        volatile IPEndPoint phone;
        long lastHeard;

        LoopbackCapture capture;
        int srcChannels, srcBytes, srcRate;
        bool srcFloat;
        readonly byte[] pkt = new byte[Hdr + FramesPerPacket * 4];
        int accFrames;
        uint seq;

        WasapiOut micOut;
        BufferedWaveProvider micBuffer;
        int micRate;

        public string PhoneName { get; private set; }
        public IPEndPoint Phone => phone;
        public long SentPackets, MicPackets, MicDropped;
        float peak;
        long peakTicks;
        /// <summary>Loudest sample captured in the last half second (0..1).</summary>
        public float Level => Environment.TickCount64 - Interlocked.Read(ref peakTicks) > 500 ? 0 : peak;
        volatile bool toneRunning;
        public event Action<string> Log;

        public Bridge(string captureDeviceId, string micDeviceId, IPAddress manualIp)
        {
            this.captureDeviceId = captureDeviceId;
            this.micDeviceId = micDeviceId;
            this.manualIp = manualIp;
        }

        public void Start()
        {
            sock = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            sock.EnableBroadcast = true;
            sock.ReceiveTimeout = 500;
            sock.SendBufferSize = 64 * 1024;
            try
            {
                // Stop Windows from turning "port unreachable" replies into receive errors.
                const int SIO_UDP_CONNRESET = -1744830452;
                sock.IOControl(SIO_UDP_CONNRESET, new byte[] { 0, 0, 0, 0 }, null);
            }
            catch { }
            sock.Bind(new IPEndPoint(IPAddress.Any, 0));

            if (captureDeviceId != null)
            {
                var dev = new MMDeviceEnumerator().GetDevice(captureDeviceId);
                capture = new LoopbackCapture(dev);
                var wf = capture.WaveFormat;
                srcChannels = wf.Channels;
                srcBytes = wf.BitsPerSample / 8;
                srcRate = wf.SampleRate;
                srcFloat = wf.Encoding == WaveFormatEncoding.IeeeFloat ||
                           (wf is WaveFormatExtensible ext && ext.SubFormat == FloatSubtype);
                capture.DataAvailable += OnCaptured;
                capture.RecordingStopped += (s, e) =>
                {
                    if (e.Exception != null) Log?.Invoke("PC sound capture stopped: " + e.Exception.Message);
                };
                capture.StartRecording();
                Log?.Invoke($"Capturing \"{dev.FriendlyName}\" ({srcRate} Hz, {srcChannels} ch)");
            }

            rxThread = new Thread(RxLoop) { IsBackground = true, Priority = ThreadPriority.Highest, Name = "rx" };
            helloThread = new Thread(HelloLoop) { IsBackground = true, Name = "hello" };
            rxThread.Start();
            helloThread.Start();
            Log?.Invoke(manualIp != null ? $"Looking for the phone at {manualIp}…" : "Looking for the phone…");
        }

        // ---------- PC sound -> phone ----------

        float Sample(byte[] b, int o)
        {
            if (srcFloat) return BitConverter.ToSingle(b, o);
            switch (srcBytes)
            {
                case 2: return BitConverter.ToInt16(b, o) / 32768f;
                case 3: return ((b[o] | (b[o + 1] << 8) | ((sbyte)b[o + 2] << 16))) / 8388608f;
                case 4: return BitConverter.ToInt32(b, o) / 2147483648f;
                default: return 0;
            }
        }

        static void PutS16(byte[] b, int o, float v)
        {
            int s = (int)(v * 32767f);
            if (s > 32767) s = 32767; else if (s < -32768) s = -32768;
            b[o] = (byte)s;
            b[o + 1] = (byte)(s >> 8);
        }

        void OnCaptured(object sender, WaveInEventArgs e)
        {
            var to = phone;
            if (to == null || toneRunning) { accFrames = 0; return; }
            float max = 0;
            int frameBytes = srcChannels * srcBytes;
            int frames = e.BytesRecorded / frameBytes;
            for (int i = 0; i < frames; i++)
            {
                int o = i * frameBytes;
                float l, r;
                if (srcChannels == 1) { l = r = Sample(e.Buffer, o); }
                else if (srcChannels == 2) { l = Sample(e.Buffer, o); r = Sample(e.Buffer, o + srcBytes); }
                else
                {
                    // Surround -> stereo: FL FR FC LFE BL BR ... (keep the centre channel, it has the voices)
                    float fl = Sample(e.Buffer, o), fr = Sample(e.Buffer, o + srcBytes);
                    float c = Sample(e.Buffer, o + 2 * srcBytes);
                    float bl = srcChannels >= 6 ? Sample(e.Buffer, o + 4 * srcBytes) : 0;
                    float br = srcChannels >= 6 ? Sample(e.Buffer, o + 5 * srcBytes) : 0;
                    l = (fl + 0.7f * c + 0.7f * bl) * 0.6f;
                    r = (fr + 0.7f * c + 0.7f * br) * 0.6f;
                }
                float al = Math.Abs(l), ar = Math.Abs(r);
                if (al > max) max = al;
                if (ar > max) max = ar;
                int p = Hdr + accFrames * 4;
                PutS16(pkt, p, l);
                PutS16(pkt, p + 2, r);
                if (++accFrames == FramesPerPacket) Flush(to);
            }
            if (accFrames > 0) Flush(to);   // don't hold sound back waiting for a full packet
            long now = Environment.TickCount64;
            if (max >= peak || now - Interlocked.Read(ref peakTicks) > 500) { peak = max; Interlocked.Exchange(ref peakTicks, now); }
        }

        void Flush(IPEndPoint to)
        {
            WriteHeader(pkt, TAudio, 2, srcRate, seq++);
            Send(pkt, Hdr + accFrames * 4, to);
            accFrames = 0;
            SentPackets++;
        }

        /// <summary>Plays a 1.5 s beep on the phone, to check the phone → headphones part on its own.</summary>
        public void SendTestTone()
        {
            var to = phone;
            if (to == null || toneRunning) return;
            toneRunning = true;
            new Thread(() =>
            {
                try
                {
                    const int rate = 48000, total = rate * 3 / 2;
                    var b = new byte[Hdr + FramesPerPacket * 4];
                    var sw = Stopwatch.StartNew();
                    int sent = 0; uint tseq = 0;
                    while (sent < total && !stopping)
                    {
                        int due = (int)(sw.Elapsed.TotalSeconds * rate) + rate / 50;   // stay ~20 ms ahead
                        while (sent < due && sent < total)
                        {
                            int n = Math.Min(FramesPerPacket, total - sent);
                            for (int i = 0; i < n; i++)
                            {
                                double t = (double)(sent + i) / rate;
                                float v = (float)(0.3 * Math.Sin(2 * Math.PI * 660 * t));
                                PutS16(b, Hdr + i * 4, v);
                                PutS16(b, Hdr + i * 4 + 2, v);
                            }
                            WriteHeader(b, TAudio, 2, rate, tseq++);
                            Send(b, Hdr + n * 4, to);
                            sent += n;
                        }
                        Thread.Sleep(2);
                    }
                }
                finally { toneRunning = false; }
            }) { IsBackground = true, Priority = ThreadPriority.Highest }.Start();
        }

        // ---------- network ----------

        static void WriteHeader(byte[] b, byte type, int channels, int rate, uint seq)
        {
            b[0] = (byte)'A'; b[1] = (byte)'B'; b[2] = type; b[3] = (byte)channels;
            b[4] = (byte)rate; b[5] = (byte)(rate >> 8); b[6] = (byte)(rate >> 16); b[7] = (byte)(rate >> 24);
            b[8] = (byte)seq; b[9] = (byte)(seq >> 8); b[10] = (byte)(seq >> 16); b[11] = (byte)(seq >> 24);
        }

        void Send(byte[] b, int len, IPEndPoint to)
        {
            try { sock.SendTo(b, 0, len, SocketFlags.None, to); } catch { }
        }

        IEnumerable<IPAddress> Targets()
        {
            if (manualIp != null) { yield return manualIp; yield break; }
            var seen = new HashSet<string>();
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                IPInterfaceProperties props;
                try { props = ni.GetIPProperties(); } catch { continue; }
                // With USB tethering the phone is the PC's gateway on that adapter.
                foreach (var gw in props.GatewayAddresses)
                    if (gw.Address.AddressFamily == AddressFamily.InterNetwork && !gw.Address.Equals(IPAddress.Any) && seen.Add(gw.Address.ToString()))
                        yield return gw.Address;
                foreach (var ua in props.UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork || ua.IPv4Mask == null) continue;
                    var a = ua.Address.GetAddressBytes(); var m = ua.IPv4Mask.GetAddressBytes();
                    var bc = new byte[4];
                    for (int i = 0; i < 4; i++) bc[i] = (byte)(a[i] | ~m[i]);
                    var bcast = new IPAddress(bc);
                    if (seen.Add(bcast.ToString())) yield return bcast;
                }
            }
            yield return IPAddress.Broadcast;
        }

        void HelloLoop()
        {
            var hello = new byte[Hdr];
            while (!stopping)
            {
                var p = phone;
                if (p != null && Environment.TickCount64 - Interlocked.Read(ref lastHeard) > 4000)
                {
                    phone = null; p = null;
                    Log?.Invoke("Lost the phone, searching again…");
                }
                WriteHeader(hello, THello, 0, 0, 0);
                if (p != null) Send(hello, Hdr, p);
                else
                {
                    try { foreach (var t in Targets()) Send(hello, Hdr, new IPEndPoint(t, Port)); } catch { }
                }
                for (int i = 0; i < 10 && !stopping; i++) Thread.Sleep(100);
            }
        }

        void RxLoop()
        {
            var buf = new byte[8192];
            while (!stopping)
            {
                EndPoint from = new IPEndPoint(IPAddress.Any, 0);
                int n;
                try { n = sock.ReceiveFrom(buf, ref from); }
                catch (SocketException ex) when (ex.SocketErrorCode == SocketError.TimedOut) { continue; }
                catch { if (stopping) break; Thread.Sleep(50); continue; }
                if (n < Hdr || buf[0] != (byte)'A' || buf[1] != (byte)'B') continue;
                var ep = (IPEndPoint)from;
                switch (buf[2])
                {
                    case TAck:
                        Interlocked.Exchange(ref lastHeard, Environment.TickCount64);
                        if (phone == null || !phone.Equals(ep))
                        {
                            PhoneName = Encoding.UTF8.GetString(buf, Hdr, n - Hdr);
                            phone = new IPEndPoint(ep.Address, ep.Port);
                            Log?.Invoke($"Connected to {PhoneName} ({ep.Address})");
                        }
                        break;
                    case TMic:
                        Interlocked.Exchange(ref lastHeard, Environment.TickCount64);
                        if (micDeviceId == null) break;
                        MicPackets++;
                        int rate = BitConverter.ToInt32(buf, 4);
                        if (rate < 8000 || rate > 96000) break;
                        EnsureMicOut(rate);
                        var mb = micBuffer;
                        if (mb == null) break;
                        if (mb.BufferedDuration.TotalMilliseconds < MicMaxBufferMs) mb.AddSamples(buf, Hdr, n - Hdr);
                        else MicDropped++;
                        break;
                    case TBye:
                        if (phone != null && phone.Equals(ep)) { phone = null; Log?.Invoke("Phone stopped."); }
                        break;
                }
            }
        }

        // ---------- phone mic -> virtual cable ----------

        void EnsureMicOut(int rate)
        {
            if (micBuffer != null && micRate == rate) return;
            CloseMicOut();
            try
            {
                var dev = new MMDeviceEnumerator().GetDevice(micDeviceId);
                var buffer = new BufferedWaveProvider(new WaveFormat(rate, 16, 1))
                {
                    BufferDuration = TimeSpan.FromMilliseconds(500),
                    DiscardOnBufferOverflow = true,
                };
                var mix = dev.AudioClient.MixFormat;
                ISampleProvider sp = buffer.ToSampleProvider();
                if (mix.Channels >= 2) sp = new MonoToStereoSampleProvider(sp);
                if (mix.SampleRate != rate) sp = new WdlResamplingSampleProvider(sp, mix.SampleRate);
                var output = new WasapiOut(dev, AudioClientShareMode.Shared, true, 20);
                output.Init(sp.ToWaveProvider());
                output.Play();
                micOut = output; micRate = rate; micBuffer = buffer;
                Log?.Invoke($"Phone mic → \"{dev.FriendlyName}\" ({rate} Hz)");
            }
            catch (Exception ex)
            {
                Log?.Invoke("Could not play the mic: " + ex.Message);
                micRate = rate; micBuffer = null;
            }
        }

        void CloseMicOut()
        {
            var o = micOut; micOut = null; micBuffer = null;
            if (o == null) return;
            try { o.Stop(); } catch { }
            try { o.Dispose(); } catch { }
        }

        public void Dispose()
        {
            stopping = true;
            var p = phone;
            if (p != null && sock != null)
            {
                var bye = new byte[Hdr];
                WriteHeader(bye, TBye, 0, 0, 0);
                Send(bye, Hdr, p);
            }
            try { capture?.StopRecording(); } catch { }
            try { capture?.Dispose(); } catch { }
            try { sock?.Close(); } catch { }
            rxThread?.Join(1500);
            helloThread?.Join(1500);
            CloseMicOut();
        }
    }
}
