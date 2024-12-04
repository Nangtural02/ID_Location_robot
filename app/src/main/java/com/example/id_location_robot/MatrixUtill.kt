package com.example.id_location_robot


fun determinant(matrix: Array<FloatArray>): Float {
    when(matrix.size){
        1 -> return matrix[0][0]
        2 -> return matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]
        3 -> return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1]) -
                matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0]) +
                matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0])
        else -> return -66.66f //todo: 그 이상 크기의 determinant도 계산할수 있게
    }
}

fun adjoint(matrix: Array<FloatArray>): Array<FloatArray> {
    val adj = Array(3) { FloatArray(3) }
    adj[0][0] =  matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1]
    adj[0][1] = -(matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
    adj[0][2] =  matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]

    adj[1][0] = -(matrix[0][1] * matrix[2][2] - matrix[0][2] * matrix[2][1])
    adj[1][1] =  matrix[0][0] * matrix[2][2] - matrix[0][2] * matrix[2][0]
    adj[1][2] = -(matrix[0][0] * matrix[2][1] - matrix[0][1] * matrix[2][0])

    adj[2][0] =  matrix[0][1] * matrix[1][2] - matrix[0][2] * matrix[1][1]
    adj[2][1] = -(matrix[0][0] * matrix[1][2] - matrix[0][2] * matrix[1][0])
    adj[2][2] =  matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]

    // adjoint의 전치
    return arrayOf(
        floatArrayOf(adj[0][0], adj[1][0], adj[2][0]),
        floatArrayOf(adj[0][1], adj[1][1], adj[2][1]),
        floatArrayOf(adj[0][2], adj[1][2], adj[2][2])
    )
}
fun invertMatrix(matrix: Array<FloatArray>): Array<FloatArray>?{
    if(matrix.isEmpty()) return emptyArray()
    val m = matrix.size
    val n = matrix[0].size
    if(m != n) return emptyArray()
    val det = determinant(matrix)
    if(det == 0f) return null
    when(m){
        1 -> return matrix
        2 -> {
            return arrayOf(
                floatArrayOf(matrix[1][1] / det, -matrix[0][1] / det),
                floatArrayOf(-matrix[1][0] / det, matrix[0][0] / det)
            )
        }
        else -> {
            val adjoint = adjoint(matrix)
            val inverse = Array(m) { FloatArray(n) }
            for (i in 0..< m) {
                for (j in 0..< n) {
                    inverse[i][j] = adjoint[i][j] / det
                }
            }
            return inverse
        }
    }
}



/*
fun invertMatrix3x3(matrix: Array<FloatArray>): Array<FloatArray>? {
    val det = determinant(matrix)
    if (det == 0f) {
        return null
    }
    val adjoint = adjoint(matrix)
    val inverse = Array(3) { FloatArray(3) }
    for (i in 0..2) {
        for (j in 0..2) {
            inverse[i][j] = adjoint[i][j] / det
        }
    }
    return inverse
}

fun invertMatrix2x2(matrix: Array<FloatArray>): Array<FloatArray> {
    val determinant = matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]
    if (determinant == 0f) return emptyArray()

    return arrayOf(
        floatArrayOf(matrix[1][1] / determinant, -matrix[0][1] / determinant),
        floatArrayOf(-matrix[1][0] / determinant, matrix[0][0] / determinant)
    )
}*/

fun multiplyMatrixVector(matrix: Array<FloatArray>?, vector: FloatArray): FloatArray {
    val result = FloatArray(vector.size)
    if(matrix == null){

        return result
    }
    for (i in matrix!!.indices) {
        for (j in vector.indices) {
            result[i] += matrix[i][j] * vector[j]
        }
    }
    return result
}
fun multiplyMatrixMatrix(matrix1: Array<FloatArray>?, matrix2: Array<FloatArray>?): Array<FloatArray> {
    if(matrix1 == null || matrix2 == null){
        return emptyArray()
    }

    val result = Array(matrix1.size) {FloatArray(matrix2[0].size)}
    //println("${matrix1!!.size} x ${matrix2!!.size} matrix multiply")


    for (i in matrix1.indices) {
        for (j in matrix2[0].indices) {
            for(k in matrix2.indices){
                result[i][j] += matrix1[i][k] * matrix2[k][j]
            }
        }
    }
    return result

}
fun transitionMatrix(matrix: Array<FloatArray>?): Array<FloatArray>{
    if(matrix == null){
        return emptyArray()
    }
    else{
        val result = Array(matrix[0].size) { FloatArray(matrix.size) }
        for (i in result.indices) {
            for (j in result[i].indices) {
                result[i][j] = matrix[j][i]
            }
        }
        return result
    }
}