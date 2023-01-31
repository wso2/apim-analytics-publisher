package org.wso2.am.analytics.publisher.reporter.moesif;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.analytics.publisher.reporter.MetricEventBuilder;
import org.wso2.am.analytics.publisher.retriever.MoesifKeyRetriever;

import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Responsible for periodically calling the Moesif microservice and
 * refreshing the internal map.
 * This internally keeps a queue of {@link MoesifKeyRetriever} of the event,
 * that prevented publishing due to missing moesif key.
 * Along with the refresh of map, this enqueues the missing events to {@link EventQueue}.
 */
public class MissedEventHandler extends TimerTask {
    private static final Logger log = LoggerFactory.getLogger(MissedEventHandler.class);
    private final BlockingQueue<MetricEventBuilder> missedEventQueue = new LinkedBlockingQueue<>();
    MoesifKeyRetriever keyRetriever;

    public MissedEventHandler(MoesifKeyRetriever keyRetriever) {
        this.keyRetriever = keyRetriever;
    }

    public void putMissedEvent(MetricEventBuilder missedEvent) {
        try {
            missedEventQueue.put(missedEvent);
        } catch (InterruptedException e) {
            log.warn("Dropped event queueing failed. This event will not proceed", e);
        }
    }

    @Override
    public void run() {
        // refresh the internal map of orgID-MoesifKey
        keyRetriever.initOrRefreshOrgIDMoesifKeyMap();
        // dequeue all the event builders in missedEvent queue
        // to queue initiated at MoesifCounterMetric.
        for (MetricEventBuilder builder :
                missedEventQueue) {
            MoesifCounterMetric.queue.put(builder);
        }

    }
}
