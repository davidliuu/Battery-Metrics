/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.battery.metrics.memory;

import static com.facebook.battery.metrics.core.Utilities.checkNotNull;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Debug;
import android.system.Os;
import android.system.OsConstants;
import androidx.annotation.GuardedBy;
import com.facebook.battery.metrics.core.ProcFileReader;
import com.facebook.battery.metrics.core.SystemMetricsCollector;
import com.facebook.battery.metrics.core.SystemMetricsLogger;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.infer.annotation.ThreadSafe;
import java.util.concurrent.atomic.AtomicLong;

@Nullsafe(Nullsafe.Mode.LOCAL)
@ThreadSafe
public class MemoryMetricsCollector extends SystemMetricsCollector<MemoryMetrics> {
  private static final String TAG = "MemoryMetricsCollector";

  private static final String PROC_STAT_FILE_PATH = "/proc/self/statm";
  private static final int KB = 1024;

  private final ThreadLocal<ProcFileReader> mProcFileReader = new ThreadLocal<>();
  private final AtomicLong mCounter = new AtomicLong();
  private long pageSizeKb;

  @TargetApi(21)
  public MemoryMetricsCollector() {
    pageSizeKb = 4;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      try {
        long pageSizeB = Os.sysconf(OsConstants._SC_PAGESIZE);
        if (pageSizeB > 0) {
          pageSizeKb = pageSizeB / KB;
        }
      } catch (Throwable t) {
        // shouldn't happen; fallback to default value
      }
    }
  }

  @GuardedBy("this")
  private boolean mIsEnabled = false;

  @Override
  @ThreadSafe(enableChecks = false)
  public synchronized boolean getSnapshot(MemoryMetrics snapshot) {
    checkNotNull(snapshot, "Null value passed to getSnapshot!");
    if (!mIsEnabled) {
      return false;
    }

    /* this helps to track the latest snapshot, diff/sum always picks latest as truth */
    snapshot.sequenceNumber = mCounter.incrementAndGet();

    snapshot.javaHeapMaxSizeKb = getRuntimeMaxMemory() / KB;
    snapshot.javaHeapAllocatedKb = (getRuntimeTotalMemory() - getRuntimeFreeMemory()) / KB;

    snapshot.nativeHeapSizeKb = Debug.getNativeHeapSize() / KB;
    snapshot.nativeHeapAllocatedKb = Debug.getNativeHeapAllocatedSize() / KB;

    snapshot.vmSizeKb = -1;
    snapshot.vmRssKb = -1;

    try {
      ProcFileReader reader = mProcFileReader.get();
      if (reader == null) {
        reader = new ProcFileReader(getPath());
        mProcFileReader.set(reader);
      }

      reader.reset();

      if (reader.isValid()) {
        snapshot.vmSizeKb = readField(reader);
        snapshot.vmRssKb = readField(reader);
      }
    } catch (ProcFileReader.ParseException pe) {
      SystemMetricsLogger.wtf(TAG, "Unable to parse memory (statm) field", pe);
    }

    return true;
  }

  public synchronized void enable() {
    mIsEnabled = true;
  }

  @Override
  public MemoryMetrics createMetrics() {
    return new MemoryMetrics();
  }

  private long readField(ProcFileReader reader) {
    long memoryKb = reader.readNumber() * pageSizeKb;
    reader.skipSpaces();
    return memoryKb;
  }

  protected String getPath() {
    return PROC_STAT_FILE_PATH;
  }

  protected long getRuntimeMaxMemory() {
    return Runtime.getRuntime().maxMemory();
  }

  protected long getRuntimeTotalMemory() {
    return Runtime.getRuntime().totalMemory();
  }

  protected long getRuntimeFreeMemory() {
    return Runtime.getRuntime().freeMemory();
  }
}
