package ml.asvsharma.oscilloscopeapp

import android.graphics.Canvas
import android.util.Log
import android.view.SurfaceHolder

class WaveformPlotThread(private val holder: SurfaceHolder, private val plot_area: WaveformView) : Thread() {
    private var _run = false
    fun setRunning(run: Boolean) {
        _run = run
    }

    override fun run() {
        lateinit var c: Canvas
        while (_run) {
            try {
                c = holder.lockCanvas(null)
                synchronized(holder) {
                    plot_area.PlotPoints(c)
                }
            }catch (e:Exception){
                Log.d("FUCKER",e.toString())
            }
            finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c)
                    }catch (e:Exception){
                        Log.d("FUCK",e.toString())
                    }
                }
            }
        }
    }

}