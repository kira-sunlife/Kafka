package com.read.xml.kafka_consumer_elasticSearch;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParser;

public class ElasticSearchConsumer {
	public static RestHighLevelClient createClient() {

		// https://3rhp89h66g:n45ccj322a@twitter-project-5007597132.ap-southeast-2.bonsaisearch.net:443

		String hostName = "twitter-project-5007597132.ap-southeast-2.bonsaisearch.net";
		String userName = "3rhp89h66g";
		String password = "n45ccj322a";

		// Only for Cloud ElasticSearch
		final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

		RestClientBuilder builder = RestClient.builder(new HttpHost(hostName, 443, "https"))
				.setHttpClientConfigCallback(new HttpClientConfigCallback() {
					public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
						return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
					}
				});

		RestHighLevelClient client = new RestHighLevelClient(builder);
		return client;

	}

	public static KafkaConsumer<String, String> createConsumer(String topic) {
		// Logger logger = LoggerFactory.getLogger(ConsumerClass.class.getName());

		Properties properties = new Properties();

		String bootStrapServer = "127.0.0.1:9092";
		String groupId = "kafka-demo-xmlProject";
//		String topic = "twitter_topics";

		properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServer);
		properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // earliest/latest/none
		properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // disable auto commit of offsets
		properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "200");

		// create Consumer
		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(properties);

		// Subscribe consumer to our topic(s)
		consumer.subscribe(Arrays.asList(topic));

		// for many topic
		// consumer.subscribe(Arrays.asList("first_topic" , "Second_topic"));

		return consumer;

	}

	private static JsonParser jsonParser = new JsonParser();

	private static String extractIdFromTweets(String tweetJson) {
		// gson library

		return jsonParser.parse(tweetJson).getAsJsonObject().get("id_str").getAsString();
	}

	public static void main(String[] args) throws IOException {
		Logger logger = Logger.getLogger(ElasticSearchConsumer.class.getName());

		RestHighLevelClient client = createClient();

		KafkaConsumer<String, String> consumer = createConsumer("assignment_topic");

		// poll for new data
		while (true) {
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100)); // new in kafka

			Integer recordCount = records.count();
			logger.info("Received" + recordCount + "records");

			BulkRequest bulkRequest = new BulkRequest();

			for (ConsumerRecord<String, String> record : records) {

				// // 1-kafka generic ID
				// String id= record.topic() + "_" + record.partition() + "_" + record.offset();
				//
				// 2- twitter feed specific id
				try {
					String id = extractIdFromTweets(record.value());

					// where we insert data into elasticSearch
					String jsonString = record.value();

					IndexRequest indexRequest = new IndexRequest("twitter", "tweets", "id" // this is to make our
																							// consumer
																							// idempotent
					).source(jsonString, XContentType.JSON);

					bulkRequest.add(indexRequest); // We add to our bulkrequest (take no time)
				} catch (NullPointerException e) {
					logger.info("skipping bad data" + record.value());
				}
			}
			if (recordCount > 0) {
				BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);

				logger.info("Committing offsets...");
				consumer.commitSync();
				logger.info("Offsets have been committed");
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		// close the client
		// client.close();
	}

}
