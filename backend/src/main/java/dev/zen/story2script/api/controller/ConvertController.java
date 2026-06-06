package dev.zen.story2script.api.controller;

import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;
import dev.zen.story2script.api.service.NovelToScreenplayService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/convert")
class ConvertController {

    private final NovelToScreenplayService service;

    ConvertController(NovelToScreenplayService service) {
        this.service = service;
    }

    @PostMapping
    ConvertResponse convert(@Valid @RequestBody ConvertRequest request) {
        return service.convert(request);
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter convertStream(@Valid @RequestBody ConvertRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> {
            try {
                service.convertStream(request, event -> send(emitter, event.type(), event));
                emitter.complete();
            } catch (RuntimeException ex) {
                send(emitter, "error", ex.getMessage());
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to send convert stream event.", ex);
        }
    }
}
