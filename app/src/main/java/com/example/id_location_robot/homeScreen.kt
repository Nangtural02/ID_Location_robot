package com.example.id_location_robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.id_location_admin.Domyon
import com.example.id_location_admin.Point
import kotlinx.coroutines.coroutineScope

@Composable
fun HomeScreen(serialViewModel: SerialViewModel) {
    var buttonLabel by remember { mutableStateOf("Connect") }
    val webConnectionState = serialViewModel.webSocketConnectionState.collectAsState().value
    val serialIsConnected = serialViewModel.serialIsConnected.value
    val context = LocalContext.current
    val nowrangingdata = serialViewModel.nowRangingData.value
    Domyon(
        anchorList = listOf(Point(0f,0f),Point(6f,6f)),
        pointsList = listOf(serialViewModel.nowRangingData.value.coordinates),
        backgroundImageResId = R.drawable.yulgok_background
    )

    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Status: $webConnectionState")
        if (!serialViewModel.webSocketIsConnected.value) {
            Button(onClick = {
                serialViewModel.connectWebSocket()
                buttonLabel = "Disconnect"
            }
            ) {
                Text(buttonLabel)
            }
        }
        if (!serialIsConnected) {
            Button(onClick = {
                serialViewModel.connectSerialDevice(context)
            }) {
                Text("Connect to Serial")
            }
        }
        Text(text = nowrangingdata.toString())



        /*
        Button(onClick = {
            if (!serialViewModel.webSocketIsConnected.value) {
                serialViewModel.connectWebSocket()
                buttonLabel = "Disconnect"

            } else {
                serialViewModel.disconnectWebSocket()
                buttonLabel = "Connect"
            }
        }) {
            Text(buttonLabel)
        }

        if (serialIsConnected) {
            Text(text = serialViewModel.nowRangingData.value.toString())
        } else {
            Button(onClick = {
                serialViewModel.connectSerialDevice(context)
            }) {
                Text("Connect to Serial")
            }
        }

         */
    }


}