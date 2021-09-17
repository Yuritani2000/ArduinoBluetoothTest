package com.yuritaniapps.otokaketestkotlin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Handlerとやり取りする際のMessageの種別はここに定数として記述する．
 */
private const val MESSAGE_RECEIVE = 0
private const val STATUS_CHANGE = 1

class BluetoothFragment(private val defaultDeviceName: String) : Fragment() {

    private var connectThread: ConnectThread? = null

    companion object {
        /**
         * ReadDataThreadから受け取ったセンサ入力値を，Message形式でUIスレッドに取り出す．
         * この際，取り出した値を処理するためのラムダ式をMainActivityから受け取り，入力値をそのラムダ式に渡す．
         */
        private class MessageHandler(
            val handleMessageReceive: (Int) -> Unit,
            val handleStatusChange: (String) -> Unit
        ): Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MESSAGE_RECEIVE -> {
                        handleMessageReceive(msg.arg2)
                    }
                    STATUS_CHANGE -> {
                        handleStatusChange(msg.obj.toString())
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_bluetooth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ConnectThreadでの例外を処理する． */
        val connectThreadExceptionHandler = Thread.UncaughtExceptionHandler { _, throwable ->
            Log.d("debug", throwable.toString() )
            val statusTextView = view.findViewById<TextView>(R.id.statusTextView)
            statusTextView.text = throwable.toString()
        }

        val searchDevice =  fun (deviceName: String): BluetoothDevice?{
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
            pairedDevices?.forEach { device ->
                if(device.name == deviceName){
                    Log.d("debug", "found target device: ${device.name}")
                    val statusTextView = view.findViewById<TextView>(R.id.statusTextView)
                    statusTextView.text = "device ${device.name} has been found"
                    return device
                }
            }
            return null
        }

        /* Handlerに渡った圧力を用いてTextViewを更新するクラス．Handlerに呼んでもらう */
        val updatePressureTextView = fun (value: Int){
            val pressureTextView = view.findViewById<TextView>(R.id.pressureTextView1)
            pressureTextView.text = value.toString()
        }

        val updateStatusTextView = {message: String ->
            val statusTextView = view.findViewById<TextView>(R.id.statusTextView)
            statusTextView.text = message
        }

        val makeConnection = fun(device: BluetoothDevice){
            connectThread?.closeBluetoothSocket()
            val messageHandler = MessageHandler(updatePressureTextView, updateStatusTextView)
            connectThread = ConnectThread(device, messageHandler)
            connectThread?.uncaughtExceptionHandler = connectThreadExceptionHandler
            connectThread?.start()
        }

        val searchDeviceEditText = view.findViewById<EditText>(R.id.searchDeviceEditText)
        searchDeviceEditText.text = SpannableStringBuilder(defaultDeviceName)
        var device = searchDevice(searchDeviceEditText.text.toString())

        val searchDeviceButton = view.findViewById<Button>(R.id.searchDeviceButton)
        searchDeviceButton.setOnClickListener {
            device = searchDevice(searchDeviceEditText.text.toString())
        }

        val disconnectButton = view.findViewById<Button>(R.id.disconnectButton)
        disconnectButton.setOnClickListener {
            try {
                connectThread?.closeBluetoothSocket()
            } catch (e: IOException) {
                Log.e("IOException", e.toString())
                val statusTextView = view.findViewById<TextView>(R.id.statusTextView)
                statusTextView.text = "接続の切断に失敗"
            }
        }

        /* ボタンを押すと，現在ペアリングしている端末から足裏デバイスを探し出し，接続を試みる． */
        val connectButton = view.findViewById<Button>(R.id.connectButton)
        connectButton.setOnClickListener {
            device?.let {
                makeConnection(it)
            }
        }
    }

    override fun onPause(){
        super.onPause()
        connectThread?.closeBluetoothSocket()
    }
}

/**
 * Bluetoothデバイスへ接続を行う処理が書かれたスレッド．
 * スレッドを分ける理由は，この処理はスレッドをブロックする処理であり，
 * AndroidではメインのUIスレッドをブロックする処理の実行が禁止されているためである．
 */
private class ConnectThread(
    private val device: BluetoothDevice,
    private val handler: Handler
): Thread(){

    /* onCreate内で取得したデバイスに対し，接続を試みる．成功するとbluetoothSocketのオブジェクトが返ってくる */
    private val bluetoothSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
    }

    /**
     * 接続の社団はMainActivityクラスからもアクセスしたいので，publicにする．
     */
    fun closeBluetoothSocket(){
        try {
            bluetoothSocket?.close()
            val connectingStatusMessage = handler.obtainMessage(STATUS_CHANGE, "切断済み")
            connectingStatusMessage.sendToTarget()
        }catch(e: IOException){
            throw e
        }
    }

    override fun run(){
//        val bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        Log.d("debug", "接続を開始します．")
        val connectingStatusMessage = handler.obtainMessage(STATUS_CHANGE, "接続中…")
        connectingStatusMessage.sendToTarget()
        // スレッドから投げられた RuntimeExceptionを処理するには，UncaughtExceptionHandlerを実装する必要がある
        val readDataExceptionHandler = UncaughtExceptionHandler { thread, throwable ->
            Log.d("debug", "$thread での例外を検出しました")
            try {
                closeBluetoothSocket()
                Log.d("debug", "クライアントのソケットを閉じました．")
                throw throwable
            }catch(e: IOException) {
                Log.d("debug", e.toString())
                throw java.lang.RuntimeException("クライアント側のソケットを閉じることができませんでした．")
            }
        }

        if(bluetoothSocket == null){
            throw java.lang.RuntimeException("デバイスに接続することができませんでした．")
        }
        bluetoothSocket?.let { socket ->
            socket.connect()

            // ここでデータ読み取り専用のスレッドを起動する．
            // 呼出し先のスレッドでは，接続が切れると例外が発生するようにしてある．それを受け取ったら，socketをcloseする．
            val readDataThread = ReadDataThread(socket, handler)
            readDataThread.uncaughtExceptionHandler = readDataExceptionHandler
            readDataThread.start()
        }
    }
}

/**
 * 実際にメッセージをBluetoothデバイスから受け取る処理を行うスレッド
 */
private class ReadDataThread(
    bluetoothSocket: BluetoothSocket,
    private val handler: Handler
): Thread() {
    private val inputStream: InputStream = bluetoothSocket.inputStream
    private var byteArray: ByteArray = ByteArray(1024)

    override fun run() {
        Log.d("debug", "通信を開始します")
        val communicatingStatusMessage = handler.obtainMessage(STATUS_CHANGE, "接続済み")
        communicatingStatusMessage.sendToTarget()
        var numBytes: Int

        // IOExceptionを検知するまでループを続け，Bluetoothデバイスと通信を行う．
        while (true){
            numBytes = try {
                inputStream.read(byteArray, 0, 7)
            }catch(e: IOException) {
                Log.d("debug", "Input stream was disconnected", e)
                throw RuntimeException("デバイスとの接続が切断されました．")
            }
            for(i in 0..6){
                Log.d("debug", "byteArray[$i]: ${byteArray[i].toInt() and 0xFF}")
            }

            if(byteArray[0].toInt().toChar() == 's' && byteArray[3].toInt().toChar() == 'm' && byteArray[6].toInt().toChar() == 'e'){
                val sensorValue: Int = (byteArray[1].toInt() and 0xFF)*256 + (byteArray[2].toInt() and 0xFF)
                Log.d("debug", "sensor1Value: $sensorValue")
                val readMsg = handler.obtainMessage(MESSAGE_RECEIVE, numBytes, sensorValue)
                readMsg.sendToTarget()
                byteArray = ByteArray(1024)
            }else{
                Log.e("bluetoothDataFormatError", "受け取ったデータ形式が正しくありません．")
            }
        }
    }
}