package dev.zen.story2script.api.service;

import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;
import dev.zen.story2script.api.dto.ConvertStreamEvent;

import java.util.function.Consumer;

public interface NovelToScreenplayService {

    ConvertResponse convert(ConvertRequest request);

    void convertStream(ConvertRequest request, Consumer<ConvertStreamEvent> eventConsumer);
}
