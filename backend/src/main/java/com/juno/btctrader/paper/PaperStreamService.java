package com.juno.btctrader.paper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class PaperStreamService {
    private static final Logger logger = LoggerFactory.getLogger(PaperStreamService.class);
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long DEFAULT_TIMEOUT = 60 * 60 * 1000L; // 1 hour

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        logger.info("SSE: new subscriber for user {} (total={})", userId, emitters.get(userId).size());

        emitter.onCompletion(() -> {
            removeEmitter(userId, emitter);
            logger.info("SSE: completed for user {}", userId);
        });
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            logger.info("SSE: timeout for user {}", userId);
        });
        emitter.onError((e) -> {
            removeEmitter(userId, emitter);
            logger.warn("SSE: error for user {}: {}", userId, e.getMessage());
        });

        return emitter;
    }

    public void send(String userId, PaperSummary summary) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            logger.debug("SSE: no subscribers for user {}, skipping send", userId);
            return;
        }
        logger.info("SSE: sending summary to user {} to {} subscribers", userId, list.size());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("summary").data(summary));
            } catch (IOException e) {
                logger.warn("SSE: failed to send to user {}: {}", userId, e.getMessage());
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(userId);
    }
}