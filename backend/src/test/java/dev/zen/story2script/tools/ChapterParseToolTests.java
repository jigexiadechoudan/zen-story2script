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
}
