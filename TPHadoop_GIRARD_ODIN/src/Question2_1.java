
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import com.google.common.collect.MinMaxPriorityQueue;

public class Question2_1 {

	public static enum CUSTOM_COUNTER {
		COUNTRY_NOT_FOUND; 
	}

	public static class MyMapper extends Mapper<LongWritable, Text, Text, Text> {
		@Override
		protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			String[] data = value.toString().split("\t");

			Country c = Country.getCountryAt(Double.parseDouble(URLDecoder.decode(data[11],"UTF-8")), Double.parseDouble(URLDecoder.decode(data[10],"UTF-8")));
			if (c != null){
				String tags = data[8]+","+data[9];

				for (String t : tags.split(",")){
					context.write(new Text(c.toString()), new Text(URLDecoder.decode(t,"UTF-8").toString()));
				}
			}
			else
				context.getCounter(CUSTOM_COUNTER.COUNTRY_NOT_FOUND).increment(1);
		}
	}

	public static class StringAndInt implements Comparable<StringAndInt> {

		public String tag;
		public int occ;

		public StringAndInt(String tag, int occ) {
			super();
			this.tag = tag;
			this.occ = occ;
		}

		@Override
		public int compareTo(StringAndInt arg0) {
			if (occ < arg0.occ)
				return -1;
			else if (occ > arg0.occ)
				return 1;
			else
				return 0;
		}

	}

	public static class MyReducer extends Reducer<Text, Text, Text, Text> {
		@Override
		protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
			Map<String, Integer> map = new HashMap<String, Integer>();
			for (Text value : values) {
				if (map.containsKey(value.toString()))
					map.put(value.toString(), map.get(value.toString())+1);
				else
					map.put(value.toString(), 0);
			}

			MinMaxPriorityQueue<StringAndInt> list = MinMaxPriorityQueue.create();
			for (String k : map.keySet())
				list.add(new StringAndInt(k, map.get(k)));

			for (int i=0; i < context.getConfiguration().getInt("K", 1); i++){
				StringAndInt toAdd = list.pollLast();
				context.write(key, new Text(toAdd.tag+" "+toAdd.occ));
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		String input = otherArgs[0];
		String output = otherArgs[1];
		conf.setInt("K", Integer.parseInt(otherArgs[2]));

		Job job = Job.getInstance(conf, "Question2_1");
		job.setJarByClass(Question2_1.class);

		job.setMapperClass(MyMapper.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);

		job.setReducerClass(MyReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job, new Path(input));
		job.setInputFormatClass(TextInputFormat.class);

		FileOutputFormat.setOutputPath(job, new Path(output));
		job.setOutputFormatClass(TextOutputFormat.class);

		job.waitForCompletion(true);

		Counters counters = job.getCounters();
		Counter c1 = counters.findCounter(CUSTOM_COUNTER.COUNTRY_NOT_FOUND);
		System.out.println(c1.getDisplayName()+":"+c1.getValue());
	}
}