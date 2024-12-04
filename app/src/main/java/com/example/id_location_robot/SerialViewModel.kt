package com.example.id_location_robot

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.id_location_admin.Point
import com.example.id_location_admin.calcBy3Side
import com.example.id_location_admin.calcByDoubleAnchor
import com.example.id_location_admin.calcMiddleBy4Side
import com.example.id_location_admin.refinePositionByGaussNewton
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.hoho.android.usbserial.BuildConfig
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.Date

private val serverURL =
    "ws://202.30.29.212:5000/androidA"

class SerialViewModel(application: Application) : AndroidViewModel(application), SerialInputOutputManager.Listener {
    /** data to Update */
    var nowRangingData: MutableState<RangingData> = mutableStateOf(RangingData())
    var nowBlockString : MutableState<String> = mutableStateOf("not Connected")
    /* data to update */
    // Serial
    val serialIsConnected:MutableState<Boolean> = mutableStateOf(false)
    // WebSocket 연결 상태
    val webSocketIsConnected:MutableState<Boolean> = mutableStateOf(false) // WebSocket 연결 상태 추적
    private val _connectionState = MutableStateFlow("Disconnected") // 연결 상태 초기화
    val webSocketConnectionState: StateFlow<String> = _connectionState // 외부에서 읽기 전용으로 제공

    //for Moving Average Filter
    private val distanceBuffers = mutableMapOf<Int, MutableList<Float>>()
    private val windowSize = 6  // 이동 평균 필터의 윈도우 크기
    // 상한/하한을 설정할 때 사용하는 팩터 (필요 시 조정)
    private val boundFactor = 1.5f
    //
    /** setUp Parameter */
    var baudRate = 115200
    var anchorList = listOf(Anchor(0,0f),Anchor(1,8.455f,0f),Anchor(2,8.455f,4.06f,),Anchor(3,0f,4.06f))
    /* setUp Parameter **/
    //parsing custom data format for DWM3001CDK, which is "{ID(int), BlockNum(int), Distance(float)}"
    // 새 포맷을 위한 데이터 클래스 선언
    data class ParsedData(val id: Int, val blockNum: Int, val distance: Float)

    // ViewModel 내에 버퍼 변수를 선언
    private val dataBuffer = mutableMapOf<Int, MutableList<ParsedData>>()
    private var currentBlockNum: Int? = null

    // blockHandler 함수
    private fun blockHandler(blockString: String) {
        nowBlockString.value = blockString
        val parsedData = parseData(blockString)

        if (parsedData != null) {
            val blockNum = parsedData.blockNum
            val currentBlockData = dataBuffer.getOrPut(blockNum) { mutableListOf() }

            // 블록 데이터 모으기
            currentBlockData.add(parsedData)

            // 현재 블록의 데이터에서 수집된 ID 목록
            val idsCollected = currentBlockData.map { it.id }.distinct()

            // ID 0~3이 모두 수집되었는지 확인
            if (idsCollected.containsAll(listOf(0, 1, 2, 3))) {
                // 모든 ID의 데이터가 수집되었으면 처리
                processCurrentBlockData(blockNum)

                // 처리 후 해당 블록 데이터 제거
                dataBuffer.remove(blockNum)
            }
        } else {
            Log.e("SerialViewModel", "Invalid data format")
        }
    }

    // 현재 블록 데이터를 처리하는 함수
    private fun processCurrentBlockData(blockNum: Int) {
        val currentBlockData = dataBuffer[blockNum]
        if (currentBlockData != null && currentBlockData.size >= 4) {
            try {
                val distanceList: List<RangingDistance> = currentBlockData.map { result ->
                    val id = result.id
                    val rawDistance = result.distance.toFloat()

                    // 이동 평균 필터 적용 및 상한/하한 설정
                    val filteredDistance = if (id != -1) {
                        val buffer = distanceBuffers.getOrPut(id) { mutableListOf() }

                        // 새로운 거리값 추가
                        buffer.add(rawDistance)

                        // 윈도우 크기 초과 시 가장 오래된 값 제거
                        if (buffer.size > windowSize) {
                            buffer.removeAt(0)
                        }

                        // 가중 이동 평균 계산
                        val weights = (1..buffer.size).toList()
                        val weightedSum = buffer.mapIndexed { index, value ->
                            value * weights[index]
                        }.sum()
                        val totalWeight = weights.sum()

                        val weightedAverage = weightedSum / totalWeight

                        // 상한/하한 설정
                        val upperBound = weightedAverage * (1 + boundFactor)
                        val lowerBound = weightedAverage * (1 - boundFactor)

                        // 현재 거리값 조정
                        when {
                            rawDistance > upperBound -> upperBound
                            rawDistance < lowerBound -> lowerBound
                            else -> rawDistance
                        }
                    } else {
                        rawDistance
                    }
                    RangingDistance(
                        id = id,
                        distance = filteredDistance
                    )
                }

                val blockData = RangingData(
                    blockNum = blockNum,
                    distanceList = distanceList,
                    time = SimpleDateFormat("dd HH:mm:ss").format(Date())
                )

                val validInput = distanceList.filter { it.id != -1 }
                Log.e("asdf", anchorList.toString())
                Log.e("asdf", "$validInput")
                val tempCoord = nowRangingData.value.coordinates
                blockData.coordinates =
                    when (validInput.size) {
                        4 -> calcMiddleBy4Side(
                            validInput.map { it.distance },
                            validInput.map { validDistance ->
                                anchorList.find { it.id == validDistance.id }?.getPoint() ?: Point()
                            }
                        )
                        3 -> calcBy3Side(
                            validInput.map { it.distance },
                            validInput.map { validDistance ->
                                anchorList.find { it.id == validDistance.id }?.getPoint() ?: Point()
                            }
                        )
                        2 -> calcByDoubleAnchor(
                            validInput.map { it.distance },
                            validInput.map { validDistance ->
                                anchorList.find { it.id == validDistance.id }?.getPoint() ?: Point()
                            },
                            anchorList.map { it.getPoint() }
                        )
                        else -> Point()
                    }

                //blockData.coordinates.z = 2.37f - blockData.coordinates.z
                val epsilon = 0.001f // 허용 오차
                if (kotlin.math.abs(blockData.coordinates.x) < epsilon && kotlin.math.abs(blockData.coordinates.y) < epsilon) {
                    Log.e("Positioning Error", blockData.coordinates.toString())
                    blockData.coordinates = tempCoord
                }


                nowRangingData.value = blockData

                // 처리된 데이터를 WebSocket으로 전송
                val validDistanceList: List<Float> = blockData.distanceList.map { it.distance }
                sendDataViaWebSocket(validDistanceList)

                // 블록 데이터는 이미 blockHandler에서 제거되었으므로 추가로 초기화할 필요 없음

            } catch (e: Exception) {
                Log.e("SerialViewModel", "Error processing block data: ${e.message}")
            }
        } else {
            Log.e("SerialViewModel", "Insufficient data for block: $blockNum")
        }
    }


    // 데이터 파싱 함수
    fun parseData(input: String): ParsedData? {
        // 공백을 제거하고, 데이터 양 끝의 '{', '}'를 제거
        val cleanedInput = input.trim().removeSurrounding("{", "}").trim()
        val parts = cleanedInput.split(",").map { it.trim() } // 각 부분의 공백도 제거

        return if (parts.size == 3) {
            try {
                val id = parts[0].toInt()
                val blockNum = parts[1].toInt()
                val distance = parts[2].toFloat()
                ParsedData(id, blockNum, distance)
            } catch (e: NumberFormatException) {
                Log.e("SerialViewModel", "Number format error: ${e.message}")
                null
            }
        } else {
            Log.e("SerialViewModel", "Data format error: expected 3 parts but got ${parts.size}")
            null
        }
    }






    /*
    // JSON Parsing and handle for DWM3001CDK CLI build
    private fun blockHandler(blockString: String) {
        try {
            val data = Gson().fromJson(blockString, Data::class.java)
            val distanceList: List<Float?> = data.results.map {
                if (it.status == "Err") null else it.dCm.toFloat() / 100
            }

            // 예외를 던지기 전에 null이 있는지 확인
            if (distanceList.any { it == null }) {
                throw IllegalArgumentException("Signal error")
            }
            // null이 없음을 확신한 후 안전하게 캐스팅
            val validDistanceList: List<Float> = distanceList.filterNotNull()
            nowRangingData.value = validDistanceList

            Log.d("asdf", distanceList.toString())

            // 처리된 데이터를 WebSocket으로 전송
            sendDataViaWebSocket(validDistanceList)

        } catch (e: JsonSyntaxException) {
            Log.e("SerialViewModel", "signal error")
        } catch (e: NullPointerException) {
            Log.e("SerialViewModel", "nullPointer -")
        } catch (e: IllegalArgumentException) {
            Log.e("SerialViewModel", "Serial Signal Error")
        }
    }
     */

    // WebSocket 관련 변수
    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()
    private val gson = Gson()

    // WebSocket 연결 함수
    fun connectWebSocket() {
        val request = Request.Builder()
            .url(serverURL)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connection opened")
                CoroutineScope(Dispatchers.Main).launch {
                    webSocketIsConnected.value = true
                    _connectionState.value = "Connected" //연결 성공
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Message received: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closing: $code / $reason")
                _connectionState.value = "Disconnecting" // 연결 해제 중
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closed: $code / $reason")
                CoroutineScope(Dispatchers.Main).launch {
                    webSocketIsConnected.value = false
                    _connectionState.value = "Disconnected" // 연결 해제 완료
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("WebSocket", "Connection failed: ${t.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    webSocketIsConnected.value = false
                    _connectionState.value = "Failed: ${t.message}" // 연결 실패
                }
            }
        })
    }

    // WebSocket 연결 해제 함수
    fun disconnectWebSocket() {
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "User disconnected")
            Log.d("WebSocket", "Connection closed by user")
            webSocketIsConnected.value = false
            _connectionState.value = "Disconnected" // 수동으로 연결 해제
        }
    }

    // 처리된 데이터를 WebSocket으로 전송하는 함수
    private fun sendDataViaWebSocket(data: List<Float>) {
        if (webSocketIsConnected.value) {
            /*
            val distanceData = mapOf(
                "distance1" to data.getOrNull(0),
                "distance2" to data.getOrNull(1),
                "distance3" to data.getOrNull(2),
                "distance4" to data.getOrNull(3)
            )
            val jsonData = gson.toJson(distanceData)
            */
            val jsonData = gson.toJson(data)
            webSocket.send(jsonData)
            Log.d("WebSocket", "Data sent: $jsonData")
        } else {
            Log.d("WebSocket", "Not connected to server")
        }
    }

    private var connectedUSBItem = MutableStateFlow<USBItem?>(null)
    private enum class USBPermission { UnKnown, Requested, Granted, Denied }
    private var usbPermission: USBPermission = USBPermission.UnKnown
    private val INTENT_ACTION_GRANT_USB: String = BuildConfig.LIBRARY_PACKAGE_NAME + ".GRANT_USB"
    private var usbIOManager: SerialInputOutputManager? = null
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (INTENT_ACTION_GRANT_USB == intent.action) {
                usbPermission = if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    USBPermission.Granted
                } else {
                    USBPermission.Denied
                }
                connectSerialDevice(context)
            }
        }
    }

    fun connectSerialDevice(context: Context) {
        var count = 0
        viewModelScope.launch(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            while (connectedUSBItem.value == null) {
                Log.d("SerialViewModel", "try to Connect")
                for (device in usbManager.deviceList.values) {
                    val driver = CdcAcmSerialDriver(device)
                    if (driver.ports.size == 1) {
                        connectedUSBItem.update {
                            USBItem(device, driver.ports[0], driver)
                        }
                        Log.d("SerialViewModel", "device Connected")
                    }
                }
                kotlinx.coroutines.delay(1000L) //by 1 sec
                count++
                if (count > 5) {
                    disConnectSerialDevice()
                    cancel()
                } //more than 5 sec
            }
            val device: UsbDevice = connectedUSBItem.value!!.device

            Log.d("SerialViewModel", "usb connection try")
            var usbConnection: UsbDeviceConnection? = null
            if (usbPermission == USBPermission.UnKnown && !usbManager.hasPermission(device)) {
                usbPermission = USBPermission.Requested
                val intent: Intent = Intent(INTENT_ACTION_GRANT_USB)
                intent.setPackage(getApplication<Application>().packageName)
                Log.d("SerialViewModel", "request Permission")
                usbManager.requestPermission(
                    device,
                    PendingIntent.getBroadcast(
                        getApplication(),
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                return@launch
            }
            kotlinx.coroutines.delay(1000L)
            try {
                Log.d("SerialViewModel", "Port open try")
                usbConnection = usbManager.openDevice(device)
                connectedUSBItem.value!!.port.open(usbConnection)
            } catch (e: IllegalArgumentException) {
                disConnectSerialDevice()
                return@launch
            } catch (e: IOException) {
                if (e.message != "Already Open") throw IOException()
            }
            Log.d("SerialViewModel", "Port open")
            connectedUSBItem.value!!.port.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE)
            usbIOManager = SerialInputOutputManager(connectedUSBItem.value!!.port, this@SerialViewModel)
            usbIOManager!!.start()
            Log.d("SerialViewModel", "dtr On")
            connectedUSBItem.value?.port?.dtr = true
            serialIsConnected.value = true
        }
    }

    private fun disConnectSerialDevice() {
        usbPermission = USBPermission.UnKnown
        usbIOManager?.listener = null
        usbIOManager?.stop()
        if (connectedUSBItem.value == null) return
        if (connectedUSBItem.value!!.port.isOpen()) {
            connectedUSBItem.value?.port?.close()
        }
        connectedUSBItem.update { null }
        serialIsConnected.value = false
    }

    private var _buffer = mutableStateOf("")

    // for JSON
    /*
    override fun onNewData(data: ByteArray?) { // called when get data
        viewModelScope.launch {
            if (data != null) {
                if (data.isNotEmpty()) {
                    val result: String = getLineString(data, data.size)
                    if (_buffer.value.isEmpty()) {
                        _buffer.value += result
                    } else {
                        if (result.contains("{\"B")) { // 메시지를 받다말고 새로운 메시지가 들어옴
                            _buffer.value = result
                        } else if ((_buffer.value + result).contains("}]}")) { // 메시지의 끝
                            _buffer.value += result
                            blockHandler(_buffer.value)
                            _buffer.value = ""
                        } else {
                            _buffer.value += result
                        }
                    }
                }
            }
        }
    }
    */

    // For Custom Data Format ( {~~~~~} )

    override fun onNewData(data: ByteArray?) {
        viewModelScope.launch{
            if(data != null) {
                if (data.isNotEmpty()) {
                    val result : String = getLineString(data, data.size)
                    if (_buffer.value.isEmpty()) {
                        _buffer.value += result
                    }else{
                        result.replace(" ","")
                        if(result.contains("}")){
                            _buffer.value += result
                            Log.d("blockHandle", _buffer.value)
                            blockHandler(_buffer.value)
                            _buffer.value = ""
                        }else{
                            _buffer.value += result
                        }
                    }
                }
            }
        }
    }

    override fun onRunError(e: Exception) {
        viewModelScope.launch() {
            Log.e("SerialViewModel", "Disconnected: ${e.message}")
            disConnectSerialDevice()
        }
    }

    private fun getLineString(array: ByteArray, length: Int): String {
        val result = StringBuilder()
        val line = ByteArray(8)
        var lineIndex = 0
        for (i in 0 until 0 + length) {
            if (lineIndex == line.size) {
                for (j in line.indices) {
                    if (line[j] > ' '.code.toByte() && line[j] < '~'.code.toByte()) {
                        result.append(String(line, j, 1))
                    } else {
                        result.append(" ")
                    }
                }
                lineIndex = 0
            }
            val b = array[i]
            line[lineIndex++] = b
        }
        for (i in 0 until lineIndex) {
            if (line[i] > ' '.code.toByte() && line[i] < '~'.code.toByte()) {
                result.append(String(line, i, 1))
            } else {
                result.append(" ")
            }
        }
        return result.toString()
    }

    init {
        val filter = IntentFilter(INTENT_ACTION_GRANT_USB)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상일 경우, 명시적으로 플래그를 지정
            getApplication<Application>().registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Android 12 미만일 경우, 기존 방식으로 등록
            getApplication<Application>().registerReceiver(usbPermissionReceiver, filter)
        }
        // WebSocket 연결 시작
        connectWebSocket()
    }

    override fun onCleared() {
        super.onCleared()
        disConnectSerialDevice()
        disconnectWebSocket()
        getApplication<Application>().unregisterReceiver(usbPermissionReceiver)
    }
}

data class Result(
    @SerializedName("Addr") val addr: String,
    @SerializedName("Status") val status: String,
    @SerializedName("D_cm") val dCm: Int,
    @SerializedName("LPDoA_deg") val lPDoADeg: Float,
    @SerializedName("LAoA_deg") val lAoADeg: Float,
    @SerializedName("LFoM") val lfom: Int,
    @SerializedName("RAoA_deg") val raDoADeg: Float,
    @SerializedName("CFO_100ppm") val cfo100ppm: Int
)

data class Data(
    @SerializedName("Block") val block: Int,
    @SerializedName("results") val results: List<Result>
)

data class USBItem(
    val device: UsbDevice,
    val port: UsbSerialPort,
    val driver: UsbSerialDriver = CdcAcmSerialDriver(device)
)
data class RangingData(
    val blockNum: Int = 0,
    val distanceList: List<RangingDistance> = emptyList(),
    var coordinates: Point = Point(),
    val time: String = SimpleDateFormat("dd HH:mm:ss").format(Date())
)
fun RangingData.toPoint():Point{
    return this.coordinates
}
data class RangingDistance(
    val id: Int,
    val distance : Float,
    val PDOA : Float? = null,
    val AOA : Float? = null
)

