package com.github.kafka.tutorial1;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {
    public static void main(String[] args) {
        new ConsumerDemoWithThread().run();
    }

    private ConsumerDemoWithThread(){
    }

    private void run() {
        Logger logger = LoggerFactory.getLogger(ConsumerDemoWithThread.class);

        String bootstrapServer = "127.0.0.1:9092";
        String groupId = "my-sixth-application";
        String topic = "first_topic";

        // latch for dealing with multiple threads
        CountDownLatch latch = new CountDownLatch(1);

        // create the consumer runnable
        logger.info("Creating the consumer thread");
        Runnable myConsumerRunnable = new ConsumerRunnable(bootstrapServer, topic, groupId, latch);

        // starts the thread
        Thread myThread = new Thread(myConsumerRunnable);
        myThread.start();

        // add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread( () -> {
            logger.info("Caught shutdown hook");
            ((ConsumerRunnable) myConsumerRunnable).shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("Application has exited");
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Application got interrupted",e);
        } finally {
            logger.info("Application is closing");
        }
    }

    private class ConsumerRunnable implements Runnable {

        private CountDownLatch latch;
        private KafkaConsumer<String, String> kafkaConsumer;
        private Logger logger = LoggerFactory.getLogger(ConsumerDemoWithThread.class);

        public ConsumerRunnable(
                String bootstrapServer,
                String topic,
                String groupId,
                CountDownLatch latch){

            this.latch = latch;

            // create consumer configs
            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            kafkaConsumer = new KafkaConsumer<String, String>(properties);

            kafkaConsumer.subscribe(Arrays.asList(topic));
        }

        public void run() {
            try {
                while(true) {
                    ConsumerRecords<String, String> records
                            = kafkaConsumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        logger.info("Key " + record.key() + ", Value: " + record.value());
                        logger.info("Partition " + record.partition() + ", Offset: " + record.offset());
                    }
                }
            } catch (WakeupException wakeupException){
                logger.info("Received shutdown signal");
            } finally {
                kafkaConsumer.close();
                latch.countDown();
            }
        }

        public void shutdown(){
            // interrupt consumer.poll();
            // it will throw the exception WakeUpException
            kafkaConsumer.wakeup();
        }
    }

}
