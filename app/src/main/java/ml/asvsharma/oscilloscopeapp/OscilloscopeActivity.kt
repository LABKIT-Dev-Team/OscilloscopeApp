package ml.asvsharma.oscilloscopeapp


//import sun.text.normalizer.UTF16.append



//import androidx.test.espresso.matcher.ViewMatchers.isChecked

//import androidx.test.espresso.matcher.ViewMatchers.isChecked

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_oscilloscope.*
import kotlin.experimental.and
import ml.asvsharma.oscilloscopeapp.BluetoothDevicesActivity.Companion.device
import ml.asvsharma.oscilloscopeapp.BluetoothDevicesActivity.Companion.m_bluetoothSocket
import ml.asvsharma.oscilloscopeapp.BluetoothDevicesActivity.Companion.m_isConnected

@ExperimentalStdlibApi
@Suppress("DEPRECATION", "PLUGIN_WARNING")
class OscilloscopeActivity : AppCompatActivity() {
    var mWaveform: ml.asvsharma.oscilloscopeapp.WaveformView? = null
    companion object {

        // Local Bluetooth adapter
        var mBluetoothAdapter: BluetoothAdapter? = null
        // Member object for the RFCOMM services
        private var mRfcommClient : BluetoothRfcommClient? = null

        lateinit var progress_bar:ProgressBar
        // Message types sent from the BluetoothRfcommClient Handler
        val MESSAGE_STATE_CHANGE = 1
        val MESSAGE_READ = 2
        val MESSAGE_WRITE = 3
        val MESSAGE_DEVICE_NAME = 4
        val MESSAGE_TOAST = 5

        // var for vibrator
        lateinit var vibrator:Vibrator
        // Key names received from the BluetoothRfcommClient Handler
        val DEVICE_NAME = "device_name"
        val TOAST = "toast"

        // bt-uart constants
        private val MAX_SAMPLES = 640
        private val MAX_LEVEL = 240
        private val DATA_START = MAX_LEVEL + 1
        private val DATA_END = MAX_LEVEL + 2

        private val REQ_DATA: Byte = 0x00
        private val ADJ_HORIZONTAL: Byte = 0x01
        private val ADJ_VERTICAL: Byte = 0x02
        private val ADJ_POSITION: Byte = 0x03

        private val CHANNEL1: Byte = 0x01
        private val CHANNEL2: Byte = 0x02

        // Run/Pause status
        private var bReady = false
        //osc1 and osc2 status
        var ch_1isOn = false
        var ch_2isOn = false
        // receive data
        private val ch1_data = IntArray(MAX_SAMPLES / 2)
        private val ch2_data = IntArray(MAX_SAMPLES / 2)
        var dataIndex = 0
        var dataIndex1: Int = 0
        var dataIndex2: Int = 0
        lateinit var display_timebase : String
        var amp1 : String = "not set"
        var amp2 : String = "not set"
        var bDataAvailable = false
        var readampscale = false
        var readtimebase = true
        // Intent request codes
        private const val REQUEST_CONNECT_DEVICE = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    // Name of the connected device
    private var mConnectedDeviceName: String? = null
    private var timebase = arrayOf(
        "5us",
        "10us",
        "20us",
        "50us",
        "100us",
        "200us",
        "500us",
        "1ms",
        "2ms",
        "5ms",
        "10ms",
        "20ms",
        "50ms"
    )
    private var ampscale =
        arrayOf("10mv", "20mv", "50mv", "100mv", "200mv", "500mv", "1v", "2v", "GND")
    private var timebase_index: Byte = 5
    private var ch1_index: Byte = 4
    private var ch2_index:Byte = 5
    private var ch1_pos: Byte = 24
    private var ch2_pos:Byte = 17 // 0 to 40

    @ExperimentalStdlibApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_oscilloscope)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()
        progress_bar = findViewById(R.id.progress_bar_connectingdevice)
        //var BTname : String? = intent.getStringExtra("BTname")
        //var BTaddress : String? = intent.getStringExtra("BTaddress")
        //connectToDevice().execute(BTname,BTaddress)

        // if (Build.VERSION.SDK_INT < 16) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        //  }
        vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        this.mWaveform = findViewById(R.id.waveform_area)
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        //this is the onClick listener for the voltage and timebase knob
        time_base_arc.numberOfStates = timebase.size
        time_base_arc.state = timebase.indexOf(timebase[0])
        display_timebase = timebase[time_base_arc.state]
        amp1 = ampscale[time_base_arc.state]
        amp2 = ampscale[time_base_arc.state]
        time_base_arc.setOnStateChanged {
            if(readampscale){
                //Toast.makeText(this,ampscale[time_base_arc.state],Toast.LENGTH_SHORT).show()
                vibrator.vibrate(40)

                if(ch_1isOn and ch_2isOn){
                    amp1 = ampscale[time_base_arc.state]
                    amp2 = ampscale[time_base_arc.state]
                    ch1_index = time_base_arc.state.toByte()
                    ch2_index = time_base_arc.state.toByte()
                    var text = "osc 1 : $amp1 and $display_timebase"
                    osc_1_status.text = text
                    text = "osc 2 : $amp2 and $display_timebase"
                    osc_2_status.text = text
                    sendMessage(String(byteArrayOf(ADJ_VERTICAL, CHANNEL1,ch1_index, CHANNEL2,ch2_index)))
                }else{
                    if (ch_1isOn) {
                        amp1 = ampscale[time_base_arc.state]
                        ch1_index = time_base_arc.state.toByte()
                        val text = "osc 1 : $amp1 and $display_timebase"
                        osc_1_status.text = text
                        sendMessage(String(byteArrayOf(ADJ_VERTICAL, CHANNEL1, ch1_index)))
                    }
                    if(ch_2isOn) {
                        amp2 = ampscale[time_base_arc.state]
                        ch2_index = time_base_arc.state.toByte()
                        val text = "osc 2 : $amp2 and $display_timebase"
                        osc_2_status.text = text
                        sendMessage(String(byteArrayOf(ADJ_VERTICAL, CHANNEL2, ch2_index)))
                    }
                }
                if(!(ch_1isOn) and !(ch_2isOn)){
                    Toast.makeText(this,"Choose any of the channel to change the amplitude scale",Toast.LENGTH_SHORT).show()
                }
            }
            if(readtimebase){
                //Toast.makeText(this,timebase[time_base_arc.state],Toast.LENGTH_SHORT).show()
                if(ch_1isOn or ch_2isOn) {
                    vibrator.vibrate(40)
                    timebase_index = time_base_arc.state.toByte()
                    display_timebase = timebase[time_base_arc.state]
                    val text1 = "osc 1 : $amp1 and $display_timebase"
                    val text2 = "osc 2 : $amp2 and $display_timebase"
                    osc_1_status.text = text1
                    osc_2_status.text = text2
                    sendMessage(String(byteArrayOf(ADJ_HORIZONTAL, timebase_index)))
                }else{
                    Toast.makeText(this,"turn any of the channel to change time base",Toast.LENGTH_SHORT).show()
                }

            }
        }

    }

    override fun onStart() {
        super.onStart()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        // If BT is not on, request that it be enabled.
        if (!mBluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        }else{
            if (mRfcommClient == null){
                setupOscilloscope()
            }
        }
    }
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    @ExperimentalStdlibApi
    private fun sendMessage(message: String) { // Check that we're actually connected before trying anything
        if (mRfcommClient!!.state != BluetoothRfcommClient.STATE_CONNECTED) {
            Toast.makeText(this, "communication with device is lost", Toast.LENGTH_SHORT).show()
            return
        }
        // Check that there's actually something to send
        if (message.length > 0) { // Get the message bytes and tell the BluetoothRfcommClient to write
            val send = message.toByteArray()
            Log.d("MONITER",send.contentToString())
            mRfcommClient!!.write(send)
        }
    }


    private fun setupOscilloscope() {
        //val serverIntent = Intent(this, BluetoothDevicesActivity::class.java)
        //startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE)
        // Initialize the BluetoothRfcommClient to perform bluetooth connections
        mRfcommClient = BluetoothRfcommClient(this, mHandler)
    }
    private fun toScreenPos(position: Byte): Int { //return ( (int)MAX_LEVEL - (int)position*6 );
        return MAX_LEVEL - position.toInt() * 6 - 7
    }
    // The Handler that gets information back from the BluetoothRfcommClient
    @ExperimentalStdlibApi
    private val mHandler: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    BluetoothRfcommClient.STATE_CONNECTED -> {
                        progress_bar_connectingdevice.visibility = View.INVISIBLE
                        Toast.makeText(this@OscilloscopeActivity,"Connected to "+mConnectedDeviceName.toString(),Toast.LENGTH_SHORT).show()
                    }
                    BluetoothRfcommClient.STATE_CONNECTING -> {
                        progress_bar_connectingdevice.visibility = View.VISIBLE
                        Toast.makeText(this@OscilloscopeActivity,"Connecting to "+mConnectedDeviceName.toString(),Toast.LENGTH_SHORT).show()
                    }
                    BluetoothRfcommClient.STATE_NONE -> {
                        progress_bar_connectingdevice.visibility = View.INVISIBLE
                        Toast.makeText(this@OscilloscopeActivity,"disconnected from the device",Toast.LENGTH_SHORT).show()
                    }
                }
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    //Log.d("MoniterInc",readBuf.contentToString())
                    val data_length: Int = msg.arg1
                    var x = 0
                    while (x < data_length) {
                        val raw = UByte(readBuf[x])
                        if (raw > MAX_LEVEL) {
                            if (raw == DATA_START) {
                                bDataAvailable = true
                                dataIndex = 0
                                dataIndex1 = 0
                                dataIndex2 = 0
                            } else if (raw == DATA_END || dataIndex >= MAX_SAMPLES) {
                                bDataAvailable = false
                                dataIndex = 0
                                dataIndex1 = 0
                                dataIndex2 = 0
                                mWaveform!!.set_data(ch1_data, ch2_data)
                                if (bReady) { // send "REQ_DATA" again
                                    this@OscilloscopeActivity.sendMessage(
                                        String(
                                            byteArrayOf(
                                                REQ_DATA
                                            )
                                        )
                                    )
                                }
                                //break;
                            }
                        } else if (bDataAvailable && dataIndex < MAX_SAMPLES) { // valid data
                            Log.d("HERE","Yesiam here")
                            if (dataIndex++ % 2 === 0) {
                                ch1_data[dataIndex1++] = raw // even data
                            }
                            else {
                                    ch2_data[dataIndex2++] = raw // odd data
                                }
                        }
                        x++
                    }
                }
                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    mConnectedDeviceName =
                        msg.data.getString(Settings.Global.DEVICE_NAME)
                    Toast.makeText(
                        applicationContext, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT
                    ).show()
                }
                MESSAGE_TOAST -> Toast.makeText(
                    applicationContext, msg.data.getString(TOAST),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // signed to unsigned
        private fun UByte(b: Byte): Int {
            return if (b < 0) // if negative
                ((b and 0x7F) + 128) else b.toInt()
        }
    }

        fun onClick(view:View){
        when(view.id){
            R.id.up1->{
                vibrator.vibrate(40)
                if ( ch_1isOn && ch1_pos < 38) {
                    ch1_pos = (ch1_pos + 1).toByte()
                    //uncommenting below line will do padding to the particular element inside its defined area
                    //osc_1_status.setPadding(0, toScreenPos(ch1_pos), 0, 0)
                    sendMessage(String(byteArrayOf(ADJ_POSITION, CHANNEL1, ch1_pos)))
                }
            }
            R.id.up2->{
                vibrator.vibrate(40)
                if (ch_2isOn && ch2_pos < 38) {
                    ch2_pos = (ch2_pos + 1).toByte()
                    //uncommenting below line will do padding to the particular element inside its defined area
                    //osc_2_status.setPadding(0,toScreenPos(ch2_pos),0,0)
                    sendMessage(String(byteArrayOf(ADJ_POSITION, CHANNEL2, ch2_pos)))
                }

            }
            R.id.down1->{
                vibrator.vibrate(40)
                if (ch_1isOn && ch1_pos > 4) {
                    ch1_pos.dec()
                    //uncommenting below line will do padding to the particular element inside its defined area
                    //osc_1_status.setPadding(0, toScreenPos(ch1_pos), 0, 0)
                    sendMessage(String(byteArrayOf(ADJ_POSITION, CHANNEL1, ch1_pos)))
                }
            }
            R.id.down2->{
                vibrator.vibrate(40)
                if (ch_2isOn && ch2_pos > 4) {
                    ch2_pos.dec()
                    //uncommenting below line will do padding to the particular element inside its defined area
                    //osc_2_status.setPadding(0, toScreenPos(ch2_pos), 0, 0)
                    sendMessage(String(byteArrayOf(ADJ_POSITION, CHANNEL2, ch2_pos)))
                }
            }
            R.id.osc_switch1->{
                vibrator.vibrate(40)
                ch_1isOn = osc_switch1.isChecked
            }
            R.id.osc_switch2->{
                vibrator.vibrate(40)
                ch_2isOn = osc_switch2.isChecked
            }
            R.id.run_stop_switch->{
                vibrator.vibrate(40)
                if(run_stop_switch.isChecked){
                    this.sendMessage(
                        String(
                            byteArrayOf(
                                ADJ_HORIZONTAL, timebase_index,
                                ADJ_VERTICAL, CHANNEL1, ch1_index,
                                ADJ_VERTICAL, CHANNEL2, ch2_index,
                                ADJ_POSITION, CHANNEL1, ch1_pos,
                                ADJ_POSITION, CHANNEL2, ch2_pos,
                                REQ_DATA
                            )
                        )
                    )
                    bReady = true

                }else{
                    bReady = false
                }
            }
            R.id.time_base_switch->{
                if(time_base_switch.isChecked){
                    vibrator.vibrate(40)
                    readtimebase = false
                    readampscale = true
                    time_base_arc.numberOfStates = ampscale.size
                }else{
                    vibrator.vibrate(40)
                    readtimebase = true
                    readampscale = false
                    time_base_arc.numberOfStates = timebase.size
                }
            }
        }
    }

    override fun onBackPressed() {
        //if you uncomment this the alertDialogBuilder will not work
        //super.onBackPressed()
        var mAlertDialogBuilder = AlertDialog.Builder(this@OscilloscopeActivity)
        mAlertDialogBuilder.setTitle("Do you wish to disconnect the device?")
        mAlertDialogBuilder.setMessage("You are terminating the communication of the app with the device. hope you had a great learning.")
        mAlertDialogBuilder.setPositiveButton("yes"){
            _,_->
            m_bluetoothSocket!!.close()
            finish()
        }
        mAlertDialogBuilder.setNegativeButton("no"){
            _,_->
        }
        mAlertDialogBuilder.create().show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the Bluetooth RFCOMM services
        if (mRfcommClient != null) mRfcommClient!!.stop()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mRfcommClient != null) { // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mRfcommClient!!.state == BluetoothRfcommClient.STATE_NONE) { // Start the Bluetooth  RFCOMM services
                mRfcommClient!!.connected(m_bluetoothSocket, device)
            }
        }
    }
    /*
    private class connectToDevice() : AsyncTask<String,Void,Void>(){
        override fun onPreExecute() {
            super.onPreExecute()
            progress_bar.visibility = View.VISIBLE
        }
        override fun doInBackground(vararg params: String?): Void? {
            var name = params[0]
            val address = params[1]
            val device: BluetoothDevice? = mBluetoothAdapter?.getRemoteDevice(address)
            // Attempt to connect to the device
            if (device != null) {
                mRfcommClient?.connect(device)
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            progress_bar.visibility = View.GONE
        }

    }
    */
}
