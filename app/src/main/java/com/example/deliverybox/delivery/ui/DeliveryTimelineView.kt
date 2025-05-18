package com.example.deliverybox.delivery.ui

import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.DeliveryStep
import com.example.deliverybox.delivery.dp
import com.example.deliverybox.delivery.sp

class DeliveryTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var deliverySteps: List<DeliveryStep> = emptyList()
    private var currentStatus: DeliveryStatus = DeliveryStatus.REGISTERED

    // Paint 객체들
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4.dp
        style = Paint.Style.STROKE
    }

    private val completedLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_blue)
        strokeWidth = 4.dp
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14.sp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.sp
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    private val descriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13.sp
        textAlign = Paint.Align.START
    }

    // 상수
    private val circleRadius = 16.dp
    private val lineMargin = 8.dp
    private val textMargin = 12.dp
    private val stepVerticalSpacing = 80.dp

    // 단계별 정보
    private val stepInfo = listOf(
        StepInfo("ORDER_PLACED", "접수", R.drawable.ic_receipt),
        StepInfo("PICKED_UP", "상차", R.drawable.ic_pickup),
        StepInfo("IN_TRANSIT", "이동중", R.drawable.ic_truck),
        StepInfo("OUT_FOR_DELIVERY", "배송출발", R.drawable.ic_delivery),
        StepInfo("DELIVERED", "배송완료", R.drawable.ic_delivered)
    )

    data class StepInfo(val type: String, val title: String, val iconRes: Int)

    fun setDeliverySteps(steps: List<DeliveryStep>, status: DeliveryStatus) {
        deliverySteps = steps
        currentStatus = status
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (stepInfo.isEmpty()) return

        val startX = circleRadius + 20.dp
        var currentY = circleRadius + 20.dp

        stepInfo.forEachIndexed { index, stepInfo ->
            val step = findStepByType(stepInfo.type)
            val isCompleted = isStepCompleted(index)
            val isCurrent = isCurrentStep(index)

            // 연결선 그리기 (다음 단계가 있는 경우)
            if (index < this.stepInfo.size - 1) {
                drawConnectionLine(canvas, startX, currentY, isCompleted)
            }

            // 단계 그리기
            drawStep(canvas, startX, currentY, stepInfo, step, isCompleted, isCurrent)

            currentY += stepVerticalSpacing
        }
    }

    private fun drawConnectionLine(canvas: Canvas, centerX: Float, centerY: Float, isCompleted: Boolean) {
        val startY = centerY + circleRadius + lineMargin
        val endY = centerY + stepVerticalSpacing - circleRadius - lineMargin

        val paint = if (isCompleted) completedLinePaint else linePaint.apply {
            color = ContextCompat.getColor(context, R.color.gray_300)
        }

        canvas.drawLine(centerX, startY, centerX, endY, paint)
    }

    private fun drawStep(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        stepInfo: StepInfo,
        step: DeliveryStep?,
        isCompleted: Boolean,
        isCurrent: Boolean
    ) {
        // 원 색상 설정
        circlePaint.color = when {
            isCompleted -> ContextCompat.getColor(context, R.color.primary_blue)
            isCurrent -> ContextCompat.getColor(context, R.color.primary_blue_light)
            else -> ContextCompat.getColor(context, R.color.gray_300)
        }

        // 원 그리기
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

        // 아이콘 또는 체크마크 그리기
        if (isCompleted) {
            drawCheckMark(canvas, centerX, centerY)
        } else {
            drawIcon(canvas, centerX, centerY, stepInfo.iconRes, isCurrent)
        }

        // 제목 그리기
        textPaint.color = when {
            isCompleted || isCurrent -> ContextCompat.getColor(context, R.color.text_primary)
            else -> ContextCompat.getColor(context, R.color.text_secondary)
        }

        textPaint.textAlign = Paint.Align.START
        canvas.drawText(
            stepInfo.title,
            centerX + circleRadius + textMargin,
            centerY - 5.dp,
            textPaint
        )

        // 시간 및 설명 그리기 (완료된 단계만)
        if (isCompleted && step != null) {
            // 시간
            val timeText = formatTime(step.timestamp)
            canvas.drawText(
                timeText,
                centerX + circleRadius + textMargin,
                centerY + 15.dp,
                timePaint
            )

            // 설명 및 위치
            if (step.description.isNotEmpty() || step.location?.isNotEmpty() == true) {
                val description = buildString {
                    if (step.location?.isNotEmpty() == true) append(step.location)
                    if (step.description.isNotEmpty()) {
                        if (isNotEmpty()) append(" | ")
                        append(step.description)
                    }
                }

                descriptionPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
                canvas.drawText(
                    description,
                    centerX + circleRadius + textMargin,
                    centerY + 35.dp,
                    descriptionPaint
                )
            }
        }
    }

    private fun drawCheckMark(canvas: Canvas, centerX: Float, centerY: Float) {
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3.dp
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val checkPath = Path().apply {
            moveTo(centerX - circleRadius * 0.4f, centerY)
            lineTo(centerX - circleRadius * 0.1f, centerY + circleRadius * 0.3f)
            lineTo(centerX + circleRadius * 0.4f, centerY - circleRadius * 0.3f)
        }

        canvas.drawPath(checkPath, checkPaint)
    }

    private fun drawIcon(canvas: Canvas, centerX: Float, centerY: Float, iconRes: Int, isActive: Boolean) {
        val drawable = ContextCompat.getDrawable(context, iconRes)
        drawable?.let {
            val halfSize = (circleRadius * 0.6f).toInt()
            it.setBounds(
                (centerX - halfSize).toInt(),
                (centerY - halfSize).toInt(),
                (centerX + halfSize).toInt(),
                (centerY + halfSize).toInt()
            )

            it.setTint(
                if (isActive) Color.WHITE
                else ContextCompat.getColor(context, R.color.text_secondary)
            )
            it.draw(canvas)
        }
    }

    private fun isStepCompleted(stepIndex: Int): Boolean {
        return stepIndex < getCurrentStepIndex()
    }

    private fun isCurrentStep(stepIndex: Int): Boolean {
        return stepIndex == getCurrentStepIndex()
    }

    private fun getCurrentStepIndex(): Int {
        return when (currentStatus) {
            DeliveryStatus.REGISTERED -> 0
            DeliveryStatus.PICKED_UP -> 1
            DeliveryStatus.IN_TRANSIT -> 2
            DeliveryStatus.OUT_FOR_DELIVERY -> 3
            DeliveryStatus.DELIVERED, DeliveryStatus.IN_BOX -> 4
        }
    }

    private fun findStepByType(type: String): DeliveryStep? {
        return deliverySteps.find { it.stepType == type }
    }

    private fun formatTime(timestamp: Long): String {
        val format = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        return format.format(Date(timestamp))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (stepInfo.size * stepVerticalSpacing + circleRadius * 2 + 40.dp).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}