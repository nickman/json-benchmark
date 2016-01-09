/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.heliosapm.benchmarks.json;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;

import com.heliosapm.utils.enums.SpaceUnit;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;

/**
 * <p>Title: JVMSummaryProfiler</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.benchmarks.json.JVMSummaryProfiler</code></p>
 */

public class JVMSummaryProfiler implements InternalProfiler {
	
	private static final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
	private static final OperatingSystemMXBean OS =  (OperatingSystemMXBean)sun.management.ManagementFactoryHelper.getOperatingSystemMXBean();
	private static final ThreadMXBean TX =  (ThreadMXBean)sun.management.ManagementFactoryHelper.getThreadMXBean();
	
	
	protected long[] baseline = null;

	/**
	 * Creates a new JVMSummaryProfiler
	 */
	public JVMSummaryProfiler() {
	}

	/**
	 * {@inheritDoc}
	 * @see org.openjdk.jmh.profile.Profiler#getDescription()
	 */
	@Override
	public String getDescription() {		
		return "JVM Stats Wot I Care About And When I Wants Em";
	}

	/**
	 * {@inheritDoc}
	 * @see org.openjdk.jmh.profile.InternalProfiler#beforeIteration(org.openjdk.jmh.infra.BenchmarkParams, org.openjdk.jmh.infra.IterationParams)
	 */
	@Override
	public void beforeIteration(final BenchmarkParams benchmarkParams, final IterationParams iterationParams) {
		baseline = getJVMStats();
	}

	/**
	 * {@inheritDoc}
	 * @see org.openjdk.jmh.profile.InternalProfiler#afterIteration(org.openjdk.jmh.infra.BenchmarkParams, org.openjdk.jmh.infra.IterationParams, org.openjdk.jmh.results.IterationResult)
	 */
	@Override
	public Collection<? extends Result> afterIteration(final BenchmarkParams benchmarkParams, final IterationParams iterationParams, final IterationResult result) {		
		System.out.println(getJVMStats(baseline, TimeUnit.SECONDS, SpaceUnit.MEGABYTES, benchmarkParams.getBenchmark()));
		return Collections.emptySet();
	}
	
	public static long[] getJVMStats() {
		final long[] countTime = new long[5];
		for(GarbageCollectorMXBean gc: collectors) {
			countTime[0] += gc.getCollectionCount();
			countTime[1] += gc.getCollectionTime();
		}
		countTime[2] = TX.getCurrentThreadCpuTime();
		countTime[3] = OS.getProcessCpuTime();
		countTime[4] = TX.getThreadAllocatedBytes(Thread.currentThread().getId());
		return countTime;
	}
	
	public static String getJVMStats(final long[] prior, TimeUnit cpuUnit, SpaceUnit memUnit, final String testName) {
		final long[] countTime = getJVMStats();
		final StringBuilder b = new StringBuilder("JVM Stats for [").append(testName).append("]");
		b.append("\n\tGC Collections:").append(countTime[0] - prior[0]);
		b.append("\n\tGC Time:").append(countTime[1] - prior[1]);
		b.append("\n\tThread CPU Time:").append(cpuUnit.convert(countTime[2] - prior[2], TimeUnit.NANOSECONDS));
		b.append("\n\tJVM CPU Time:").append(cpuUnit.convert(countTime[3] - prior[3], TimeUnit.NANOSECONDS));
		b.append("\n\tAllocated Bytes:").append((memUnit.fovert(countTime[4] - prior[4], SpaceUnit.BYTES)));
		return b.toString();		
	}
	

}
