package ml.asvsharma.oscilloscopeapp

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.*
import android.R.string.cancel





@ExperimentalStdlibApi
class BluetoothDevicesActivity : AppCompatActivity() {
    private lateinit var  bluetoothPairedDevices : Set<BluetoothDevice>
    lateinit var buttonPairedList:Button
    companion object{
        lateinit var device: BluetoothDevice
        lateinit var vibrator: Vibrator
        lateinit var m_progress: ProgressBar
        var m_isConnected:Boolean = false
        var m_bluetoothSocket: BluetoothSocket?=null
        lateinit var m_bluetoothAdapter:BluetoothAdapter
        lateinit var m_address:String
        lateinit var m_name:String
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_devices)
        buttonPairedList = this.findViewById(R.id.check_bluetooth)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        var bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if(bluetoothAdapter == null) {
            Toast.makeText(applicationContext,"Your device does not support bluetooth...",Toast.LENGTH_LONG).show()
        }else{
            if(!bluetoothAdapter?.isEnabled!!){
                val turnOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                val REQUEST_CODE = 1
                val c = startActivityForResult(turnOn,REQUEST_CODE)
            }

        }
        buttonPairedList.setOnClickListener {
            vibrator.vibrate(40)
            if(bluetoothAdapter.isEnabled){
                getPairedDevicesList()
            }else{
                Toast.makeText(applicationContext,"You need to turn on the bluetooth...",Toast.LENGTH_LONG).show()
                if(!bluetoothAdapter?.isEnabled!!){
                    val turnOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    val REQUEST_CODE = 1
                    val c = startActivityForResult(turnOn,REQUEST_CODE)
                }

            }
        }
    }

    override fun onBackPressed() {
        if(m_bluetoothAdapter.isEnabled) {
            m_bluetoothAdapter.disable()
        }
        finish()
    }

    override fun onDestroy() {

        if(m_bluetoothAdapter.isEnabled) {
            m_bluetoothAdapter.disable()
        }
        super.onDestroy()
    }

    private fun getPairedDevicesList(){
        var bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothPairedDevices = bluetoothAdapter.bondedDevices
        var BluetoothDeviceLists = ArrayList<String>()
        if(!bluetoothPairedDevices.isEmpty()){
            for(bluetoothDevices:BluetoothDevice in bluetoothPairedDevices){
                BluetoothDeviceLists.add(bluetoothDevices.name+"\n"+bluetoothDevices.address)
            }
        }else{
            Toast.makeText(this@BluetoothDevicesActivity,"No Paired Devices found.",Toast.LENGTH_SHORT).show()
        }
        var BluetoothDeviceListsArray = BluetoothDeviceLists.toTypedArray()
        //Toast.makeText(applicationContext,"Showing paired devices...",Toast.LENGTH_SHORT).show()
        val alertDialogBuiler = AlertDialog.Builder(this@BluetoothDevicesActivity)
        alertDialogBuiler.setTitle("choose the device from list of paired devices")
        var cancelable = alertDialogBuiler.setCancelable(false)
        alertDialogBuiler.setItems(BluetoothDeviceListsArray) {_,which ->
            when (which) {
                which-> {
                    vibrator.vibrate(40)
                    m_address = BluetoothDeviceLists[which].split('\n')[1]
                    m_name = BluetoothDeviceLists[which].split('\n')[0]
                    ConnectToDevice(this@BluetoothDevicesActivity).execute()
                    //var intent = Intent(this@BluetoothDevicesActivity,OscilloscopeActivity::class.java)
                    //intent.putExtra("BTname", m_name)
                    //intent.putExtra("BTaddress", m_address)
                    //this@BluetoothDevicesActivity.startActivity(intent)
                }
            }
        }
        alertDialogBuiler.setNeutralButton("Cancel"){_,_ ->
            vibrator.vibrate(40)
            //Toast.makeText(this@BluetoothDevicesActivity,"No choice is made",Toast.LENGTH_LONG).show()
        }
        alertDialogBuiler.create().show()
    }

    @Suppress("DEPRECATION")
    public class ConnectToDevice(c:Context): AsyncTask<Void, Void, String>() {
        private var connectSuccess:Boolean = true
        private lateinit var context: Context
        init {
            this.context = c
        }
        val progressDialog = ProgressDialog(context)
        override fun onPreExecute() {
            super.onPreExecute()
            progressDialog.setMessage("connecting to "+ m_name)
            progressDialog.setCanceledOnTouchOutside(true)
            progressDialog.setOnDismissListener {
                this.cancel(true)
                Toast.makeText(context,"Cancelled",Toast.LENGTH_SHORT).show()
            }
            progressDialog.show()
        }
        override fun doInBackground(vararg params: Void?): String? {
            try{
                if(m_bluetoothSocket==null||!m_isConnected) {
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    device = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket?.connect()


                }
            }catch(e: IOException){
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if(!connectSuccess){
                progressDialog.dismiss()
                Toast.makeText(context,"Couldnot establish connection"+ m_name,Toast.LENGTH_SHORT).show()
            }else{
                m_isConnected = true
                progressDialog.dismiss()
                Toast.makeText(context,"Connected to "+ m_name,Toast.LENGTH_SHORT).show()
                if(m_isConnected) {
                    var intent = Intent(context,OscilloscopeActivity::class.java)
                    context.startActivity(intent)
                    m_isConnected = false
                }
            }
            progressDialog.dismiss()
        }
    }
}