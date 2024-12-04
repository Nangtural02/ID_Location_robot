package com.example.id_location_admin

import androidx.compose.ui.geometry.Offset
import com.google.gson.annotations.SerializedName
import kotlin.math.pow
import kotlin.math.sqrt

data class Point(
    @SerializedName("x") var x : Float = 0f,
    @SerializedName("y") var y : Float = 0f,
    var z : Float = 0f
)


fun Point.add(other : Point){
    this.x += other.x
    this.y += other.y
    this.z += other.z
}
fun Point.sub(other : Point){
    this.x -= other.x
    this.y -= other.y
    this.z -= other.z
}
fun Point.mul(realNum:Float){
    this.x *= realNum
    this.y *= realNum
    this.z *= realNum
}
fun Point.div(realNum:Float){
    this.x /= realNum
    this.y /= realNum
    this.z /= realNum
}

fun Point.toOffSet(): Offset {
    return Offset(100*this.x,100*this.y)
}

fun Point.getDistance(anotherPoint: Point): Float{
    return sqrt((this.x-anotherPoint.x).pow(2)+(this.y-anotherPoint.y).pow(2)+(this.z-anotherPoint.z).pow(2))
}
fun Point.getMiddlePoint(anotherPoint: Point): Point{
    return Point((this.x+anotherPoint.x)/2, (this.y+anotherPoint.y)/2)
}