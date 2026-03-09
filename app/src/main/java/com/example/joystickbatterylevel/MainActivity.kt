package com.example.joystickbatterylevel

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val requestBluetooth = 1
    private val macAddress = BuildConfig.JOYSTICK_MAC_ADDRESS
    private val joystickDisplayName = BuildConfig.DEVICE_NAME_DISPLAY
    private val batteryServiceUuid = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val batteryLevelUuid = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    private val deviceInfoServiceUuid = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
    private val firmwareRevisionUuid = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private lateinit var deviceNameText: TextView
    private lateinit var batteryText: TextView
    private lateinit var bluetoothStatusText: TextView
    private lateinit var firmwareText: TextView
    private lateinit var bluetoothIcon: ImageView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> updateConnectionUI("Desconectado", false)
                    BluetoothAdapter.STATE_ON -> checkBluetoothPermission()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceNameText = findViewById(R.id.deviceName)
        batteryText = findViewById(R.id.batterylevel)
        bluetoothStatusText = findViewById(R.id.bluetoothStatus)
        firmwareText = findViewById(R.id.firmwareVersion)
        bluetoothIcon = findViewById(R.id.bluetoothIcon)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        ViewCompat.setOnApplyWindowInsetsListener(swipeRefresh) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        swipeRefresh.setOnRefreshListener {
            if (bluetoothAdapter?.isEnabled == true) {
                checkBluetoothPermission()
            } else {
                Toast.makeText(this, "Activa el Bluetooth", Toast.LENGTH_SHORT).show()
                swipeRefresh.isRefreshing = false
            }
        }

        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        checkBluetoothPermission()
    }

    private fun updateBatteryUI(level: Int) {
        val color = when (level) {
            in 0..15 -> "#F44336".toColorInt()
            in 16..40 -> "#FF9800".toColorInt()
            else -> "#4CAF50".toColorInt()
        }
        runOnUiThread {
            batteryText.text = "$level%"
            batteryText.setTextColor(color)
            swipeRefresh.isRefreshing = false
        }
    }

    private fun updateConnectionUI(status: String, isConnected: Boolean) {
        runOnUiThread {
            bluetoothStatusText.text = status
            val isBtEnabled = bluetoothAdapter?.isEnabled == true

            if (isConnected) {

                deviceNameText.text = joystickDisplayName
                bluetoothIcon.setImageResource(R.drawable.outline_bluetooth_connected_24)
                bluetoothIcon.alpha = 1.0f
            } else {
                // Si el BT está apagado, mostramos el aviso. Si está encendido pero no conectado, "Buscando..."
                deviceNameText.text = if (isBtEnabled) "Bluetooth Encendido" else "Bluetooth Apagado"

                // Icono disabled solo si la antena está apagada
                if (isBtEnabled) {
                    bluetoothIcon.setImageResource(R.drawable.baseline_bluetooth_24)
                    bluetoothIcon.alpha = 0.8f
                } else {
                    bluetoothIcon.setImageResource(R.drawable.outline_bluetooth_disabled_24)
                    bluetoothIcon.alpha = 0.5f
                }

                batteryText.text = "--"
                batteryText.setTextColor(Color.GRAY)
                firmwareText.text = "Firmware: --"
            }
        }
    }

    private fun checkBluetoothPermission() {
        if (bluetoothAdapter == null) return
        if (!bluetoothAdapter!!.isEnabled) {
            updateConnectionUI("Desconectado", false)
            swipeRefresh.isRefreshing = false
            return
        }

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.ACCESS_FINE_LOCATION

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestBluetooth)
            swipeRefresh.isRefreshing = false
        } else {
            connectToDevice()
        }
    }

    @RequiresPermission(value = Manifest.permission.BLUETOOTH_CONNECT, conditional = true)
    private fun connectToDevice() {

        startConnectingAnimation()
        bluetoothGatt?.close()

        try {
            val device = bluetoothAdapter?.getRemoteDevice(macAddress)
            bluetoothGatt = device?.connectGatt(this, false, gattCallback)

            handler.postDelayed({
                if (connectingAnimation) {
                    stopConnectingAnimation()
                    updateConnectionUI("Error al conectar", false)
                    Toast.makeText(this, "Dispositivo fuera de alcance", Toast.LENGTH_SHORT).show()
                    swipeRefresh.isRefreshing = false
                    bluetoothGatt?.close()
                }
            }, 8000)

        } catch (_: Exception) {
            stopConnectingAnimation()
            updateConnectionUI("Error de conexión", false)
            swipeRefresh.isRefreshing = false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var connectingAnimation = false

    private val connectingRunnable = object : Runnable {
        private val states = arrayOf(
            "Conectando",
            "Conectando.",
            "Conectando..",
            "Conectando..."
        )
        private var index = 0

        override fun run() {
            if (!connectingAnimation) return

            updateConnectionUI(states[index], false)
            index = (index + 1) % states.size

            handler.postDelayed(this, 400)
        }
    }

    private fun startConnectingAnimation() {
        connectingAnimation = true
        handler.post(connectingRunnable)
    }

    private fun stopConnectingAnimation() {
        connectingAnimation = false
        handler.removeCallbacks(connectingRunnable)
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                stopConnectingAnimation()

                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    updateConnectionUI("Conectado", true)
                }

                gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                stopConnectingAnimation()

                val errorMsg =
                    if (status != BluetoothGatt.GATT_SUCCESS) "Error al conectar"
                    else "Desconectado"

                runOnUiThread {
                    if (swipeRefresh.isRefreshing) {
                        Toast.makeText(this@MainActivity, "Dispositivo fuera de alcance", Toast.LENGTH_SHORT).show()
                        swipeRefresh.isRefreshing = false
                    }
                }

                updateConnectionUI(errorMsg, false)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readBattery(gatt)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {

                    batteryLevelUuid -> {
                        val level = characteristic.value[0].toInt() and 0xFF
                        updateBatteryUI(level)
                        enableNotifications(gatt, characteristic)

                        val infoService = gatt.getService(deviceInfoServiceUuid)
                        val firmwareChar = infoService?.getCharacteristic(firmwareRevisionUuid)

                        if (firmwareChar != null) {
                            readCharacteristicWithDelay(gatt, firmwareChar, 400)
                        }
                    }

                    firmwareRevisionUuid -> {
                        val version = String(characteristic.value)
                        runOnUiThread { firmwareText.text = "Firmware: $version" }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == batteryLevelUuid) {
                updateBatteryUI(characteristic.value[0].toInt() and 0xFF)
            }
        }

        private fun readBattery(gatt: BluetoothGatt) {
            val service = gatt.getService(batteryServiceUuid)
            val char = service?.getCharacteristic(batteryLevelUuid)
            if (char != null) readCharacteristicWithDelay(gatt, char, 400)
        }

        private fun readCharacteristicWithDelay(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, delay: Long) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    gatt.readCharacteristic(char)
                }
            }, delay)
        }

        private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(cccdUuid)
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        stopConnectingAnimation()

        unregisterReceiver(bluetoothStateReceiver)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothGatt?.close()
        }
    }
}