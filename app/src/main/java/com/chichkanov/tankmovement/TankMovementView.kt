package com.chichkanov.tankmovement

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.android.parcel.Parcelize
import kotlin.math.atan2
import kotlin.math.roundToInt

class TankMovementView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var destinationCoordinates: Coordinates? = null
    private var destinationPointRadius: Float
    private val destinationPaint = Paint().apply { style = Paint.Style.FILL }

    private val movePath = Path()
    private var pathMeasure = PathMeasure(movePath, false)
    private var pathLength = pathMeasure.length
    private val pathPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var tankProperties: TankProperties
    private lateinit var tankBitmap: Bitmap
    private lateinit var tankBitmapMatrix: Matrix

    private var tempPos = FloatArray(2)
    private var tempTan = FloatArray(2)

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TankMovementView, defStyleAttr, 0)

        try {
            val pathColor = typedArray.getColor(R.styleable.TankMovementView_tmv_path_color, Color.GRAY)
            pathPaint.color = pathColor

            val pathWidth = typedArray.getDimension(
                R.styleable.TankMovementView_tmv_path_width,
                resources.getDimension(R.dimen.tmv_default_path_width)
            )
            pathPaint.strokeWidth = pathWidth

            tankProperties = createTankProperties(typedArray)

            destinationPointRadius = typedArray.getDimension(
                R.styleable.TankMovementView_tmv_destination_point_radius,
                resources.getDimension(R.dimen.tmv_default_destination_point_radius)
            )
            destinationPaint.color = typedArray.getColor(
                R.styleable.TankMovementView_tmv_destination_point_color,
                Color.BLACK
            )
        } finally {
            typedArray.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(movePath, pathPaint)

        destinationCoordinates?.let { coord ->
            tankBitmapMatrix.reset()

            canvas.drawCircle(coord.x, coord.y, destinationPointRadius, destinationPaint)

            when {
                tankProperties.targetAngle - tankProperties.currentAngle > tankProperties.rotationSpeed -> {
                    tankProperties.currentAngle += tankProperties.rotationSpeed
                    tankProperties.updateMatrix(tankBitmapMatrix)
                    invalidate()
                }
                tankProperties.currentAngle - tankProperties.targetAngle > tankProperties.rotationSpeed -> {
                    tankProperties.currentAngle -= tankProperties.rotationSpeed
                    tankProperties.updateMatrix(tankBitmapMatrix)
                    invalidate()
                }
                else -> {
                    tankProperties.currentAngle = tankProperties.targetAngle
                    if (tankProperties.distanceGone < pathLength) {
                        pathMeasure.getPosTan(tankProperties.distanceGone, tempPos, tempTan)

                        tankProperties.targetAngle = (atan2(tempTan[1], tempTan[0]) * 180.0 / Math.PI).toFloat()
                        tankProperties.coordinates.x = tempPos[0] - tankProperties.offsetX
                        tankProperties.coordinates.y = tempPos[1] - tankProperties.offsetY

                        tankProperties.updateMatrix(tankBitmapMatrix)

                        tankProperties.distanceGone += tankProperties.speed
                        invalidate()
                    } else {
                        tankProperties.updateMatrix(tankBitmapMatrix)
                        destinationCoordinates = null
                        invalidate()
                    }
                }
            }
        }

        canvas.drawBitmap(tankBitmap, tankBitmapMatrix, null)
    }

    // Пока не поддерживаются Спец возможности
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        destinationCoordinates = Coordinates(event.x, event.y)

        if (movePath.isEmpty) {
            movePath.moveTo(
                tankProperties.coordinates.x,
                tankProperties.coordinates.y
            )
        }
        movePath.lineTo(event.x, event.y)

        pathMeasure = PathMeasure(movePath, false)
        pathLength = pathMeasure.length

        invalidate()

        return super.onTouchEvent(event)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState!!)
        ss.tankCoordinates = tankProperties.coordinates
        ss.tankRotationAngle = tankProperties.currentAngle
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        tankProperties.coordinates = ss.tankCoordinates
        tankProperties.currentAngle = ss.tankRotationAngle
        tankBitmapMatrix.reset()
        tankProperties.updateMatrix(tankBitmapMatrix)
    }

    private fun createTankProperties(typedArray: TypedArray): TankProperties {
        val tankIcon = typedArray.getResourceId(R.styleable.TankMovementView_tmv_tank_icon, R.drawable.ic_tank)
        val carBitmapUnscaled = BitmapFactory.decodeResource(resources, tankIcon)
        val vehicleSize = typedArray.getDimension(
            R.styleable.TankMovementView_tmv_tank_size,
            resources.getDimension(R.dimen.tmv_default_tank_size)
        )
        val aspectRatio = carBitmapUnscaled.width / carBitmapUnscaled.height.toFloat()
        val width = vehicleSize.toInt()
        val height = (width / aspectRatio).roundToInt()

        tankBitmap = Bitmap.createScaledBitmap(carBitmapUnscaled, width, height, false)
        val coordinates = Coordinates(tankBitmap.width.toFloat(), tankBitmap.height.toFloat())
        val offsetX = tankBitmap.width / 2f
        val offsetY = tankBitmap.height / 2f
        tankBitmapMatrix = Matrix().apply { postTranslate(coordinates.x - offsetX, coordinates.y - offsetY) }

        return TankProperties(
            Coordinates(tankBitmap.width.toFloat(), tankBitmap.height.toFloat()),
            typedArray.getInteger(R.styleable.TankMovementView_tmv_tank_speed, DEFAULT_TANK_SPEED),
            typedArray.getInteger(
                R.styleable.TankMovementView_tmv_tank_rotation_speed,
                DEFAULT_TANK_ROTATION_SPEED
            ),
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    internal class SavedState : BaseSavedState {
        lateinit var tankCoordinates: Coordinates
        var tankRotationAngle: Float = 0f

        constructor(source: Parcel) : super(source) {
            tankCoordinates = source.readParcelable(javaClass.classLoader)
            tankRotationAngle = source.readFloat()
        }

        constructor(superState: Parcelable) : super(superState)

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeParcelable(tankCoordinates, PARCELABLE_WRITE_RETURN_VALUE)
            out.writeFloat(tankRotationAngle)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    @Parcelize
    internal data class Coordinates(
        var x: Float,
        var y: Float
    ) : Parcelable

    @Parcelize
    internal data class TankProperties(
        var coordinates: Coordinates,
        var speed: Int,
        var rotationSpeed: Int,
        var distanceGone: Float = 0f,
        var currentAngle: Float = 0.toFloat(),
        var targetAngle: Float = 0.toFloat(),
        var offsetX: Float = 0f,
        var offsetY: Float = 0f
    ) : Parcelable {

        fun updateMatrix(tankMatrix: Matrix) {
            tankMatrix.postRotate(currentAngle, offsetX, offsetY)
            tankMatrix.postTranslate(coordinates.x, coordinates.y)
        }
    }

    private companion object {
        private const val DEFAULT_TANK_SPEED = 5
        private const val DEFAULT_TANK_ROTATION_SPEED = 2
    }
}