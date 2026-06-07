package dev.zen.story2script.api.controller;

import dev.zen.story2script.api.dto.AssistantChatRequest;
import dev.zen.story2script.api.dto.AssistantChatResponse;
import dev.zen.story2script.api.dto.AssistantRefineInputRequest;
import dev.zen.story2script.api.dto.AssistantRefineInputResponse;
import dev.zen.story2script.api.service.InputAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
class InputAssistantController {

    private final InputAssistantService service;

    InputAssistantController(InputAssistantService service) {
        this.service = service;
    }

    @PostMapping("/refine-input")
    AssistantRefineInputResponse refineInput(@Valid @RequestBody AssistantRefineInputRequest request) {
        return service.refine(request);
    }

    @PostMapping("/chat")
    AssistantChatResponse chat(@RequestBody AssistantChatRequest request) {
        return service.chat(request);
    }
}
