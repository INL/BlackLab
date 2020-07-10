/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index;

import java.io.File;

import nl.inl.util.TimeUtil;

/**
 * Used to report progress while indexing, so we can give feedback to the user.
 */
public class IndexListenerReportConsole extends IndexListener {
    static final int REPORT_INTERVAL_SEC = 10;

    long prevCharsDoneReported = 0;

    long prevTokensDoneReported = 0;

    double prevReportTime = 0;

    double curSpeed = -1;

    double curTokensSpeed = -1;

    @Override
    public synchronized void charsDone(long charsDone) {
        super.charsDone(charsDone);

        reportProgress();
    }

    private synchronized void reportProgress() {
        reportProgress(false);
    }

    private synchronized void reportProgress(boolean force) {
        double elapsed = getElapsed();
        if (elapsed == 0)
            elapsed = 0.1;
        double secondsSinceLastReport = elapsed - prevReportTime;
        if (force || secondsSinceLastReport >= REPORT_INTERVAL_SEC) {
            long totalCharsDone = getCharsProcessed();
            long charsDoneSinceLastReport = totalCharsDone - prevCharsDoneReported;

            double lastMbDone = charsDoneSinceLastReport / 1_000_000.0;
            double lastSpeed = lastMbDone / secondsSinceLastReport;

            double mbDone = totalCharsDone / 1_000_000.0;
            double overallSpeed = mbDone / elapsed;

            if (curSpeed < 0)
                curSpeed = overallSpeed;
            curSpeed = curSpeed * 0.7 + lastSpeed * 0.3;

            long totalTokensDone = getTokensProcessed();
            long tokensDoneSinceLastReport = totalTokensDone - prevTokensDoneReported;

            double lastKDone = tokensDoneSinceLastReport / 1000.0;
            double lastTokensSpeed = lastKDone / secondsSinceLastReport;

            double kTokensDone = totalTokensDone / 1000.0;
            double overallTokenSpeed = kTokensDone / elapsed;

            if (curTokensSpeed < 0)
                curTokensSpeed = overallTokenSpeed;
            curTokensSpeed = curTokensSpeed * 0.7 + lastTokensSpeed * 0.3;

            long indexTimeSoFar = System.currentTimeMillis() - getIndexStartTime();
            System.out
                    .printf("%s docs (%s, %s tokens); avg. %.1fk tok/s (%.1f MB/s); " +
                            "currently %.1fk tok/s (%.1f MB/s); %s elapsed%n",
                            formatNumber(getDocsDone()), formatSizeBytes(totalCharsDone), formatNumber(totalTokensDone), overallTokenSpeed,
                            overallSpeed, curTokensSpeed, curSpeed, formatTimeMs(indexTimeSoFar));

            prevCharsDoneReported = totalCharsDone;
            prevTokensDoneReported = totalTokensDone;
            prevReportTime = elapsed;
        }
    }

    private static String formatTimeMs(long t) {
        if (t < 1000)
            return t + " ms";
        t /= 1000;
        if (t < 60)
            return t + " sec";
        t /= 60;
        if (t < 60)
            return t + " min";
        int h = (int)(t / 60);
        t %= 60;
        return String.format("%dh %02dmin", h, t);
    }

    private static String formatSizeBytes(double sz) {
        if (sz < 1000)
            return String.format("%d B", (int)sz);
        sz /= 1000;
        if (sz < 1000)
            return String.format("%d kB", (int)sz);
        sz /= 1000;
        if (sz < 1000)
            return String.format("%d MB", (int)sz);
        sz /= 1000.0;
        return String.format("%.1f GB", sz);
    }

    private static String formatNumber(double n) {
        if (n < 1000)
            return String.format("%d", (int)n);
        n /= 1000;
        if (n < 1000)
            return String.format("%.1fk", n);
        n /= 1000;
        if (n < 1000)
            return String.format("%.1fM", n);
        n /= 1000;
        return String.format("%.2fG", n);
    }

    private double getElapsed() {
        return (System.currentTimeMillis() - getIndexStartTime()) / 1000.0;
    }

    @Override
    public synchronized void indexEnd() {
        super.indexEnd();
        reportProgress(true);
        System.out.println(
                "Done. Elapsed time: " + TimeUtil.describeInterval(System.currentTimeMillis() - getIndexStartTime()));
    }

    @Override
    public synchronized boolean errorOccurred(Throwable e, String path, File f) {
        System.out.println("An error occurred during indexing!");
        System.out.println("error: " + e.getMessage() + " (in " + path + ")");
        e.printStackTrace();
        return super.errorOccurred(e, path, f);
    }

    @Override
    public synchronized void warning(String msg) {
        System.out.println("WARNING: " + msg);
    }

}
