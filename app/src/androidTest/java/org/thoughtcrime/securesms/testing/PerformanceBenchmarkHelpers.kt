package org.thoughtcrime.securesms.testing

import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PerformanceBenchmarkHelpers {
  private const val TAG = "PerformanceBenchmarkHelpers"

  /** Run a lightweight dry-run benchmark that collects memory and CPU info for the app
   * and writes results to the app's external files directory under 'benchmark-results'.
   */
  fun runDryRunBenchmark(): File? {
    try {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val ui = instrumentation.uiAutomation

      val outDir = File(instrumentation.targetContext.getExternalFilesDir(null), "benchmark-results")
      outDir.mkdirs()
      val outFile = File(outDir, "meminfo-${System.currentTimeMillis()}.txt")

      val pfd = ui.executeShellCommand("dumpsys meminfo org.thoughtcrime.securesms")
      val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
      FileOutputStream(outFile).use { fos ->
        input.copyTo(fos)
      }
      Log.i(TAG, "Wrote benchmark output to ${outFile.absolutePath}")
      return outFile
    } catch (e: Exception) {
      Log.w(TAG, "Benchmark collection failed", e)
      return null
    }
  }
}


