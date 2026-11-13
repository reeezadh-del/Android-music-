package com.example.headunit

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Real Android USB Host & AOAP (Android Open Accessory Protocol) manager.
 * Detects connected USB phones/accessories, requests UsbManager permissions,
 * and sends AOAP 2.0 control transfers (0x33/0x34/0x35) to switch a phone
 * into Android Auto Accessory mode (VID 0x18D1, PID 0x2D00..0x2D05).
 */
class AoapUsbManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _uiState = MutableStateFlow(UsbHostUiState())
    val uiState: StateFlow<UsbHostUiState> = _uiState.asStateFlow()

    private var registered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION,
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    refreshDevices()
                }
            }
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
        }
        registered = true
        refreshDevices()
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(usbReceiver) }
        registered = false
    }

    fun refreshDevices() {
        val manager = usbManager
        if (manager == null) {
            _uiState.value = UsbHostUiState(statusMessage = "سخت‌افزار USB Host روی این دستگاه در دسترس نیست")
            return
        }

        val deviceSummaries = manager.deviceList.values.map { device ->
            val aoap = isAoapDevice(device.vendorId, device.productId)
            val label = device.productName?.takeIf { it.isNotBlank() }
                ?: device.manufacturerName?.takeIf { it.isNotBlank() }
                ?: "USB ${String.format("%04X:%04X", device.vendorId, device.productId)}"
            UsbDeviceSummary(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                productLabel = label,
                isAoapMode = aoap,
                hasPermission = manager.hasPermission(device)
            )
        }

        val accessory = manager.accessoryList?.firstOrNull()
        val accessoryLabel = accessory?.let { "${it.manufacturer ?: "Android"} ${it.model ?: "Auto"}" }

        val message = when {
            accessory != null -> "دستگاه در حالت AOAP متصل است: $accessoryLabel"
            deviceSummaries.any { it.isAoapMode } -> "حالت اندروید اتو USB (AOAP) آماده دریافت است"
            deviceSummaries.isNotEmpty() -> "${deviceSummaries.size} دستگاه USB شناسایی شد (برای AOAP روی اتصال بزنید)"
            else -> "کابل USB OTG متصل نیست"
        }

        _uiState.value = UsbHostUiState(
            devices = deviceSummaries,
            activeAccessoryLabel = accessoryLabel,
            statusMessage = message,
            isUsbConnected = accessory != null || deviceSummaries.isNotEmpty()
        )
    }

    fun requestPermissionForFirstDevice(): Boolean {
        val manager = usbManager ?: return false
        val accessory = manager.accessoryList?.firstOrNull()
        if (accessory != null && !manager.hasPermission(accessory)) {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            manager.requestPermission(accessory, pi)
            return true
        }
        val device = manager.deviceList.values.firstOrNull() ?: return false
        if (!manager.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            manager.requestPermission(device, pi)
            return true
        }
        return false
    }

    fun openAoapStream(): AoapStreamHandle? {
        val manager = usbManager ?: return null

        // Case 1: UsbAccessory attached
        val accessory: UsbAccessory? = manager.accessoryList?.firstOrNull()
        if (accessory != null) {
            if (!manager.hasPermission(accessory)) {
                requestPermissionForFirstDevice()
                return null
            }
            val pfd: ParcelFileDescriptor = manager.openAccessory(accessory) ?: return null
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            return AoapStreamHandle(
                label = "${accessory.manufacturer ?: "Android"} ${accessory.model ?: "Auto"}",
                inputStream = input,
                outputStream = output,
                closeAction = {
                    runCatching { input.close() }
                    runCatching { output.close() }
                    runCatching { pfd.close() }
                }
            )
        }

        // Case 2: UsbDevice in Host mode
        val device = manager.deviceList.values.firstOrNull() ?: return null
        if (!manager.hasPermission(device)) {
            requestPermissionForFirstDevice()
            return null
        }

        if (!isAoapDevice(device.vendorId, device.productId)) {
            val switched = trySwitchDeviceToAoap(manager, device)
            if (switched) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "دستور AOAP ارسال شد؛ در حال اتصال مجدد USB…"
                )
            }
            return null
        }

        val connection = manager.openDevice(device) ?: return null
        val usbInterface = findBulkInterface(device)
        if (usbInterface == null || !connection.claimInterface(usbInterface, true)) {
            connection.close()
            return null
        }

        var inEndpoint: UsbEndpoint? = null
        var outEndpoint: UsbEndpoint? = null
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEndpoint = ep
                else if (ep.direction == UsbConstants.USB_DIR_OUT) outEndpoint = ep
            }
        }
        if (inEndpoint == null || outEndpoint == null) {
            connection.releaseInterface(usbInterface)
            connection.close()
            return null
        }

        val bulkIn = UsbBulkInputStream(connection, inEndpoint)
        val bulkOut = UsbBulkOutputStream(connection, outEndpoint)
        return AoapStreamHandle(
            label = device.productName ?: "Android Auto USB",
            inputStream = bulkIn,
            outputStream = bulkOut,
            closeAction = {
                runCatching { connection.releaseInterface(usbInterface) }
                runCatching { connection.close() }
            }
        )
    }

    private fun trySwitchDeviceToAoap(manager: UsbManager, device: UsbDevice): Boolean {
        val conn = manager.openDevice(device) ?: return false
        return try {
            val versionBuf = ByteArray(2)
            val protoResult = conn.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR,
                AOAP_GET_PROTOCOL,
                0,
                0,
                versionBuf,
                versionBuf.size,
                1000
            )
            if (protoResult < 2) return false

            sendAoapString(conn, 0, "Android")
            sendAoapString(conn, 1, "Android Auto")
            sendAoapString(conn, 2, "CarPlay HUR Receiver")
            sendAoapString(conn, 3, "2.0")
            sendAoapString(conn, 4, "https://developer.android.com/auto")
            sendAoapString(conn, 5, "CARPLAY-HUR-01")

            val startResult = conn.controlTransfer(
                UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
                AOAP_START_ACCESSORY,
                0,
                0,
                null,
                0,
                1000
            )
            startResult >= 0
        } finally {
            conn.close()
        }
    }

    private fun sendAoapString(conn: UsbDeviceConnection, index: Int, value: String) {
        val bytes = (value + "\u0000").toByteArray(Charsets.UTF_8)
        conn.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
            AOAP_SEND_STRING,
            0,
            index,
            bytes,
            bytes.size,
            1000
        )
    }

    private fun findBulkInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var hasIn = false
            var hasOut = false
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true
                    if (ep.direction == UsbConstants.USB_DIR_OUT) hasOut = true
                }
            }
            if (hasIn && hasOut) return iface
        }
        return null
    }

    private fun isAoapDevice(vendorId: Int, productId: Int): Boolean {
        return vendorId == GOOGLE_VENDOR_ID && productId in 0x2D00..0x2D05
    }

    class AoapStreamHandle(
        val label: String,
        val inputStream: InputStream,
        val outputStream: OutputStream,
        private val closeAction: () -> Unit
    ) {
        fun close() = closeAction()
    }

    private class UsbBulkInputStream(
        private val conn: UsbDeviceConnection,
        private val endpoint: UsbEndpoint
    ) : InputStream() {
        private val singleByte = ByteArray(1)
        override fun read(): Int {
            val count = read(singleByte, 0, 1)
            return if (count <= 0) -1 else singleByte[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val chunk = ByteArray(len)
            val transferred = conn.bulkTransfer(endpoint, chunk, len, 2000)
            if (transferred > 0) {
                System.arraycopy(chunk, 0, b, off, transferred)
            }
            return transferred
        }
    }

    private class UsbBulkOutputStream(
        private val conn: UsbDeviceConnection,
        private val endpoint: UsbEndpoint
    ) : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            val slice = if (off == 0 && len == b.size) b else b.copyOfRange(off, off + len)
            conn.bulkTransfer(endpoint, slice, slice.size, 2000)
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.headunit.USB_PERMISSION"
        private const val GOOGLE_VENDOR_ID = 0x18D1
        private const val AOAP_GET_PROTOCOL = 51
        private const val AOAP_SEND_STRING = 52
        private const val AOAP_START_ACCESSORY = 53
    }
}
