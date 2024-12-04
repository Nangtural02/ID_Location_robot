package com.example.id_location_robot

import com.example.id_location_admin.Point


data class Anchor(
    var id : Int = 0,
    var coordinateX: Float = 0f,
    var coordinateY: Float = 0f,
    var name : String = ""
)
fun Anchor.getPoint(): Point {
    return Point(this.coordinateX,this.coordinateY)
}