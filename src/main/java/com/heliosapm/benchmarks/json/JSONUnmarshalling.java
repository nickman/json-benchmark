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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
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
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
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
				ex.printStackTrace(System.err);
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
	
  
  public static final ChannelBuffer serializeToBuffer(final ChannelBufferFactory bfactory, final Object object) {
	    if (object == null)
	      throw new IllegalArgumentException("Object was null");
	    OutputStream os = null;
	    Writer wos = null;
	    try {
//	    	final ChannelBuffer b = bfactory.getBuffer(1024);
	    	final ChannelBuffer b = ChannelBuffers.dynamicBuffer(1024, bfactory);
	    	os = new ChannelBufferOutputStream(b);
	    	wos = new OutputStreamWriter(os, UTF8);
	    	jsonMapper.writeValue(wos, object);
	        return b;
	    } catch (Exception e) {
	      throw new RuntimeException(e);
	    } finally {
	    	if(wos!=null) try { wos.close(); } catch (Exception x) {/* No Op */}
	    	if(os!=null) try { os.close(); } catch (Exception x) {/* No Op */}
	    }
	  }
  
  public static final String serializeToString(final Object object) {
	    if (object == null)
	      throw new IllegalArgumentException("Object was null");
	    try {
	    	return jsonMapper.writeValueAsString(object);
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
    InputStream i = null;
    Reader r = null;
    try {
    	i = new ChannelBufferInputStream(json); 
    	r = new InputStreamReader(i);
      return jsonMapper.readValue(r, pojo);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
    	if(r!=null) try { r.close(); } catch (Exception x) {/* No Op */}
    	if(i!=null) try { i.close(); } catch (Exception x) {/* No Op */}
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
		final Map<String, ChannelBuffer> bufferMap = DIRECT_DATA_BUFFERS;
		log("JSON Test");
		InputStream is = null;
		for(String sample: DATA) {
				try {
					final String jsonText = bufferMap.get(sample).duplicate().toString(UTF8);
					Person[] p = parseToObject(jsonText, Person[].class);
					log("Parsed STRING [%s] to objects: %s", sample, p.length);
					p = parseToObject(bufferMap.get(sample).duplicate(), Person[].class);
					log("Parsed BUFFER [%s] to objects: %s", sample, p.length);
					String s = serializeToString(p);
					log("Serialized to STRING [%s], size: %s", sample, s.length());
					ChannelBuffer c = serializeToBuffer(heapFactory, p);
					log("Serialized to Heap Buffer [%s], size: %s", sample, c.readableBytes());
					c = serializeToBuffer(directFactory, p);
					log("Serialized to Direct Buffer [%s], size: %s", sample, c.readableBytes());					
				} catch (Exception ex) {
					throw new RuntimeException("Failed to process string sample [" + sample + "]", ex);
				} finally {
					if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
				}
		}
	}
	
	
  public static void log(final Object fmt, final Object...args) {
  	System.out.println(String.format(fmt.toString(), args));
  }
  
  
  public static Person[] deserPersons(final ChannelBuffer buff) {
	  InputStream is = null;
	  Reader ros = null;
	  try {
		  is = new ChannelBufferInputStream(buff);
		  ros = new InputStreamReader(is, UTF8);
		  return jsonMapper.readValue(ros, Person[].class);
	  } catch (Exception ex) {
		  throw new RuntimeException(ex);
	  } finally {
		  if(ros!=null) try { ros.close(); } catch (Exception x) {/* No Op */}
		  if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
		  buff.resetReaderIndex();
	  }
  }
  
  
   public static abstract class Sample {
		@Setup(Level.Trial)
		public void setup() {
			System.gc();
			totalParsed = 0;
		}
	    @TearDown(Level.Trial)
	    public void clear() {
	    	/* No Op */
	    }
   }
  
	@State(Scope.Group)
	public static class Heap56Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-56kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = heapFactory;
	}
	
	@State(Scope.Group)
	public static class Direct56Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-56kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = directFactory;
	}
	
	@State(Scope.Group)
	public static class Heap118Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-118kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = heapFactory;
	}
	
	@State(Scope.Group)
	public static class Direct118Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-118kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = directFactory;
	}
	
	@State(Scope.Group)
	public static class Heap614Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-614kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = heapFactory;
	}
	
	@State(Scope.Group)
	public static class Direct614Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-614kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = directFactory;
	}

	@State(Scope.Group)
	public static class Heap1Kb extends Sample {
		final ChannelBuffer sampleBuff = HEAP_DATA_BUFFERS.get("sample-1kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = heapFactory;
	}
	
	@State(Scope.Group)
	public static class Direct1Kb extends Sample {
		final ChannelBuffer sampleBuff = DIRECT_DATA_BUFFERS.get("sample-1kb.json.gz");
		final Person[] pojos = deserPersons(sampleBuff);
		final ChannelBufferFactory cbf = directFactory;
	}
	
  
  
  	private static long[] jvmStats = null;
  	private static int totalParsed = 0;
  
	
    
    public void stringReadTest(final ChannelBuffer buffer, final Blackhole blackHole) {
//    	log("\n\t====================\n\tStrRead Thread [%s][%s]\n\t====================", Thread.currentThread().getName(), Thread.currentThread().getThreadGroup());
			for(int x = 0; x < loopsPerOp; x++) {
				blackHole.consume(parseToObject(buffer.toString(UTF8), Person[].class).length);
				buffer.resetReaderIndex();
			}
    }
    
    public void bufferReadTest(final ChannelBuffer buffer, final Blackhole blackHole) {
//    	log("\n\t====================\n\tBuffRead Thread [%s][%s]\n\t====================", Thread.currentThread().getName(), Thread.currentThread().getThreadGroup());
    	for(int x = 0; x < loopsPerOp; x++) {
				blackHole.consume(parseToObject(buffer, Person[].class).length);
				buffer.resetReaderIndex();
			}  
    }
    
    public void stringWriteTest(final Person[] people, final ChannelBufferFactory factory, final Blackhole blackHole) {
			for(int x = 0; x < loopsPerOp; x++) {
				blackHole.consume(serializeToString(people));				
			}
    }
    
    public void bufferWriteTest(final Person[] people, final ChannelBufferFactory factory, final Blackhole blackHole) {
			for(int x = 0; x < loopsPerOp; x++) {
				blackHole.consume(serializeToBuffer(factory, people));				
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

    @Group("DirectStringWrite1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void DirectStringWrite1Kb(final Direct1Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectStringWrite56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void DirectStringWrite56Kb(final Direct56Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectStringWrite118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void DirectStringWrite118Kb(final Direct118Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectStringWrite614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void DirectStringWrite614Kb(final Direct614Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectBufferWrite1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void DirectBufferWrite1Kb(final Direct1Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectBufferWrite56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void DirectBufferWrite56Kb(final Direct56Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectBufferWrite118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void DirectBufferWrite118Kb(final Direct118Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("DirectBufferWrite614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void DirectBufferWrite614Kb(final Direct614Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapStringWrite1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void HeapStringWrite1Kb(final Heap1Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapStringWrite56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void HeapStringWrite56Kb(final Heap56Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapStringWrite118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void HeapStringWrite118Kb(final Heap118Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapStringWrite614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void HeapStringWrite614Kb(final Heap614Kb sample, final Blackhole blackhole) {
        stringWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapBufferWrite1Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 1)
    @Benchmark
    public void HeapBufferWrite1Kb(final Heap1Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapBufferWrite56Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 82)
    @Benchmark
    public void HeapBufferWrite56Kb(final Heap56Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapBufferWrite118Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 171)
    @Benchmark
    public void HeapBufferWrite118Kb(final Heap118Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

    @Group("HeapBufferWrite614Kb")    
    @Fork(1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @GroupThreads(3)
    @OperationsPerInvocation(loopsPerOp * 888)
    @Benchmark
    public void HeapBufferWrite614Kb(final Heap614Kb sample, final Blackhole blackhole) {
        bufferWriteTest(sample.pojos, sample.cbf, blackhole);
    }

	

}
