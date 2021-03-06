package com.kafka.firstProject.Twitterproject;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;

public class TwitterProducer {

	Logger logger = LoggerFactory.getLogger(TwitterProducer.class.getName());

	String topic = "twitter_topics";
	String key = "null";

	 String consumerKey = "";
	 String consumerSecret = "";
	 String token = "";
	 String secret = "";

	public TwitterProducer() {

	}

	public static void main(String[] args) {

		new TwitterProducer().run();
	}

	public void run() {
		logger.info("Setup");

		/**
		 * Set up your blocking queues: Be sure to size these properly based on expected
		 * TPS of your stream
		 */
		BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(1000);

		// create a twitter client
		Client client = createTwitterClient(msgQueue);
		// Attempts to establish a connection.
		client.connect();

		// create a kafka producer
		KafkaProducer<String, String> producer = createKafkaProducer();

		// add a shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("stopping application");
			logger.info("shutting down client from twitter...");
			client.stop();
			logger.info("closing producer...");
			producer.close();
			logger.info("done");

		}));

		// loop to send tweets to kafka
		// on a different thread, or multiple different threads....
		while (!client.isDone()) {
			String msg = null;
			try {
				msg = msgQueue.poll(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
				client.stop();
			}
			if (msg != null) {
				logger.info(msg);
				producer.send(new ProducerRecord<String, String>(topic, key, msg), new Callback() {

					public void onCompletion(RecordMetadata metadata, Exception exception) {
						if (exception != null) {
							logger.error("Something get wrong", exception);
						}

					}
				});
			}

		}
		logger.info("End of applications");
	}

	public Client createTwitterClient(BlockingQueue<String> msgQueue) {

		Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
		StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();

		List<String> terms = Lists.newArrayList("bitcoin");
		hosebirdEndpoint.trackTerms(terms);

		// These secrets should be read from a config file
		Authentication hosebirdAuth = new OAuth1(consumerKey,consumerSecret,token,secret);

		// Creating a client

		ClientBuilder builder = new ClientBuilder().name("Hosebird-Client-01") // optional: mainly for the logs
				.hosts(hosebirdHosts).authentication(hosebirdAuth).endpoint(hosebirdEndpoint)
				.processor(new StringDelimitedProcessor(msgQueue));

		Client hosebirdClient = builder.build();
		return hosebirdClient;

	}

	public KafkaProducer<String, String> createKafkaProducer() {
		String bootStrapServers = "127.0.0.1:9092";
		// Create Producer Property
		Properties properties = new Properties();
		properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServers);
		properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

		// create Safe producer
		properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
		properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
		properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
		properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

		// create high throughput producer (at the expense of a bit of latency and CPU
		// Usage)
		properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024)); // 32kb
		properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
		properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

		// Create the producer
		KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);

		return producer;
	}

}
