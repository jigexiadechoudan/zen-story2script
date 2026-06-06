package dev.zen.story2script.api.dto;

public record ConvertStreamEvent(
        String type,
        String message,
        ConvertResponse data
) {

    public static ConvertStreamEvent status(String message) {
        return new ConvertStreamEvent("status", message, null);
    }

    public static ConvertStreamEvent step(String message) {
        return new ConvertStreamEvent("step", message, null);
    }

    public static ConvertStreamEvent result(ConvertResponse data) {
        return new ConvertStreamEvent("result", "", data);
    }

    public static ConvertStreamEvent error(String message) {
        return new ConvertStreamEvent("error", message, null);
    }
}
