using System;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace AudioBridge
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            // Only one copy: opening the app again just brings the running one to the front.
            using var mutex = new Mutex(true, "AudioBridge.SingleInstance", out bool first);
            var showSignal = new EventWaitHandle(false, EventResetMode.AutoReset, "AudioBridge.Show");
            if (!first) { showSignal.Set(); return; }

            Application.SetHighDpiMode(HighDpiMode.SystemAware);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            bool tray = args.Any(a => a.Equals("--tray", StringComparison.OrdinalIgnoreCase));
            Application.Run(new MainForm(tray, showSignal));
        }
    }
}
