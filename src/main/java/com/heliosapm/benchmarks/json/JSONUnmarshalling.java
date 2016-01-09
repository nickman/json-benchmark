/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.benchmarks.json;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.DirectChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heliosapm.utils.config.ConfigurationHelper;
import com.heliosapm.utils.enums.SpaceUnit;
import com.heliosapm.utils.io.NIOHelper;
import com.heliosapm.utils.time.SystemClock;
import com.heliosapm.utils.time.SystemClock.ElapsedTime;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;

/**
 * <p>Title: JSONUnmarshalling</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.benchmarks.json.JSONUnmarshalling</code></p>
 * <p>Sample data generated using <a href="http://www.json-generator.com/">JSON Generator</a></p>
 * 
 * DIRECT:
STRING Test Complete: 22840000:	Completed 2.284E7 Loads in 205066 ms.  AvgPer: 0 ms/8 µs/8978 ns.
Stats:GC Collections:661, GC Time:1450, Thread CPU Time:203, JVM CPU Time:212, Allocated Bytes:168,191MB
================================================================================================
BUFFER Test Complete: 22840000:	Completed 2.284E7 Loads in 145682 ms.  AvgPer: 0 ms/6 µs/6378 ns.
Stats:GC Collections:282, GC Time:927, Thread CPU Time:144, JVM CPU Time:151, Allocated Bytes:82,500MB

 * HEAP:
STRING Test Complete: 22840000:	Completed 2.284E7 Loads in 155519 ms.  AvgPer: 0 ms/6 µs/6809 ns.
Stats:GC Collections:351, GC Time:880, Thread CPU Time:154, JVM CPU Time:160, Allocated Bytes:168,189MB
================================================================================================
BUFFER Test Complete: 22840000:	Completed 2.284E7 Loads in 145653 ms.  AvgPer: 0 ms/6 µs/6377 ns.
Stats:GC Collections:413, GC Time:877, Thread CPU Time:144, JVM CPU Time:150, Allocated Bytes:82,329MB
 */

public class JSONUnmarshalling {
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	public static final int SAMPLE_SIZE = ConfigurationHelper.getIntSystemThenEnvProperty("sample.size", 1000000);
	public static final int INIT_THREADS = ConfigurationHelper.getIntSystemThenEnvProperty("init,threads", SAMPLE_SIZE/CORES);
	public static final int LOOPS = ConfigurationHelper.getIntSystemThenEnvProperty("loops", 20);
	
	private static final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
	private static final OperatingSystemMXBean OS =  (OperatingSystemMXBean)sun.management.ManagementFactoryHelper.getOperatingSystemMXBean();
	private static final ThreadMXBean TX =  (ThreadMXBean)sun.management.ManagementFactoryHelper.getThreadMXBean();
	
	public static final int loopsPerOp = 1000;
	public static final TimeUnit outputTimeUnit = TimeUnit.MILLISECONDS;
	
	//@OutputTimeUnit(TimeUnit.MILLISECONDS)
	
	public static final Set<String> DATA = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
			"sample-1kb.json.gz", "sample-56kb.json.gz", "sample-118kb.json.gz", "sample-614kb.json.gz"
	)));
	
	private static final ChannelBufferFactory directFactory = new DirectChannelBufferFactory();
	private static final ChannelBufferFactory heapFactory = new HeapChannelBufferFactory();
	private static final Map<String, ChannelBuffer> DIRECT_DATA_BUFFERS = new HashMap<String, ChannelBuffer>(DATA.size());
	private static final Map<String, ChannelBuffer> HEAP_DATA_BUFFERS = new HashMap<String, ChannelBuffer>(DATA.size());
	private static TimeUnit cpuUnit = TimeUnit.SECONDS;
	private static SpaceUnit memUnit = SpaceUnit.MEGABYTES;
	
	
	public static Charset UTF8 = Charset.forName("UTF8");
	
	static {
		TX.setThreadAllocatedMemoryEnabled(true);
		TX.setThreadCpuTimeEnabled(true);
		for(String sample: DATA) {
			InputStream is = null;
			ReadableByteChannel rbc = null;
			GZIPInputStream gz = null;
			ByteBuffer b = null;
			try {
				is = JSONUnmarshalling.class.getClassLoader().getResourceAsStream("data/" + sample);
				final int size = is.available();
//				log("[%s]: Size: %s", sample, size);
				b = ByteBuffer.allocateDirect(size*20);				
				gz = new GZIPInputStream(is);
				rbc = Channels.newChannel(gz);
				int readBytes = rbc.read(b);
				b.flip();
				ChannelBuffer buff = directFactory.getBuffer(readBytes);
				buff.writeBytes(b);
				DIRECT_DATA_BUFFERS.put(sample, buff);
				b.rewind();
				buff = heapFactory.getBuffer(readBytes);
				buff.writeBytes(b);
				HEAP_DATA_BUFFERS.put(sample, buff);
				
				//final byte[] jbytes = URLHelper.getBytesFromURL(JSONUnmarshalling.class.getClassLoader().getResource("data/" + sample));
			} catch (Exception ex) {
				throw new RuntimeException("Failed to load sample data [" + sample + "]", ex);
			} finally {
				if(gz!=null) try { gz.close(); } catch (Exception x) {/* No Op */}
				if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
				if(rbc!=null) try { rbc.close(); } catch (Exception x) {/* No Op */}
				if(b!=null) try { NIOHelper.clean(b); } catch (Exception x) {/* No Op */}
			}
		}
	}

	private static final ObjectMapper jsonMapper = new ObjectMapper();
	
  /**
   * Deserializes a JSON formatted byte array to a specific class type
   * <b>Note:</b> If you get mapping exceptions you may need to provide a 
   * TypeReference
   * @param json The byte array to deserialize
   * @param pojo The class type of the object used for deserialization
   * @return An object of the {@code pojo} type
   * @throws IllegalArgumentException if the data or class was null or parsing 
   * failed
   */
  public static final <T> T parseToObject(final byte[] json,
      final Class<T> pojo) {
    if (json == null)
      throw new IllegalArgumentException("Incoming data was null");
    if (pojo == null)
      throw new IllegalArgumentException("Missing class type");
    try {
      return jsonMapper.readValue(json, pojo);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }	
  
  /**
   * Deserializes a JSON formatted string to a specific class type
   * <b>Note:</b> If you get mapping exceptions you may need to provide a 
   * TypeReference
   * @param json The string to deserialize
   * @param pojo The class type of the object used for deserialization
   * @return An object of the {@code pojo} type
   * @throws IllegalArgumentException if the data or class was null or parsing 
   * failed
   */
  public static final <T> T parseToObject(final String json, final Class<T> pojo) {
    if (json == null || json.isEmpty())
      throw new IllegalArgumentException("Incoming data was null or empty");
    if (pojo == null)
      throw new IllegalArgumentException("Missing class type");
    
    try {
      return jsonMapper.readValue(json, pojo);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }  
  
  /**
   * Deserializes a JSON formatted string to a specific class type
   * <b>Note:</b> If you get mapping exceptions you may need to provide a 
   * TypeReference
   * @param json The string to deserialize
   * @param pojo The class type of the object used for deserialization
   * @return An object of the {@code pojo} type
   * @throws IllegalArgumentException if the data or class was null or parsing 
   * failed
   */
  public static final <T> T parseToObject(final ChannelBuffer json, final Class<T> pojo) {
    if (json == null || json.readableBytes()<2)
      throw new IllegalArgumentException("Incoming data was null or empty");
    if (pojo == null)
      throw new IllegalArgumentException("Missing class type");
    Reader r = null;
    try {
    	r = new InputStreamReader(new ChannelBufferInputStream(json));
      return jsonMapper.readValue(r, pojo);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
    	if(r!=null) try { r.close(); } catch (Exception x) {/* No Op */}
    }
  }    
	
	/**
	 * Creates a new JSONUnmarshalling
	 */
	public JSONUnmarshalling() {
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final Map<String, ChannelBuffer> bufferMap = HEAP_DATA_BUFFERS;
		log("JSON Test");
		InputStream is = null;
		final int WARMUP = 20000;
		final int TEST = 20000;
		for(String sample: DATA) {
				try {
					final String jsonText = bufferMap.get(sample).duplicate().toString(UTF8);
//					log("Loaded String from [%s], length: %s", sample, jsonText.length());
					Person[] p = parseToObject(jsonText, Person[].class);
					log("Parsed STRING [%s] to objects: %s", sample, p.length);
				} catch (Exception ex) {
					throw new RuntimeException("Failed to process string sample [" + sample + "]", ex);
				} finally {
					if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
				}
				try {
					Person[] p = parseToObject(bufferMap.get(sample).duplicate(), Person[].class);
					log("Parsed BUFFER [%s] to objects: %s", sample, p.length);
				} catch (Exception ex) {
					throw new RuntimeException("Failed to process buffer sample [" + sample + "]", ex);
				} finally {
					if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
				}
		}
		log("Starting Warmup....");
		long total = 0;
		System.gc();System.gc();
		long[] initStats = getJVMStats();
		ElapsedTime et = SystemClock.startClock();
		for(int x = 0; x < WARMUP; x++) {
			total += parseToObject(bufferMap.get("sample-56kb.json.gz").duplicate().toString(UTF8), Person[].class).length;
		}
		String stats = getJVMStats(initStats, cpuUnit, memUnit);
		log("STRING Warmup Complete: %s:\t%s\n%s", total, et.printAvg("Loads", total), stats);
		total = 0;
		System.gc();System.gc();
		initStats = getJVMStats();
		et = SystemClock.startClock();
		for(int x = 0; x < WARMUP; x++) {
			total += parseToObject(bufferMap.get("sample-56kb.json.gz").duplicate(), Person[].class).length;
		}
		stats = getJVMStats(initStats, cpuUnit, memUnit);
		log("BUFFER Warmup Complete: %s:\t%s\n%s", total, et.printAvg("Loads", total), stats);
		log("================================================================================================");
		log("================================================================================================");		
		log("Starting Test");
		total = 0;
		System.gc();System.gc();
		initStats = getJVMStats();
		et = SystemClock.startClock();		
		for(String sample: DATA) {
			for(int x = 0; x < TEST; x++) {
				total += parseToObject(bufferMap.get(sample).duplicate().toString(UTF8), Person[].class).length;
			}
		}
		stats = getJVMStats(initStats, cpuUnit, memUnit);
		log("STRING Test Complete: %s:\t%s\n%s", total, et.printAvg("Loads", total), stats);
		log("================================================================================================");
		total = 0;
		System.gc();System.gc();
		initStats = getJVMStats();
		et = SystemClock.startClock();		
		for(String sample: DATA) {
			for(int x = 0; x < TEST; x++) {
				total += parseToObject(bufferMap.get(sample).duplicate(), Person[].class).length;
			}
		}
		stats = getJVMStats(initStats, cpuUnit, memUnit);
		log("BUFFER Test Complete: %s:\t%s\n%s", total, et.printAvg("Loads", total), stats);
		

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
	
	public static String getJVMStats(final long[] prior, TimeUnit cpuUnit, SpaceUnit memUnit) {
		final long[] countTime = getJVMStats();
		final StringBuilder b = new StringBuilder("JVM Stats:");
		b.append("\n\tGC Collections:").append(countTime[0] - prior[0]);
		b.append("\n\tGC Time:").append(countTime[1] - prior[1]);
		b.append("\n\tThread CPU Time:").append(cpuUnit.convert(countTime[2] - prior[2], TimeUnit.NANOSECONDS));
		b.append("\n\tJVM CPU Time:").append(cpuUnit.convert(countTime[3] - prior[3], TimeUnit.NANOSECONDS));
		b.append("\n\tAllocated Bytes:").append((memUnit.fovert(countTime[4] - prior[4], SpaceUnit.BYTES)));
		return b.toString();		
	}
	
  public static void log(final Object fmt, final Object...args) {
  	System.out.println(String.format(fmt.toString(), args));
  }
  
  private static final Map<String, String> allJVMStats = new LinkedHashMap<String, String>();
  
  static {
	  Runtime.getRuntime().addShutdownHook(new Thread(){
		  public void run() {
			  log("\n\n\tJVM STATS SUMMARY");
			  for(Map.Entry<String, String> entry: allJVMStats.entrySet()) {
				  log("Test [%s]\n%s", entry.getKey(), entry.getValue());
			  }
		  }
	  });
  }
  
   public static abstract class Sample {
		@Setup(Level.Trial)
		public void setup() {
			System.gc(); System.gc();
			jvmStats = getJVMStats();
			totalParsed = 0;
		}
	    @TearDown(Level.Trial)
	    public void clear() {
	    	final String stats = getJVMStats(jvmStats, cpuUnit, memUnit);
	    	allJVMStats.put(getClass().getSimpleName(), stats);
	    	log("[JVM Stats:" + getClass().getSimpleName() + "]:" + stats);
	    }
		
	   
   }
  
	@State(Scope.Group)
	public static class Heap56Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-56kb.json.gz");
	}
	
	@State(Scope.Group)
	public static class Direct56Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-56kb.json.gz");
	}
	
	@State(Scope.Group)
	public static class Heap118Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-118kb.json.gz");
	}
	
	@State(Scope.Group)
	public static class Direct118Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-118kb.json.gz");
	}
	
	@State(Scope.Group)
	public static class Heap614Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-614kb.json.gz");
	}
	
	@State(Scope.Group)
	public static class Direct614Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-614kb.json.gz");
	}

	@State(Scope.Group)
	public static class Heap1Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-1kb.json.gz");
	}
	
	@State(Scope.Group)
	public static class Direct1Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-1kb.json.gz");
	}
	
  
  
  	private static long[] jvmStats = null;
  	private static int totalParsed = 0;
  
	
    
    public void stringReadTest(final ChannelBuffer buffer, final Blackhole blackHole) {
//    	log("\n\t===================\n\tStarting StringRead Test: directBuff:[%s], sample:[%s]\n\t===================", buffer.isDirect(), sampleSet);
		for(int x = 0; x < loopsPerOp; x++) {
			blackHole.consume(parseToObject(buffer.toString(UTF8), Person[].class).length);
			buffer.resetReaderIndex();
		}
    }
    
    public void bufferReadTest(final ChannelBuffer buffer, final Blackhole blackHole) {
//    	log("\n\t===================\n\tStarting StringRead Test: directBuff:[%s], sample:[%s]\n\t===================", buffer.isDirect(), sampleSet);
		for(int x = 0; x < loopsPerOp; x++) {
			blackHole.consume(parseToObject(buffer, Person[].class).length);
			buffer.resetReaderIndex();
		}  
    }
    
    @Group("DirectStringRead1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void DirectStringRead1Kb(final Direct1Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("DirectBufferRead1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void DirectBufferRead1Kb(final Direct1Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapStringRead1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void HeapStringRead1Kb(final Heap1Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapBufferRead1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void HeapBufferRead1Kb(final Heap1Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    
    @Group("DirectStringRead56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void DirectStringRead56Kb(final Direct56Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("DirectBufferRead56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void DirectBufferRead56Kb(final Direct56Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapStringRead56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void HeapStringRead56Kb(final Heap56Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapBufferRead56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void HeapBufferRead56Kb(final Heap56Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("DirectStringRead118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void DirectStringRead118Kb(final Direct118Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("DirectBufferRead118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void DirectBufferRead118Kb(final Direct118Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapStringRead118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void HeapStringRead118Kb(final Heap118Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapBufferRead118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void HeapBufferRead118Kb(final Heap118Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("DirectStringRead614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void DirectStringRead614Kb(final Direct614Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("DirectBufferRead614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void DirectBufferRead614Kb(final Direct614Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapStringRead614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void HeapStringRead614Kb(final Heap614Kb sample, final Blackhole blackhole) {
    	stringReadTest(sample.sampleBuff.duplicate(), blackhole);
    }
    
    @Group("HeapBufferRead614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void HeapBufferRead614Kb(final Heap614Kb sample, final Blackhole blackhole) {
    	bufferReadTest(sample.sampleBuff.duplicate(), blackhole);
    }

	

}
