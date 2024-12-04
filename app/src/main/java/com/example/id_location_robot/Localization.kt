package com.example.id_location_admin

import android.util.Log
import com.example.id_location_robot.invertMatrix
import com.example.id_location_robot.multiplyMatrixMatrix
import com.example.id_location_robot.multiplyMatrixVector
import com.example.id_location_robot.transitionMatrix
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

fun refinePositionByGaussNewton(
    distances: List<Float>,
    anchorPosition: List<Point>,
    lastPosition: Point = Point()
): Point {
    // 초기 위치 설정
    val initialPosition = Point(
        x = anchorPosition.map { it.x }.average().toFloat(),
        y = anchorPosition.map { it.y }.average().toFloat(),
        z = anchorPosition.map { it.z }.average().toFloat()
    )
    var currentPosition = if (lastPosition.x == 0f && lastPosition.y == 0f && lastPosition.z == 0f) {
        initialPosition
    } else {
        lastPosition
    }

    // 허용 오차와 최대 반복 횟수 설정
    val tolerance = 1e-6f
    val maxIterations = 100
    var iteration = 0

    val epsilon = 1e-8f

    while (iteration < maxIterations) {
        val numAnchors = distances.size
        val jacobian = Array(numAnchors) { FloatArray(3) } // 자코비안 행렬
        val residuals = FloatArray(numAnchors) // 잔차 벡터

        // 잔차와 자코비안 계산
        for (i in distances.indices) {
            val dx = currentPosition.x - anchorPosition[i].x
            val dy = currentPosition.y - anchorPosition[i].y
            val dz = currentPosition.z - anchorPosition[i].z

            val predictedDistance = sqrt(dx * dx + dy * dy + dz * dz) + epsilon

            residuals[i] = distances[i] - predictedDistance

            val distanceInverse = 1 / predictedDistance

            jacobian[i][0] = -dx * distanceInverse
            jacobian[i][1] = -dy * distanceInverse
            jacobian[i][2] = -dz * distanceInverse
        }

        // 자코비안 전치 계산
        val jacobianT = transposeMatrix(jacobian)

        // H = J^T * J 계산
        val hessian = multiplyMatrixMatrix(jacobianT, jacobian)

        // 레귤러라이제이션 적용
        val lambda = 1e-3f
        val identityMatrix = Array(3) { FloatArray(3) { 0f } }
        for (i in 0..2) {
            identityMatrix[i][i] = 1f
        }
        val regularizedHessian = addMatrices(hessian, scalarMultiplyMatrix(identityMatrix, lambda))

        // 역행렬 계산
        val hessianInverse = invertMatrix(regularizedHessian)
        if (hessianInverse == null) {
            Log.e("refine_gauss_newton", "Hessian is singular, cannot invert")
            break
        }

        // g = J^T * residuals 계산
        val gradient = multiplyMatrixVector(jacobianT, residuals)

        // Δx = H^-1 * g 계산
        val delta = multiplyMatrixVector(hessianInverse, gradient)

        // 위치 업데이트
        currentPosition = Point(
            currentPosition.x + delta[0],
            currentPosition.y + delta[1],
            currentPosition.z + delta[2]
        )

        // 변화량 확인
        val deltaNorm = sqrt(delta[0].pow(2) + delta[1].pow(2) + delta[2].pow(2))
        if (deltaNorm < tolerance) {
            Log.d("refine_gauss_newton", "Converged at iteration $iteration")
            break
        }

        iteration++
    }

    Log.d("refine_gauss_newton", "Final position: $currentPosition after $iteration iterations")
    return currentPosition
}

// 필요한 함수들 구현

fun transposeMatrix(matrix: Array<FloatArray>): Array<FloatArray> {
    val rowCount = matrix.size
    val colCount = matrix[0].size
    val transposed = Array(colCount) { FloatArray(rowCount) }
    for (i in 0 until rowCount) {
        for (j in 0 until colCount) {
            transposed[j][i] = matrix[i][j]
        }
    }
    return transposed
}

fun multiplyMatrixMatrix(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
    val resultRows = a.size
    val resultCols = b[0].size
    val bRows = b.size
    val result = Array(resultRows) { FloatArray(resultCols) }
    for (i in 0 until resultRows) {
        for (j in 0 until resultCols) {
            var sum = 0f
            for (k in 0 until bRows) {
                sum += a[i][k] * b[k][j]
            }
            result[i][j] = sum
        }
    }
    return result
}
fun addMatrices(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
    val rowCount = a.size
    val colCount = a[0].size
    val result = Array(rowCount) { FloatArray(colCount) }

    for (i in 0 until rowCount) {
        for (j in 0 until colCount) {
            result[i][j] = a[i][j] + b[i][j]
        }
    }
    return result
}
fun scalarMultiplyMatrix(matrix: Array<FloatArray>, scalar: Float): Array<FloatArray> {
    val rowCount = matrix.size
    val colCount = matrix[0].size
    val result = Array(rowCount) { FloatArray(colCount) }

    for (i in 0 until rowCount) {
        for (j in 0 until colCount) {
            result[i][j] = matrix[i][j] * scalar
        }
    }
    return result
}

fun multiplyMatrixVector(matrix: Array<FloatArray>, vector: FloatArray): FloatArray {
    val resultSize = matrix.size
    val vectorSize = vector.size
    val result = FloatArray(resultSize)
    for (i in 0 until resultSize) {
        var sum = 0f
        for (j in 0 until vectorSize) {
            sum += matrix[i][j] * vector[j]
        }
        result[i] = sum
    }
    return result
}

fun invertMatrix(matrix: Array<FloatArray>): Array<FloatArray>? {
    val size = matrix.size
    val augmented = Array(size) { FloatArray(2 * size) }
    // 증분 행렬 생성 [A | I]
    for (i in 0 until size) {
        for (j in 0 until size) {
            augmented[i][j] = matrix[i][j]
        }
        augmented[i][size + i] = 1f
    }
    // 가우스 조던 소거법 적용
    for (i in 0 until size) {
        // 피벗 선택
        var maxRow = i
        for (k in i + 1 until size) {
            if (abs(augmented[k][i]) > abs(augmented[maxRow][i])) {
                maxRow = k
            }
        }
        // 행 교환
        val temp = augmented[i]
        augmented[i] = augmented[maxRow]
        augmented[maxRow] = temp

        // 특이 행렬 확인
        if (abs(augmented[i][i]) < 1e-8f) {
            return null // 역행렬 없음
        }

        // 피벗 행 정규화
        val pivot = augmented[i][i]
        for (j in 0 until 2 * size) {
            augmented[i][j] /= pivot
        }

        // 열 제거
        for (k in 0 until size) {
            if (k != i) {
                val factor = augmented[k][i]
                for (j in 0 until 2 * size) {
                    augmented[k][j] -= factor * augmented[i][j]
                }
            }
        }
    }
    // 역행렬 추출
    val inverse = Array(size) { FloatArray(size) }
    for (i in 0 until size) {
        for (j in 0 until size) {
            inverse[i][j] = augmented[i][j + size]
        }
    }
    return inverse
}



fun calcMiddleBy4Side(distances: List<Float>, anchorPosition: List<Point>): Point {
    Log.d("QWERQWEER",anchorPosition.toString())
    val x = anchorPosition.map{it.x}
    val y = anchorPosition.map{it.y}
    val d = distances.map{it}

    val A1 = arrayOf(
        floatArrayOf(2 * (x[1] - x[0]), 2 * (y[1] - y[0])),
        floatArrayOf(2 * (x[3] - x[2]), 2 * (y[3] - y[2]))
    )
    val B1 = floatArrayOf(
        generateRight(x[1],y[1],d[1]) - generateRight(x[0],y[0],d[0]),
        generateRight(x[3],y[3],d[3]) - generateRight(x[2],y[2],d[2])
    )
    val A2 = arrayOf(
        floatArrayOf(2 * (x[1] - x[2]), 2 * (y[1] - y[2])),
        floatArrayOf(2 * (x[3] - x[0]), 2 * (y[3] - y[0]))
    )
    val B2 = floatArrayOf(
        generateRight(x[1],y[1],d[1]) - generateRight(x[2],y[2],d[2]),
        generateRight(x[3],y[3],d[3]) - generateRight(x[0],y[0],d[0])
    )
    val A3 = arrayOf(
        floatArrayOf(2 * (x[0] - x[2]), 2 * (y[0] - y[2])),
        floatArrayOf(2 * (x[3] - x[1]), 2 * (y[3] - y[1]))
    )
    val B3 = floatArrayOf(
        generateRight(x[0],y[0],d[0]) - generateRight(x[2],y[2],d[2]),
        generateRight(x[3],y[3],d[3]) - generateRight(x[1],y[1],d[1])
    )
    var resultVectorList: List<FloatArray> = mutableListOf(multiplyMatrixVector(invertMatrix(A1),B1), multiplyMatrixVector(invertMatrix(A2),B2), multiplyMatrixVector(invertMatrix(A3),B3))
    var meanPoint: Point = Point()
    resultVectorList.forEach{
        if(isPointInSquare(Point(it[0],it[1]), calcBy4Side(distances,anchorPosition))){
            meanPoint = Point(it[0],it[1])
        }
    }

    val meanArray = FloatArray(4)
    val zArray:MutableList<Float> = mutableListOf()
    for(i in 0..3){
        meanArray[i] = sqrt((meanPoint.x-anchorPosition[i].x).pow(2)+(meanPoint.y-anchorPosition[i].y).pow(2))
    }
    for(i in 0..3){
        if(distances[i].pow(2) >= meanArray[i].pow(2))
            zArray.add(sqrt(distances[i].pow(2) - meanArray[i].pow(2)))
    }
    Log.d("calc_4", "${Point(meanPoint.x, meanPoint.y, zArray.average().toFloat())}")
    return Point(meanPoint.x,meanPoint.y,zArray.average().toFloat())
}

fun sign(o: Point, a: Point, b: Point): Float {
    return (o.x - b.x) * (a.y - b.y) - (a.x - b.x) * (o.y - b.y)
}

fun isPointInTriangle(p: Point, p0: Point, p1: Point, p2: Point): Boolean {
    val b1 = sign(p, p0, p1) < 0.0
    val b2 = sign(p, p1, p2) < 0.0
    val b3 = sign(p, p2, p0) < 0.0

    return (b1 == b2) && (b2 == b3)
}

fun isPointInSquare(p: Point, quad: List<Point>): Boolean {
    // 사각형을 두 개의 삼각형으로 분할
    val t1 = listOf(quad[0], quad[1], quad[2])
    val t2 = listOf(quad[0], quad[2], quad[3])

    return isPointInTriangle(p, t1[0], t1[1], t1[2]) || isPointInTriangle(p, t2[0], t2[1], t2[2])
}

fun calcBy4Side(distances: List<Float>, anchorPosition: List<Point>): List<Point>{
    var results = emptyList<Point>()
    for (i in distances.indices) {
        results = results.plus(
            calcBy3Side(
                listOf(
                    distances[(i + 1) % distances.size],
                    distances[(i + 2) % distances.size],
                    distances[(i + 3) % distances.size]
                ),
                listOf(
                    anchorPosition[(i + 1) % distances.size],
                    anchorPosition[(i + 2) % distances.size],
                    anchorPosition[(i + 3) % distances.size]
                )
            )
        )
    }
    return results
}
fun calcBy3Side(distances: List<Float>, anchorPosition: List<Point>): Point {

    if(distances.size < 3) return Point(-66.66f,-66.66f,-66.66f)

    val x = anchorPosition.map{it.x}
    val y = anchorPosition.map{it.y}
    val z = anchorPosition.map{it.z}
    val d = distances.map{it}

    val A = arrayOf(
        floatArrayOf(2 * (x[1] - x[0]), 2 * (y[1] - y[0])),
        floatArrayOf(2 * (x[2] - x[0]), 2 * (y[2] - y[0]))
    )
    val B = floatArrayOf(
        generateRight(x[1],y[1],d[1]) - generateRight(x[0],y[0],d[0]),
        generateRight(x[2],y[2],d[2]) - generateRight(x[0],y[0],d[0])
    )
    val Ainv = invertMatrix(A)
    val result = multiplyMatrixVector(Ainv, B)


    return Point(result[0], result[1])
}
/*
fun calcByDoubleAnchor2Distance(anchor1:Int, anchor2: Int,anchorPositions: List<Point>, distances: List<Float>, lastZ:Float = 0f): List<Point>{
    val d1 = distances[anchor1]
    val d2 = distances[anchor2]

    return calcByDoubleAnchor2Distance(d1,d2, anchorPositions[anchor1],anchorPositions[anchor2])
}
fun calcByDoubleAnchor2Distance(d1:Float, d2:Float, p1: Point, p2:Point): List<Point>{
    val A = 4 * (p1.y-p2.y).pow(2)
    val B = -2 * (p1.y-p2.y) * (p2.x.pow(2)-p1.x.pow(2)+p1.y.pow(2)-p2.y.pow(2)+d2.pow(2)-d1.pow(2)+ 2 * p1.x * p2.x)
    val C = (p2.x.pow(2)-p1.x.pow(2)+p1.y.pow(2)-p2.y.pow(2)+d2.pow(2)-d1.pow(2)+ 2 * p1.x * p2.x).pow(2) - 4 * (p1.x - p2.x).pow(2) * d1.pow(2)
    val y1 = (-B+sqrt(B.pow(2)-4*A*C))/(2*A)
    val y2 = (-B-sqrt(B.pow(2)-4*A*C))/(2*A)
    val x1 = (generateRight(p1.x,p1.y,d1)-generateRight(p2.x,p2.y,d2)- 2*y1*(p1.y-p2.y))/(2*(p1.x-p2.x))
    val x2 = (generateRight(p1.x,p1.y,d1)-generateRight(p2.x,p2.y,d2)- 2*y2*(p1.y-p2.y))/(2*(p1.x-p2.x))
    return listOf(Point(x1,y1), Point(x2,y2))
}*/

fun calcByDoubleAnchor(distances: List<Float>,anchorPositions: List<Point>,actionSquare: List<Point>): Point {
    return calcByDoubleAnchor(0,1,distances, anchorPositions,actionSquare)
}
fun calcByDoubleAnchor(anchor1:Int, anchor2: Int, distances: List<Float>, anchorPositions: List<Point>, actionSquare: List<Point>): Point {
    val p1 = anchorPositions[anchor1]; val p2 = anchorPositions[anchor2]
    val distanceByAnchor:Float = p1.getDistance(p2)
    val cosTheta: Float = (distances[anchor1].pow(2)+distanceByAnchor.pow(2)-distances[anchor2].pow(2))/(2*distances[anchor1]*distanceByAnchor)
    val tanTheta: Float = sqrt(1-cosTheta.pow(2)) /cosTheta
    val m = (p2.y-p1.y)/(p2.x-p1.x)
    val mPrime = (m+tanTheta)/(1-m*tanTheta)
    val A = arrayOf(
        floatArrayOf((p2.x-p1.x)*2,(p2.y-p1.y)*2),
        floatArrayOf(-mPrime, 1f)
    )
    val B = floatArrayOf(
        (generateRight(p2.x,p2.y,distances[anchor2])- generateRight(p1.x,p1.y,distances[anchor1])),
        (p1.y- mPrime * p1.x)
    )
    val X = multiplyMatrixVector(invertMatrix(A),B)

    if((actionSquare.size>=4 && isPointInSquare(Point(X[0],X[1]),actionSquare)) || actionSquare.size>=3 && isPointInTriangle(Point(X[0],X[1]),actionSquare[0],actionSquare[1],actionSquare[2]) ){
        return Point(X[0], X[1])
    }else{
        //val cosTheta2: Float = (distances[anchor2].pow(2)+distanceByAnchor.pow(2)-distances[anchor1].pow(2))/(2*distances[anchor2]*distanceByAnchor)
        //val tanTheta2: Float = sqrt(1-cosTheta2.pow(2))/cosTheta2
        val mPrime2 = (m-tanTheta)/(1+m*tanTheta)
        val A2 = arrayOf(
            floatArrayOf((p2.x-p1.x)*2,(p2.y-p1.y)*2),
            floatArrayOf(-mPrime2, 1f)
        )
        val B2 = floatArrayOf(
            (generateRight(p2.x,p2.y,distances[anchor2])- generateRight(p1.x,p1.y,distances[anchor1])),
            (p1.y- mPrime2 * p1.x)
        )
        val X2 = multiplyMatrixVector(invertMatrix(A2),B2)
        return Point(X2[0], X2[1])
    }
}

fun generateRight(x:Float, y:Float, d:Float, z: Float= 0f): Float{
    return x*x + y*y + z*z - d*d
}