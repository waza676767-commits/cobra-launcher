package app.velo.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.velo.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What we know about the earbuds currently paired/connected. */
data class BudsState(
    val address: String,
    val name: String,
    val connected: Boolean,
    val battery: Int?,          // 0..100 (Android reports one value for the pair)
    val model: BudsModel
)

data class Nearby(val address: String, val name: String, val rssi: Int, val bonded: Boolean)

/**
 * Everything Bluetooth: which earbuds are connected, battery, saved list, connect, and the
 * "find nearest" scan. Uses broadcasts instead of polling so it costs nothing while idle.
 */
@SuppressLint("MissingPermission")
object Buds {
    const val ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private lateinit var app: Context
    private var adapter: BluetoothAdapter? = null
    private var a2dp: BluetoothA2dp? = null
    private var headset: BluetoothHeadset? = null
    private val main = Handler(Looper.getMainLooper())

    private val _current = MutableStateFlow<BudsState?>(null)
    val current: StateFlow<BudsState?> = _current.asStateFlow()

    private val _paired = MutableStateFlow<List<BudsState>>(emptyList())
    val paired: StateFlow<List<BudsState>> = _paired.asStateFlow()

    private val _nearby = MutableStateFlow<List<Nearby>>(emptyList())
    val nearby: StateFlow<List<Nearby>> = _nearby.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _connecting = MutableStateFlow<String?>(null)
    val connecting: StateFlow<String?> = _connecting.asStateFlow()

    private val batteries = HashMap<String, Int>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bindProfiles()
        refresh()
    }

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScan(): Boolean = Build.VERSION.SDK_INT < 31 ||
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    fun bindProfiles() {
        val ad = adapter ?: return
        if (!hasPermission()) return
        val l = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                when (profile) {
                    BluetoothProfile.A2DP -> a2dp = proxy as BluetoothA2dp
                    BluetoothProfile.HEADSET -> headset = proxy as BluetoothHeadset
                }
                refresh()
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) a2dp = null else headset = null
            }
        }
        runCatching {
            ad.getProfileProxy(app, l, BluetoothProfile.A2DP)
            ad.getProfileProxy(app, l, BluetoothProfile.HEADSET)
        }
    }

    private fun isAudio(d: BluetoothDevice): Boolean {
        val c = runCatching { d.bluetoothClass }.getOrNull() ?: return true
        return c.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            c.hasService(BluetoothClass.Service.AUDIO)
    }

    private fun readBattery(d: BluetoothDevice): Int? {
        batteries[d.address]?.let { return it }
        // BluetoothDevice.getBatteryLevel() is hidden but readable on most phones; -1 means unknown.
        val v = runCatching { d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int }.getOrNull()
        return v?.takeIf { it in 0..100 }
    }

    private fun connectedSet(): Set<String> {
        val s = HashSet<String>()
        runCatching { a2dp?.connectedDevices?.forEach { s += it.address } }
        runCatching { headset?.connectedDevices?.forEach { s += it.address } }
        return s
    }

    private fun stateOf(d: BluetoothDevice, connected: Set<String>): BudsState {
        val name = runCatching { d.alias ?: d.name }.getOrNull() ?: d.address
        return BudsState(d.address, name, d.address in connected, readBattery(d), BudsModels.match(name))
    }

    fun refresh() {
        val ad = adapter ?: return
        if (!hasPermission()) return
        val connected = connectedSet()
        val bonded = runCatching { ad.bondedDevices.toList() }.getOrDefault(emptyList()).filter(::isAudio)
        val states = bonded.map { stateOf(it, connected) }
            .sortedWith(compareByDescending<BudsState> { it.connected }.thenBy { savedRank(it.address) })
        _paired.value = states
        val cur = states.firstOrNull { it.connected }
        _current.value = cur ?: states.firstOrNull { savedRank(it.address) == 0 }?.copy(connected = false)
        if (cur != null && Store.now.saved.firstOrNull()?.address != cur.address) Store.remember(cur.address, cur.name)
    }

    private fun savedRank(addr: String): Int =
        Store.now.saved.indexOfFirst { it.address == addr }.let { if (it < 0) Int.MAX_VALUE else it }

    fun onBattery(address: String, level: Int) {
        if (level in 0..100) batteries[address] = level
        refresh()
    }

    /** Connects bonded earbuds; pairs new ones. Falls back to the Bluetooth panel where Android blocks it. */
    fun connect(address: String): Boolean {
        val ad = adapter ?: return false
        val d = runCatching { ad.getRemoteDevice(address) }.getOrNull() ?: return false
        _connecting.value = address
        main.postDelayed({ if (_connecting.value == address) _connecting.value = null; refresh() }, 12000)
        if (d.bondState != BluetoothDevice.BOND_BONDED) return d.createBond()
        var ok = false
        for (p in listOf(a2dp, headset)) {
            if (p == null) continue
            ok = runCatching {
                p.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(p, d) as Boolean
            }.getOrDefault(false) || ok
        }
        if (!ok) {
            _connecting.value = null
            openBluetoothPanel()
        }
        return ok
    }

    fun openBluetoothPanel() {
        val i = if (Build.VERSION.SDK_INT >= 29) Intent("android.settings.panel.action.BLUETOOTH")
        else Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        runCatching { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            app.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun onConnectionChanged() {
        _connecting.value = null
        // Profiles report the new state a beat after the ACL event.
        main.postDelayed({ refresh() }, 800)
        main.postDelayed({ refresh() }, 3000)
    }

    // ---------- Find nearest: classic discovery + BLE, sorted by signal strength ----------

    private val found = HashMap<String, Nearby>()
    private var scanReceiver: BroadcastReceiver? = null
    private val bleCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: return
            add(r.device, name, r.rssi)
        }
    }

    private fun add(d: BluetoothDevice, name: String, rssi: Int) {
        val bonded = d.bondState == BluetoothDevice.BOND_BONDED
        val prev = found[d.address]
        // Smooth RSSI so the list doesn't jump around.
        val smoothed = if (prev == null) rssi else (prev.rssi * 0.6 + rssi * 0.4).toInt()
        found[d.address] = Nearby(d.address, name, smoothed, bonded)
        _nearby.value = found.values.sortedByDescending { it.rssi }
    }

    fun startScan() {
        val ad = adapter ?: return
        if (!hasScan() || _scanning.value) return
        found.clear(); _nearby.value = emptyList()
        _scanning.value = true
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == BluetoothDevice.ACTION_FOUND) {
                    val d: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
                    if (!isAudio(d)) return
                    val rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    add(d, runCatching { d.name }.getOrNull() ?: d.address, rssi)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(scanReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND), Context.RECEIVER_EXPORTED)
        else app.registerReceiver(scanReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        runCatching { ad.startDiscovery() }
        runCatching {
            ad.bluetoothLeScanner?.startScan(null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), bleCb)
        }
        // Scans are battery-hungry: always stop on their own.
        main.postDelayed({ stopScan() }, 15000)
    }

    fun stopScan() {
        if (!_scanning.value) return
        _scanning.value = false
        runCatching { adapter?.cancelDiscovery() }
        runCatching { adapter?.bluetoothLeScanner?.stopScan(bleCb) }
        runCatching { scanReceiver?.let { app.unregisterReceiver(it) } }
        scanReceiver = null
    }

    /** Used by screenshot tests to show sample devices. */
    fun showSample(cur: BudsState?, paired: List<BudsState>, nearby: List<Nearby>) {
        _current.value = cur; _paired.value = paired; _nearby.value = nearby
    }

    fun distanceKey(rssi: Int) = when {
        rssi >= -50 -> "very_close"
        rssi >= -65 -> "close"
        rssi >= -80 -> "near"
        else -> "far"
    }
}

/** Static receiver: keeps state fresh and starts/stops "Hey Velo" with the earbuds. */
class BudsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Buds.ACTION_BATTERY -> {
                val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                d?.let { Buds.onBattery(it.address, i.getIntExtra(Buds.EXTRA_BATTERY, -1)) }
            }
            else -> {
                Buds.onConnectionChanged()
                app.velo.voice.VeloService.budsChanged(c)
            }
        }
    }
}
