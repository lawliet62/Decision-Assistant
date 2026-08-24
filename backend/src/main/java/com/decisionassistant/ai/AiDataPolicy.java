package com.decisionassistant.ai;

import com.decisionassistant.common.InvalidDecisionException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiDataPolicy {

    private final AiProperties properties;

    public AiDataPolicy(AiProperties properties) {
        this.properties = properties;
    }

    public boolean requiresSensitiveDataAcknowledgement() {
        return properties.usesGemini();
    }

    public void validateSensitiveDataAcknowledgement(boolean acknowledged) {
        if (requiresSensitiveDataAcknowledgement() && !acknowledged) {
            throw new InvalidDecisionException(List.of(
                    "Gemini API 전송 전 민감한 개인정보·금융 정보·비공개 업무 정보가 없음을 확인해야 합니다."
            ));
        }
    }

    public String dataNotice() {
        if (properties.usesGemini()) {
            return "현재 Decision 내용은 Google Gemini API로 전송됩니다. 무료 티어에서는 전송한 콘텐츠가 "
                    + "Google 제품 개선에 사용될 수 있습니다. 제품 정보와 일반 구매 메모만 보내고, "
                    + "개인 식별·금융·건강·비공개 업무 정보는 포함하지 마세요.";
        }
        return "AI 실행 시 현재 Decision의 context, Option 설명과 붙여 넣은 source text가 외부 provider로 전송됩니다.";
    }
}
