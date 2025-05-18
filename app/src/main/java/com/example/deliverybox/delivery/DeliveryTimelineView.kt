package com.example.deliverybox.delivery

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.deliverybox.R

class DeliveryTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 확장 함수들
    private val Int.dp: Float
        get() = this * context.resources.displayMetrics.density

    private val Int.sp: Float
        get() = this * context.resources.displayMetrics.scaledDensity

    // 색상 정의
    private val activeColor = ContextCompat.getColor(context, R.color.primary_blue)
    private val inactiveColor = ContextCompat.getColor(context, R.color.gray_300)
    private val completedColor = ContextCompat.getColor(context, R.color.success)
    private val textColor = ContextCompat.getColor(context, R.color.gray_800)
    private val subtextColor = ContextCompat.getColor(context, R.color.gray_500)

    // Paint 객체들
    private val circlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3.dp
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 14.sp
        color = textColor
        typeface = Typeface.DEFAULT_BOLD
    }

    private val subtextPaint = Paint().apply {
        isAntiAlias = true
        textSize = 12.sp
        color = subtextColor
    }

    // 타임라인 데이터
    private var deliverySteps = mutableListOf<DeliveryTimelineStep>()
    private var currentStatus = DeliveryStatus.REGISTERED

    // 레이아웃 상수
    private val circleRadius = 12.dp
    private val stepSpacing = 80.dp
    private val textMarginTop = 16.dp
    private val subtextMarginTop = 8.dp
    private val horizontalPadding = 24.dp
    private val verticalPadding = 16.dp

    // 타임라인 스텝 데이터 클래스
    data class DeliveryTimelineStep(
        val status: DeliveryStatus,
        val title: String,
        val subtitle: String? = null,
        val timestamp: Long? = null,
        val isCompleted: Boolean = false,
        val isCurrent: Boolean = false
    )

    init {
        // 기본 스텝 초기화
        initializeDefaultSteps()
    }

    private fun initializeDefaultSteps() {
        deliverySteps = mutableListOf(
            DeliveryTimelineStep(
                status = DeliveryStatus.REGISTERED,
                title = "접수",
                subtitle = "택배가 등록되었습니다"
            ),
            DeliveryTimelineStep(
                status = DeliveryStatus.PICKED_UP,
                title = "수거",
                subtitle = "택배사에서 수거했습니다"
            ),
            DeliveryTimelineStep(
                status = DeliveryStatus.IN_TRANSIT,
                title = "배송중",
                subtitle = "목적지로 이동 중입니다"
            ),
            DeliveryTimelineStep(
                status = DeliveryStatus.OUT_FOR_DELIVERY,
                title = "배송출발",
                subtitle = "배송기사가 배송을 시작했습니다"
            ),
            DeliveryTimelineStep(
                status = DeliveryStatus.IN_BOX,
                title = "택배함보관",
                subtitle = "택배함에 보관되었습니다"
            ),
            DeliveryTimelineStep(
                status = DeliveryStatus.DELIVERED,
                title = "수령완료",
                subtitle = "택배를 수령했습니다"
            )
        )
    }

    fun setDeliverySteps(steps: List<DeliveryStep>) {
        deliverySteps.clear()
        steps.forEach { step ->
            deliverySteps.add(
                DeliveryTimelineStep(
                    status = getStatusFromStep(step),
                    title = step.description,
                    subtitle = step.location,
                    timestamp = step.timestamp,
                    isCompleted = step.isCompleted
                )
            )
        }
        invalidate()
    }

    fun setCurrentStatus(status: DeliveryStatus) {
        currentStatus = status
        updateStepStates()
        invalidate()
    }

    private fun updateStepStates() {
        val statusOrder = listOf(
            DeliveryStatus.REGISTERED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.OUT_FOR_DELIVERY,
            DeliveryStatus.IN_BOX,
            DeliveryStatus.DELIVERED
        )

        val currentIndex = statusOrder.indexOf(currentStatus)

        deliverySteps.forEachIndexed { index, step ->
            val stepIndex = statusOrder.indexOf(step.status)
            deliverySteps[index] = step.copy(
                isCompleted = stepIndex <= currentIndex,
                isCurrent = stepIndex == currentIndex
            )
        }
    }

    private fun getStatusFromStep(step: DeliveryStep): DeliveryStatus {
        // DeliveryStep의 description이나 stepType을 기반으로 상태 결정
        return when {
            step.description.contains("접수") || step.description.contains("등록") -> DeliveryStatus.REGISTERED
            step.description.contains("수거") || step.description.contains("픽업") -> DeliveryStatus.PICKED_UP
            step.description.contains("배송중") || step.description.contains("이동") -> DeliveryStatus.IN_TRANSIT
            step.description.contains("배송출발") || step.description.contains("출발") -> DeliveryStatus.OUT_FOR_DELIVERY
            step.description.contains("보관") || step.description.contains("택배함") -> DeliveryStatus.IN_BOX
            step.description.contains("수령") || step.description.contains("완료") -> DeliveryStatus.DELIVERED
            else -> DeliveryStatus.REGISTERED
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (deliverySteps.size * stepSpacing + horizontalPadding * 2).toInt()
        val desiredHeight = (circleRadius * 2 + textMarginTop + getMaxTextHeight() +
                subtextMarginTop + getMaxSubtextHeight() + verticalPadding * 2).toInt()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (deliverySteps.isEmpty()) return

        val centerY = verticalPadding + circleRadius
        val stepWidth = (width - horizontalPadding * 2) / (deliverySteps.size - 1).coerceAtLeast(1)

        // 연결선 그리기
        drawConnectingLines(canvas, centerY, stepWidth)

        // 각 스텝 그리기
        deliverySteps.forEachIndexed { index, step ->
            val centerX = horizontalPadding + index * stepWidth
            drawStep(canvas, step, centerX, centerY)
        }
    }

    private fun drawConnectingLines(canvas: Canvas, centerY: Float, stepWidth: Float) {
        for (i in 0 until deliverySteps.size - 1) {
            val startX = horizontalPadding + i * stepWidth + circleRadius
            val endX = horizontalPadding + (i + 1) * stepWidth - circleRadius

            val currentStep = deliverySteps[i]
            val nextStep = deliverySteps[i + 1]

            linePaint.color = if (currentStep.isCompleted && nextStep.isCompleted) {
                completedColor
            } else if (currentStep.isCompleted || currentStep.isCurrent) {
                activeColor
            } else {
                inactiveColor
            }

            canvas.drawLine(startX, centerY, endX, centerY, linePaint)
        }
    }

    private fun drawStep(canvas: Canvas, step: DeliveryTimelineStep, centerX: Float, centerY: Float) {
        // 원 그리기
        circlePaint.color = when {
            step.isCompleted -> completedColor
            step.isCurrent -> activeColor
            else -> inactiveColor
        }
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

        // 체크마크 또는 점 그리기
        if (step.isCompleted) {
            drawCheckmark(canvas, centerX, centerY)
        } else if (step.isCurrent) {
            circlePaint.color = Color.WHITE
            canvas.drawCircle(centerX, centerY, circleRadius * 0.5f, circlePaint)
        }

        // 텍스트 그리기
        drawStepText(canvas, step, centerX, centerY + circleRadius + textMarginTop)
    }

    private fun drawCheckmark(canvas: Canvas, centerX: Float, centerY: Float) {
        val checkPath = Path().apply {
            moveTo(centerX - circleRadius * 0.5f, centerY)
            lineTo(centerX - circleRadius * 0.1f, centerY + circleRadius * 0.3f)
            lineTo(centerX + circleRadius * 0.5f, centerY - circleRadius * 0.3f)
        }

        val checkPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.dp
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        canvas.drawPath(checkPath, checkPaint)
    }

    private fun drawStepText(canvas: Canvas, step: DeliveryTimelineStep, centerX: Float, textY: Float) {
        // 제목 그리기
        textPaint.color = if (step.isCompleted || step.isCurrent) textColor else subtextColor
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(step.title, centerX, textY, textPaint)

        // 부제목 그리기 (있는 경우)
        step.subtitle?.let { subtitle ->
            subtextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(subtitle, centerX, textY + subtextMarginTop, subtextPaint)
        }

        // 타임스탬프 그리기 (있는 경우)
        step.timestamp?.let { timestamp ->
            val timeText = formatTimestamp(timestamp)
            subtextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(timeText, centerX, textY + subtextMarginTop + subtextPaint.textSize + 4.dp, subtextPaint)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMaxTextHeight(): Float {
        return textPaint.textSize + 4.dp
    }

    private fun getMaxSubtextHeight(): Float {
        return subtextPaint.textSize * 2 + 8.dp // 부제목 + 타임스탬프 고려
    }

    // 공개 API
    fun addStep(status: DeliveryStatus, title: String, subtitle: String? = null, timestamp: Long? = null) {
        deliverySteps.add(
            DeliveryTimelineStep(
                status = status,
                title = title,
                subtitle = subtitle,
                timestamp = timestamp
            )
        )
        updateStepStates()
        invalidate()
    }

    fun clearSteps() {
        deliverySteps.clear()
        invalidate()
    }

    fun updateStep(status: DeliveryStatus, title: String? = null, subtitle: String? = null, timestamp: Long? = null) {
        val stepIndex = deliverySteps.indexOfFirst { it.status == status }
        if (stepIndex != -1) {
            val currentStep = deliverySteps[stepIndex]
            deliverySteps[stepIndex] = currentStep.copy(
                title = title ?: currentStep.title,
                subtitle = subtitle ?: currentStep.subtitle,
                timestamp = timestamp ?: currentStep.timestamp
            )
            invalidate()
        }
    }
}