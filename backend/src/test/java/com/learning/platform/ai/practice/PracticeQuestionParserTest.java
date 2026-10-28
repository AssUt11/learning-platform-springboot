package com.learning.platform.ai.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeQuestionParserTest {
    static final String QUESTIONS = "{\"questions\":[{\"stem\":\"事务的目标是什么？\",\"options\":[\"保证一致性\",\"修改端口\",\"改变文件名\",\"删除表\"],\"answer\":0,\"explanation\":\"事务维护一致性。\",\"reference\":\"保证一致性\"},{\"stem\":\"提交对应哪一步？\",\"options\":[\"放弃修改\",\"确认修改\",\"建立索引\",\"修改连接\"],\"answer\":1,\"explanation\":\"提交确认修改。\",\"reference\":\"确认修改\"}]}";
    @Test void validatesCompleteQuizAndHandlesMarkdown() {
        assertEquals(2,PracticeQuestionParser.parse("```json\n"+QUESTIONS+"\n```",new ObjectMapper()).size());
    }
    @Test void refusesOutOfRangeAnswerAndTruncatedQuiz() {
        assertThrows(BusinessException.class,()->PracticeQuestionParser.parse(QUESTIONS.replace("\"answer\":0","\"answer\":4"),new ObjectMapper()));
        assertThrows(BusinessException.class,()->PracticeQuestionParser.parse(QUESTIONS.substring(0,100),new ObjectMapper()));
    }
    @Test void refusesDuplicateOptionsAndEmptyExplanations() {
        assertThrows(BusinessException.class,()->PracticeQuestionParser.parse(QUESTIONS.replace("修改端口","保证一致性"),new ObjectMapper()));
        assertThrows(BusinessException.class,()->PracticeQuestionParser.parse(QUESTIONS.replace("事务维护一致性。",""),new ObjectMapper()));
    }
    @Test void rejectsInventedReferencesAndAnswersOutsideTheQuotedMaterial() {
        var questions = PracticeQuestionParser.parse(QUESTIONS,new ObjectMapper());
        assertDoesNotThrow(()->PracticeQuestionParser.requireGrounding(questions,"保证一致性。确认修改。"));
        assertThrows(BusinessException.class,()->PracticeQuestionParser.requireGrounding(questions,"其他课时的内容"));
        var wrong = new java.util.LinkedHashMap<>(questions.get(0)); wrong.put("answer",1);
        assertThrows(BusinessException.class,()->PracticeQuestionParser.requireGrounding(java.util.List.of(wrong),"保证一致性。"));
    }
}
