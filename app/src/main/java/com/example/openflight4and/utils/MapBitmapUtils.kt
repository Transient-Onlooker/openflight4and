package com.example.openflight4and.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.VectorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.example.openflight4and.R
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightPrimary
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object MapBitmapUtils {

    fun createCustomMarkerBitmap(
        context: Context,
        iata: String,
        label: String,
        isSelected: Boolean
    ): BitmapDescriptor {
        val backgroundColor = if (isSelected) FlightPrimary.toArgb() else 0xFF262523.toInt()
        val textColor = if (isSelected) FlightBlack.toArgb() else FlightPrimary.toArgb()
        val strokeColor = FlightGray.toArgb()

        val density = context.resources.displayMetrics.density
        val width = ((if (isSelected) 70 else 60) * density).toInt()
        val height = ((if (isSelected) 46 else 40) * density).toInt()
        val cornerRadius = 8 * density

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        if (!isSelected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = strokeColor
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14 * density
        paint.textAlign = Paint.Align.CENTER

        val fontMetrics = paint.fontMetrics
        val yOffsetIata = (height * 0.45f) - ((fontMetrics.descent + fontMetrics.ascent) / 2)
        canvas.drawText(iata, width / 2f, yOffsetIata, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10 * density
        val yOffsetLabel = (height * 0.8f)
        canvas.drawText(label, width / 2f, yOffsetLabel, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // 비행기 아이콘 비트맵 생성 (벡터 -> 비트맵 변환)
    fun createPlaneMarkerBitmap(context: Context): BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_flight_marker) as? VectorDrawable
            ?: return BitmapDescriptorFactory.defaultMarker()

        val width = vectorDrawable.intrinsicWidth
        val height = vectorDrawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 색상 적용 (FlightPrimary)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.colorFilter = PorterDuffColorFilter(FlightPrimary.toArgb(), PorterDuff.Mode.SRC_IN)
        vectorDrawable.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
