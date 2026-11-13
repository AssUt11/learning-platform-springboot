package com.learning.platform.ai.practice;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;

class PracticeQuestionParserTest {

    static final String QUESTIONS =
        "{\"questions\":[{\"stem\":\"事务的目标是什么？\",\"options\":[\"保证一致性\",\"修改端口\",\"改变文件名\",\"删除表\"],\"answer\":0,\"explanation\":\"事务维护一致性。\",\"reference\":\"保证一致性\"},{\"stem\":\"提交对应哪一步？\",\"options\":[\"放弃修改\",\"确认修改\",\"建立索引\",\"修改连接\"],\"answer\":1,\"explanation\":\"提交确认修改。\",\"reference\":\"确认修改\"}]}";

    @Test
    void validatesCompleteQuizAndHandlesMarkdown() {
        assertEquals(
            2,
            PracticeQuestionParser.parse(
                "```json\n" + QUESTIONS + "\n```",
                new ObjectMapper()
            ).size()
        );
    }

    @Test
    void refusesOutOfRangeAnswerAndTruncatedQuiz() {
        assertThrows(BusinessException.class, () ->
            PracticeQuestionParser.parse(
                QUESTIONS.replace("\"answer\":0", "\"answer\":4"),
                new ObjectMapper()
            )
        );
        assertThrows(BusinessException.class, () ->
            PracticeQuestionParser.parse(QUESTIONS.substring(0, 100), new ObjectMapper())
        );
    }

    @Test
    void removesSurplusClosingBraceButStillRejectsMissingContent() {
        String surplus = QUESTIONS.substring(0, QUESTIONS.length() - 2) + "}" + QUESTIONS.substring(QUESTIONS.length() - 2);
        assertEquals(2, PracticeQuestionParser.parse(surplus, new ObjectMapper()).size());
        assertThrows(BusinessException.class, () -> PracticeQuestionParser.parse(QUESTIONS.substring(0, QUESTIONS.length() - 2), new ObjectMapper()));
    }

    @Test
    void repairsMisplacedKeyQuoteWithoutChangingAnswersOrQuestionValues() {
        var malformed = QUESTIONS.replace("\"stem\":\"", "\"stem:\"");
        var parsed = PracticeQuestionParser.parse(malformed, new ObjectMapper());
        assertEquals("事务的目标是什么？", parsed.get(0).get("stem"));
        assertEquals(0, parsed.get(0).get("answer"));
        assertThrows(BusinessException.class, () -> PracticeQuestionParser.parse(malformed.replace("\"answer\":0", "\"answer\":9"), new ObjectMapper()));
    }

    @Test
    void ignoresOnlyTerminalChoicePunctuationAndStillRejectsParaphrases() {
        var questions = PracticeQuestionParser.parse(QUESTIONS.replace("保证一致性\"", "保证一致性。\""), new ObjectMapper()).subList(0, 1);
        var grounded = PracticeQuestionParser.citeEvidence(questions, "事务用于保证一致性，并在失败时回滚。");
        assertEquals("事务用于保证一致性，并在失败时回滚。", grounded.get(0).get("reference"));
        var paraphrased = PracticeQuestionParser.parse(QUESTIONS.replace("保证一致性\"", "保证数据的一致性。\""), new ObjectMapper()).subList(0, 1);
        assertThrows(BusinessException.class, () -> PracticeQuestionParser.citeEvidence(paraphrased, "事务用于保证一致性，并在失败时回滚。"));
        var punctuation = PracticeQuestionParser.parse(QUESTIONS.replace("保证一致性\"", "。\""), new ObjectMapper()).subList(0, 1);
        assertThrows(BusinessException.class, () -> PracticeQuestionParser.citeEvidence(punctuation, "事务用于保证一致性，并在失败时回滚。"));
    }

    @Test
    void attachesTheActualSentenceContainingTheAnswerAndRefusesAbsentAnswers() {
        var questions = PracticeQuestionParser.parse(QUESTIONS, new ObjectMapper());
        var grounded = PracticeQuestionParser.citeEvidence(questions, "事务用于保证一致性。提交表示确认修改。");
        assertEquals("事务用于保证一致性。", grounded.get(0).get("reference"));
        assertEquals("提交表示确认修改。", grounded.get(1).get("reference"));
        assertDoesNotThrow(() -> PracticeQuestionParser.requireGrounding(grounded, "事务用于保证一致性。提交表示确认修改。"));
        assertThrows(BusinessException.class, () -> PracticeQuestionParser.citeEvidence(questions, "完全无关的讲义内容。"));
    }

    @Test
    void refusesDuplicateOptionsAndEmptyExplanations() {
        assertThrows(BusinessException.class, () ->
            PracticeQuestionParser.parse(
                QUESTIONS.replace("修改端口", "保证一致性"),
                new ObjectMapper()
            )
        );
        assertThrows(BusinessException.class, () ->
            PracticeQuestionParser.parse(
                QUESTIONS.replace("事务维护一致性。", ""),
                new ObjectMapper()
            )
        );
    }

    @Test
    void rejectsInventedReferencesAndAnswersOutsideTheQuotedMaterial() {
        var questions = PracticeQuestionParser.parse(QUESTIONS, new ObjectMapper());
        assertDoesNotThrow(() ->
            PracticeQuestionParser.requireGrounding(questions, "保证一致性。确认修改。")
        );
        assertThrows(BusinessException.class, () ->
            PracticeQuestionParser.requireGrounding(questions, "其他课时的内容")
        );
        var wrong = new java.util.LinkedHashMap<>(questions.get(0));
        wrong.put("answer", 1);
        assertThrows(BusinessException.class, () ->
            PracticeQuestionParser.requireGrounding(java.util.List.of(wrong), "保证一致性。")
        );
    }
}
