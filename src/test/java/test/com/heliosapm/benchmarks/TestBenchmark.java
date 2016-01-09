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
package test.com.heliosapm.benchmarks;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import com.heliosapm.benchmarks.json.JVMSummaryProfiler;

/**
 * <p>Title: TestBenchmark</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.benchmarks.TestBenchmark</code></p>
 */

public class TestBenchmark {
	
	/**
	 * Runs the JMX Benchmark
	 * @throws Exception thrown on any error
	 */
	@SuppressWarnings("static-method")
	@Test public void 
	launchBenchmark() throws Exception {

		Options opt = new OptionsBuilder()
		.include(".*")
		// Set the following options as needed
		.mode (Mode.AverageTime)
		.timeUnit(TimeUnit.MICROSECONDS)
		.warmupTime(TimeValue.seconds(1))
		.warmupIterations(1)
		.measurementTime(TimeValue.seconds(1))
		.measurementIterations(2)
		.threads(3)
		.forks(1)
		.shouldFailOnError(true)
		.shouldDoGC(true)
//		.addProfiler(JVMSummaryProfiler.class)
//		.addProfiler(HotspotMemoryProfiler.class)
//		.addProfiler(LinuxPerfAsmProfiler.class)
		
		//.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
		//.addProfiler(WinPerfAsmProfiler.class)
		.build();

		new Runner(opt).run();
	}



}
