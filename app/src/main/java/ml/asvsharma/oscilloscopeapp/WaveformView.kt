package ml.asvsharma.oscilloscopeapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView


class WaveformView(context: Context?, attrs: AttributeSet?) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {
    private val plot_thread: WaveformPlotThread
    private val ch1_color: Paint = Paint()
    private val ch2_color: Paint = Paint()
    private val grid_paint: Paint = Paint()
    private val cross_paint: Paint = Paint()
    private val outline_paint: Paint = Paint()
    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        plot_thread.setRunning(true)
        plot_thread.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        plot_thread.setRunning(false)
        while (retry) {
            try {
                plot_thread.join()
                retry = false
            } catch (e: InterruptedException) {
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        PlotPoints(canvas)
    }

    fun set_data(data1: IntArray, data2: IntArray) {
        plot_thread.setRunning(false)
        for (x in 0 until WIDTH) { // channel 1
            if (x < data1.size) {
                ch1_data[x] =
                    HEIGHT - data1[x] + 1
            } else {
                ch1_data[x] = ch1_pos
            }
            // channel 2
            if (x < data1.size) {
                ch2_data[x] =
                    HEIGHT - data2[x] + 1
            } else {
                ch2_data[x] = ch2_pos
            }
        }
        plot_thread.setRunning(true)
    }

    fun PlotPoints(canvas: Canvas) { // clear screen
        canvas.drawColor(Color.rgb(20, 20, 20))
        // draw vertical grids
        for (vertical in 1..9) {
            canvas.drawLine(
                (vertical * (WIDTH / 10) + 1).toFloat(),
                1F,
                (vertical * (WIDTH / 10) + 1).toFloat(),
                (HEIGHT + 1).toFloat(),
                grid_paint
            )
        }
        // draw horizontal grids
        for (horizontal in 1..9) {
            canvas.drawLine(
                1F,
                (horizontal * (HEIGHT / 10) + 1).toFloat(),
                (WIDTH + 1).toFloat(),
                (horizontal * (HEIGHT / 10) + 1).toFloat(),
                grid_paint
            )
        }
        // draw outline
        canvas.drawLine(0F, 0F, (WIDTH + 1).toFloat(), 0F, outline_paint) // top
        canvas.drawLine(
            (WIDTH + 1).toFloat(),
            0F,
            (WIDTH + 1).toFloat(),
            (HEIGHT + 1).toFloat(),
            outline_paint
        ) //right
        canvas.drawLine(
            0F,
            (HEIGHT + 1).toFloat(),
            (WIDTH + 1).toFloat(),
            (HEIGHT + 1).toFloat(),
            outline_paint
        ) // bottom
        canvas.drawLine(0F, 0F, 0F, (HEIGHT + 1).toFloat(), outline_paint) //left
        // plot data
        for (x in 0 until WIDTH - 1) {
            canvas.drawLine(
                (x + 1).toFloat(),
                ch2_data[x].toFloat(),
                (x + 2).toFloat(),
                ch2_data[x + 1].toFloat(),
                ch2_color
            )
            canvas.drawLine(
                (x + 1).toFloat(),
                ch1_data[x].toFloat(),
                (x + 2).toFloat(),
                ch1_data[x + 1].toFloat(),
                ch1_color
            )
        }
    }

    companion object {
        // plot area size
        private const val WIDTH = 766
        private const val HEIGHT = 592
        private val ch1_data = IntArray(WIDTH)
        private val ch2_data = IntArray(WIDTH)
        private const val ch1_pos = HEIGHT/4
        private const val ch2_pos = (HEIGHT/4)*3 - 100
    }

    init {
        holder.addCallback(this)
        // initial values
        for (x in 0 until WIDTH) {
            ch1_data[x] = ch1_pos
            ch2_data[x] = ch2_pos
        }
        plot_thread = WaveformPlotThread(holder, this)
        ch1_color.color = Color.GREEN
        ch2_color.color = Color.YELLOW
        grid_paint.color = Color.rgb(100, 100, 100)
        cross_paint.color = Color.rgb(70, 100, 70)
        outline_paint.color = Color.WHITE
    }
}