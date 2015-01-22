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
	final int REPORT_INTERVAL_SEC = 10;

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

	private void reportProgress() {
		reportProgress(false);
	}

	private void reportProgress(boolean force) {
		double elapsed = getElapsed();
		if (elapsed == 0)
			elapsed = 0.1;
		double secondsSinceLastReport = elapsed - prevReportTime;
		if (force || secondsSinceLastReport >= REPORT_INTERVAL_SEC) {
			long totalCharsDone = getCharsProcessed();
			long charsDoneSinceLastReport = totalCharsDone - prevCharsDoneReported;

			double lastMbDone = charsDoneSinceLastReport / 1000000.0;
			double lastSpeed = lastMbDone / secondsSinceLastReport;

			double mbDone = totalCharsDone / 1000000.0;
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

			System.out
					.printf("%d docs done (%d MB, %dk tokens). Average speed %.1fk tokens/s (%.1f MB/s), currently %.1fk tokens/s (%.1f MB/s)\n",
							getDocsDone(), (int) mbDone, (int) kTokensDone, overallTokenSpeed,
							overallSpeed, curTokensSpeed, curSpeed);

			prevCharsDoneReported = totalCharsDone;
			prevTokensDoneReported = totalTokensDone;
			prevReportTime = elapsed;
		}
	}

	private double getElapsed() {
		return (System.currentTimeMillis() - getIndexStartTime()) / 1000.0;
	}

	@Override
	public void indexEnd() {
		super.indexEnd();
		reportProgress(true);
		System.out.println("Done. Elapsed time: " + TimeUtil.describeInterval(System.currentTimeMillis() - getIndexStartTime()));
	}

	/**
	 * An index error occurred. Report it.
	 *
	 * @param error type of error, i.e. "not found"
	 * @param unitType type of indexing unit, i.e. "file", "zip", "tgz"
	 * @param unit the indexing unit in which the error occurred
	 * @param subunit optional subunit (i.e. which file inside zip, or null for regular files)
	 * @return true if indexing should continue
	 */
	@Override
	public boolean errorOccurred(String error, String unitType, File unit, File subunit) {
		System.out.println("An error occurred during indexing!");
		System.out.println("error: " + error + ", unitType: " + unitType +
				", unit: " + unit + ", subunit: " + subunit);
		return super.errorOccurred(error, unitType, unit, subunit);
	}

}
