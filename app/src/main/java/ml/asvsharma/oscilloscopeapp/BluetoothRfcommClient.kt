package ml.asvsharma.oscilloscopeapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
//import com.sun.xml.internal.ws.streaming.XMLStreamWriterUtil.getOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


@ExperimentalStdlibApi
class BluetoothRfcommClient(context: Context?, handler: Handler) {
    // Member fields
    private val mAdapter: BluetoothAdapter
    private val mHandler: Handler
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mState: Int

    companion object {
        // Unique UUID for this application
        private val MY_UUID: UUID =  //UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        //public static final int STATE_LISTEN = 1;     // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3 // now connected to a remote device
    }



    /**
     * Constructor. Prepares a new BluetoothChat session.
     * - context - The UI Activity Context
     * - handler - A Handler to send messages back to the UI Activity
     */

    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        mState = STATE_NONE
        mHandler = handler
    }


    /**
     * Return the current connection state.  */// Give the new state to the Handler so the UI Activity can update
    /**
     * Set the current state o
     */

    @set:Synchronized
    var state: Int
        get() = mState
        private set(state) {
            mState = state
            // Give the new state to the Handler so the UI Activity can update
            mHandler.obtainMessage(OscilloscopeActivity.MESSAGE_STATE_CHANGE, mState, -1)
                .sendToTarget()
        }

    /**
     * Start the Rfcomm client service.
     */
    @Synchronized
    fun start() { // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        // Cancel any thread curre.ntly running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        state = STATE_NONE
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * - device - The BluetoothDevice to connect
     */
    @Synchronized
    fun connect(device: BluetoothDevice) { // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
        state = STATE_CONNECTING
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * - socket - The BluetoothSocket on which the connection was made
     * - device - The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(
        socket: BluetoothSocket?,
        device: BluetoothDevice
    ) { // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket)
        mConnectedThread!!.start()
        // Send the name of the connected device back to the UI Activity
        val msg: Message = mHandler.obtainMessage(OscilloscopeActivity.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(OscilloscopeActivity.DEVICE_NAME, device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)
        state = STATE_CONNECTED
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        state = STATE_NONE
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * - out - The bytes to write - ConnectedThread#write(byte[])
     */
    fun write(out: ByteArray?) { // Create temporary object
        var r: ConnectedThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) return
            r = mConnectedThread
        }
        // Perform the write unsynchronized
        r!!.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        state = STATE_NONE
        // Send a failure message back to the Activity
        val msg: Message = mHandler.obtainMessage(OscilloscopeActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(OscilloscopeActivity.TOAST, "Unable to connect device")
        msg.data = bundle
        mHandler.sendMessage(msg)
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        state = STATE_NONE
        // Send a failure message back to the Activity
        val msg: Message = mHandler.obtainMessage(OscilloscopeActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(OscilloscopeActivity.TOAST, "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
                tmp =
                    mmDevice.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) { //
            }
            mmSocket = tmp
        }

        override fun run() {
            name = "ConnectThread"
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()
            // Make a connection to the BluetoothSocket
            try { // This is a blocking call and will only return on a  successful connection or an exception
                mmSocket!!.connect()
            } catch (e: IOException) {
                connectionFailed()
                // Close the socket
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) { //
                }
                // Start the service over to restart listening mode
                this@BluetoothRfcommClient.start()
                return
            }
            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothRfcommClient) { mConnectThread = null }
            // Start the connected thread
            Log.d("CHECK","Connected")
            connected(mmSocket, mmDevice)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) { //
            }
        }

    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket?) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                Log.e("",e.toString())
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int = 0
            // Keep listening to the InputStream while connected
            while (true) {
                try { // Read from the InputStream
                    if (mmInStream != null) {
                        bytes = mmInStream.read(buffer)
                    }
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(OscilloscopeActivity.MESSAGE_READ, bytes, -1, buffer)
                        .sendToTarget()
                } catch (e: IOException) { //
                    Log.e("",e.toString())
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Write to the connected OutStream.
         */
        fun write(buffer: ByteArray?) {
            try {
                if (mmOutStream != null) {
                    mmOutStream.write(buffer)
                }
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(OscilloscopeActivity.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException) { //
                Log.e("", e.toString())

            }
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) { //
                Log.e("",e.toString())

            }
        }


    }



}
