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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.HeapChannelBufferFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heliosapm.utils.config.ConfigurationHelper;
import com.heliosapm.utils.io.NIOHelper;
import com.heliosapm.utils.time.SystemClock;
import com.heliosapm.utils.time.SystemClock.ElapsedTime;
import com.sun.management.OperatingSystemMXBean;

/**
 * <p>Title: JSONUnmarshalling</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.benchmarks.json.JSONUnmarshalling</code></p>
 * <p>Sample data generated using <a href="http://www.json-generator.com/">JSON Generator</a></p>
 * DIRECT:
 * STRING Test Complete: 22840000:	Completed 2.284E7 Loads in 225408 ms.  AvgPer: 0 ms/9 µs/9869 ns.,  GC: count:1545, time:2205  CPU:227
 * BUFFER Test Complete: 22840000:	Completed 2.284E7 Loads in 171382 ms.  AvgPer: 0 ms/7 µs/7504 ns.,  GC: count:969, time:1527  CPU:173
 * HEAP:
 * STRING Test Complete: 22840000:	Completed 2.284E7 Loads in 179946 ms.  AvgPer: 0 ms/7 µs/7879 ns.,  GC: count:1202, time:1723  CPU:181
 * BUFFER Test Complete: 22840000:	Completed 2.284E7 Loads in 169338 ms.  AvgPer: 0 ms/7 µs/7414 ns.,  GC: count:975, time:1525  CPU:171
 */

public class JSONUnmarshalling {
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	public static final int SAMPLE_SIZE = ConfigurationHelper.getIntSystemThenEnvProperty("sample.size", 1000000);
	public static final int INIT_THREADS = ConfigurationHelper.getIntSystemThenEnvProperty("init,threads", SAMPLE_SIZE/CORES);
	public static final int LOOPS = ConfigurationHelper.getIntSystemThenEnvProperty("loops", 20);
	
	private static final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
	private static final OperatingSystemMXBean OS =  (OperatingSystemMXBean)sun.management.ManagementFactoryHelper.getOperatingSystemMXBean();
	
	public static final Set<String> DATA = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
			"sample-1kb.json.gz", "sample-56kb.json.gz", "sample-118kb.json.gz", "sample-614kb.json.gz"
	)));
	
//	private static final ChannelBufferFactory bufferFactory = new DirectChannelBufferFactory();
	private static final ChannelBufferFactory bufferFactory = new HeapChannelBufferFactory();
	private static final Map<String, ChannelBuffer> DATA_BUFFERS = new HashMap<String, ChannelBuffer>(DATA.size());
	private static final Map<String, Integer> DATA_BUFFER_SIZES = new HashMap<String, Integer>(DATA.size());
	
	public static Charset UTF8 = Charset.forName("UTF8");
	
	static {
		for(String sample: DATA) {
			InputStream is = null;
			ReadableByteChannel rbc = null;
			GZIPInputStream gz = null;
			ByteBuffer b = null;
			try {
				is = JSONUnmarshalling.class.getClassLoader().getResourceAsStream("data/" + sample);
				final int size = is.available();
				log("[%s]: Size: %s", sample, size);
				b = ByteBuffer.allocateDirect(size*20);				
				gz = new GZIPInputStream(is);
				rbc = Channels.newChannel(gz);
				int readBytes = rbc.read(b);
				b.flip();
				final ChannelBuffer buff = ChannelBuffers.buffer(readBytes);
				buff.writeBytes(b);
				DATA_BUFFERS.put(sample, buff);
				DATA_BUFFER_SIZES.put(sample, readBytes);
				log("[%s]: Loaded: %s, cb: %s", sample, readBytes, buff.readableBytes());
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
		log("JSON Test");
		InputStream is = null;
		final int WARMUP = 20000;
		final int TEST = 20000;
		for(String sample: DATA) {
				try {
					final String jsonText = DATA_BUFFERS.get(sample).duplicate().toString(UTF8);
//					log("Loaded String from [%s], length: %s", sample, jsonText.length());
					Person[] p = parseToObject(jsonText, Person[].class);
					log("Parsed STRING [%s] to objects: %s", sample, p.length);
				} catch (Exception ex) {
					throw new RuntimeException("Failed to process string sample [" + sample + "]", ex);
				} finally {
					if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
				}
				try {
					Person[] p = parseToObject(DATA_BUFFERS.get(sample).duplicate(), Person[].class);
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
		long[] gc = getGCProc();
		ElapsedTime et = SystemClock.startClock();
		for(int x = 0; x < WARMUP; x++) {
			total += parseToObject(DATA_BUFFERS.get("sample-56kb.json.gz").duplicate().toString(UTF8), Person[].class).length;
		}
		long[] elapsed = getGCProc(gc);
		log("STRING Warmup Complete: %s:\t%s,  GC: count:%s, time:%s  CPU:%s", total, et.printAvg("Loads", total), elapsed[0], elapsed[1], TimeUnit.SECONDS.convert(elapsed[2], TimeUnit.NANOSECONDS));
		total = 0;
		System.gc();System.gc();
		gc = getGCProc();
		et = SystemClock.startClock();
		for(int x = 0; x < WARMUP; x++) {
			total += parseToObject(DATA_BUFFERS.get("sample-56kb.json.gz").duplicate(), Person[].class).length;
		}
		elapsed = getGCProc(gc);
		log("BUFFER Warmup Complete: %s:\t%s,  GC: count:%s, time:%s  CPU:%s", total, et.printAvg("Loads", total), elapsed[0], elapsed[1], TimeUnit.SECONDS.convert(elapsed[2], TimeUnit.NANOSECONDS));

		log("Starting Test");
		total = 0;
		System.gc();System.gc();
		gc = getGCProc();
		et = SystemClock.startClock();		
		for(String sample: DATA) {
			for(int x = 0; x < TEST; x++) {
				total += parseToObject(DATA_BUFFERS.get(sample).duplicate().toString(UTF8), Person[].class).length;
			}
		}
		elapsed = getGCProc(gc);
		log("STRING Test Complete: %s:\t%s,  GC: count:%s, time:%s  CPU:%s", total, et.printAvg("Loads", total), elapsed[0], elapsed[1], TimeUnit.SECONDS.convert(elapsed[2], TimeUnit.NANOSECONDS));
		total = 0;
		System.gc();System.gc();
		gc = getGCProc();
		et = SystemClock.startClock();		
		for(String sample: DATA) {
			for(int x = 0; x < TEST; x++) {
				total += parseToObject(DATA_BUFFERS.get(sample).duplicate(), Person[].class).length;
			}
		}
		elapsed = getGCProc(gc);
		log("BUFFER Test Complete: %s:\t%s,  GC: count:%s, time:%s  CPU:%s", total, et.printAvg("Loads", total), elapsed[0], elapsed[1], TimeUnit.SECONDS.convert(elapsed[2], TimeUnit.NANOSECONDS));
		

	}
	
	public static long[] getGCProc() {
		final long[] countTime = new long[3];
		for(GarbageCollectorMXBean gc: collectors) {
			countTime[0] += gc.getCollectionCount();
			countTime[1] += gc.getCollectionTime();
		}
		countTime[2] = OS.getProcessCpuTime();
		return countTime;
	}
	
	public static long[] getGCProc(final long[] prior) {
		final long[] countTime = getGCProc();
		return new long[] {countTime[0] - prior[0], countTime[1] - prior[1], countTime[2] - prior[2]};
	}
	
  public static void log(final Object fmt, final Object...args) {
  	System.out.println(String.format(fmt.toString(), args));
  }
  
  
	

}
