package dev.zen.story2script.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterParseToolTests {

    private final ChapterParseTool tool = new ChapterParseTool();

    @Test
    void parsesArabicChineseAndEnglishChapterHeadings() {
        String sourceText = """
                第1章 归来
                林夏回到雾镇。

                第一章 旧信
                她发现匿名信。

                Chapter 1 The Station
                陈默出现在站台。
                """;

        ChapterParseTool.ChapterParseOutput output = tool.parse(new ChapterParseTool.ChapterParseInput(sourceText));

        assertThat(output.valid()).isTrue();
        assertThat(output.errorMessage()).isBlank();
        assertThat(output.chapters())
                .extracting(ChapterParseTool.ParsedChapter::heading)
                .containsExactly("第1章 归来", "第一章 旧信", "Chapter 1 The Station");
        assertThat(output.chapters().get(0).content()).contains("林夏回到雾镇");
    }

    @Test
    void fewerThanThreeChaptersReturnsClearError() {
        String sourceText = """
                第1章 归来
                林夏回到雾镇。

                Chapter 2 Letter
                她发现匿名信。
                """;

        ChapterParseTool.ChapterParseOutput output = tool.parse(new ChapterParseTool.ChapterParseInput(sourceText));

        assertThat(output.valid()).isFalse();
        assertThat(output.errorMessage())
                .contains("at least 3 chapters")
                .contains("found 2")
                .contains("第1章")
                .contains("第一章")
                .contains("Chapter 1");
        assertThat(output.chapters()).hasSize(2);
    }

    @Test
    void parsesMarkdownChineseChapterHeadings() {
        String sourceText = """
                # \u7b2c\u4e00\u7ae0 \u5f52\u6765
                \u6797\u590f\u56de\u5230\u96fe\u9547\u3002
                ## \u7b2c\u4e8c\u7ae0 \u65e7\u4fe1
                \u5979\u53d1\u73b0\u533f\u540d\u4fe1\u3002
                ### \u7b2c\u4e09\u7ae0 \u8f66\u7ad9
                \u9648\u9ed8\u51fa\u73b0\u5728\u7ad9\u53f0\u3002
                """;

        ChapterParseTool.ChapterParseOutput output = tool.parse(new ChapterParseTool.ChapterParseInput(sourceText));

        assertThat(output.valid()).isTrue();
        assertThat(output.chapters())
                .extracting(ChapterParseTool.ParsedChapter::heading)
                .containsExactly(
                        "\u7b2c\u4e00\u7ae0 \u5f52\u6765",
                        "\u7b2c\u4e8c\u7ae0 \u65e7\u4fe1",
                        "\u7b2c\u4e09\u7ae0 \u8f66\u7ad9"
                );
        assertThat(output.chapters().get(0).content()).contains("\u6797\u590f\u56de\u5230\u96fe\u9547");
    }
}
