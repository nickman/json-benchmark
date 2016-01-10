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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.profile.ProfilerResult;
import org.openjdk.jmh.results.AggregationPolicy;
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
	
	private static final Map<TimeUnit, String> TIMEUNITSYMBOLS;
	
	static {
		final Map<TimeUnit, String> tmp = new EnumMap<TimeUnit, String>(TimeUnit.class);
		tmp.put(TimeUnit.NANOSECONDS, "ns");
		tmp.put(TimeUnit.MICROSECONDS, "\u00B5"+"s");
		tmp.put(TimeUnit.MILLISECONDS, "ms");
		tmp.put(TimeUnit.SECONDS, "s");
		tmp.put(TimeUnit.MINUTES, "m");
		tmp.put(TimeUnit.HOURS, "h");
		tmp.put(TimeUnit.DAYS, "d");
		TIMEUNITSYMBOLS = Collections.unmodifiableMap(tmp);
	}
	
	
	protected long[] baseline = null;
	protected final ThreadGroup threadGroup;
	protected final List<Thread> workerThreads = new CopyOnWriteArrayList<Thread>();

	/**
	 * Creates a new JVMSummaryProfiler
	 */
	public JVMSummaryProfiler() {
		threadGroup = Thread.currentThread().getThreadGroup();
//		log("\n\t====================\n\tProf Thread [%s][%s]\n\t====================", Thread.currentThread().getName(), Thread.currentThread().getThreadGroup());
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
		final Collection<Result> results = new ArrayList<Result>(5);
//		benchmarkParams.getParam("")
		final int tcount = benchmarkParams.getThreads();
		final Thread[] threads = new Thread[tcount+1];
		threadGroup.enumerate(threads, false);
		final String bname = benchmarkParams.getBenchmark();
		for(Thread t: threads) {
			if(t.getName().startsWith(bname)) {
				workerThreads.add(t);				
			}
		}
		getJVMStats(baseline, TimeUnit.MILLISECONDS, benchmarkParams.getBenchmark(), results);
//		log("Test: [%s], TG Threads: %s", benchmarkParams.getBenchmark(), Arrays.toString(threads));		
		return results;
	}
	
	public long[] getJVMStats() {
		final long[] countTime = new long[5];
		for(GarbageCollectorMXBean gc: collectors) {
			countTime[0] += gc.getCollectionCount();
			countTime[1] += gc.getCollectionTime();
		}
		countTime[3] = OS.getProcessCpuTime();
		final long[] tstats = collectThreadStats();
		countTime[2] = tstats[0];		
		countTime[4] = tstats[1];
		return countTime;
	}
	
	public void getJVMStats(final long[] prior, final TimeUnit cpuUnit, final String testName, final Collection<Result> results) {
		final long[] countTime = getJVMStats();
		final long[] deltas = new long[]{
				countTime[0] - prior[0],
				countTime[1] - prior[1],
				countTime[2] - prior[2],
				countTime[3] - prior[3],
				countTime[4] - prior[4],
		};
		results.add(new ProfilerResult("GC-Collections", deltas[0], "GC Collections", AggregationPolicy.AVG));
		results.add(new ProfilerResult("GC-Time", deltas[1], "GC Time", AggregationPolicy.AVG));
		results.add(new ProfilerResult("ThreadCPU", cpuUnit.convert(deltas[2], TimeUnit.NANOSECONDS), "ThreadCPU " + TIMEUNITSYMBOLS.get(cpuUnit), AggregationPolicy.AVG));
		results.add(new ProfilerResult("JVMCPU", cpuUnit.convert(deltas[3], TimeUnit.NANOSECONDS), "JVMCPU " + TIMEUNITSYMBOLS.get(cpuUnit), AggregationPolicy.AVG));
		final SpaceUnit su = SpaceUnit.KILOBYTES; //.pickUnit(deltas[4]);
		results.add(new ProfilerResult("MemAlloc", su.dconvert(deltas[4], SpaceUnit.BYTES), "Memory Allocated " + su.symbol(), AggregationPolicy.AVG));

//		final StringBuilder b = new StringBuilder("JVM Stats for [").append(testName).append("]");
//		
//		b.append("\n\tGC Collections:").append(deltas[0]);
//		b.append("\n\tGC Time:").append(deltas[1] - prior[1]);
//		b.append("\n\tThread CPU Time:").append(cpuUnit.convert(deltas[2], TimeUnit.NANOSECONDS));
//		b.append("\n\tJVM CPU Time:").append(cpuUnit.convert(deltas[3], TimeUnit.NANOSECONDS));
//		b.append("\n\tAllocated Bytes:").append((su.fovert(deltas[4], SpaceUnit.BYTES)));
//		return b.toString();		
	}
	
	protected long[] collectThreadStats() {
		final long[] snapshot = new long[2];
		for(Thread t: workerThreads) {
			final long id = t.getId();
			snapshot[0] = TX.getThreadCpuTime(id);
			snapshot[1] = TX.getThreadAllocatedBytes(id);
		}
		return snapshot;
	}
	
//  public static void log(final Object fmt, final Object...args) {
//  	System.out.println(String.format(fmt.toString(), args));
//  }
	

}
