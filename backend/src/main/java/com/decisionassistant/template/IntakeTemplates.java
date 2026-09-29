package com.decisionassistant.template;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.decision.DecisionTypes.TemplateKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IntakeTemplates {
    public record IntakeTemplate(TemplateKey key, String version, String label, List<String> uses,
                                 List<Integer> budgets, List<String> compatibility, List<String> facts,
                                 List<MinimumRequirements.MinimumRequirement> minimumRequirements) {}

    private static final List<IntakeTemplate> TEMPLATES = List.of(
            template(TemplateKey.LAPTOP, "노트북", List.of("학업", "사무", "게임", "창작"),
                    List.of(1000000, 1500000, 2000000, 3000000), List.of("Windows", "macOS"),
                    List.of("가격", "OS", "CPU/GPU", "메모리", "저장공간", "화면", "무게", "배터리")),
            template(TemplateKey.PHONE, "휴대폰", List.of("일상", "사진", "게임", "업무"),
                    List.of(500000, 1000000, 1500000, 2000000), List.of("Android", "iOS"),
                    List.of("가격", "OS", "화면", "주사율", "저장공간", "카메라", "배터리", "무게")),
            template(TemplateKey.TABLET, "태블릿", List.of("영상", "필기", "그림", "업무"),
                    List.of(300000, 600000, 1000000, 1500000), List.of("Android", "iPadOS", "Windows"),
                    List.of("가격", "OS", "화면", "주사율", "저장공간", "펜 지원", "무게")),
            template(TemplateKey.MONITOR, "모니터", List.of("사무", "게임", "영상", "디자인"),
                    List.of(200000, 400000, 700000, 1000000), List.of("HDMI", "DisplayPort", "USB-C"),
                    List.of("가격", "크기", "해상도", "주사율", "패널", "연결 단자")),
            template(TemplateKey.EARBUDS, "무선 이어폰", List.of("출퇴근", "운동", "통화", "음악"),
                    List.of(100000, 200000, 300000, 500000), List.of("Android", "iOS", "멀티포인트"),
                    List.of("가격", "형태", "ANC 지원", "배터리", "무게", "방수", "호환성", "전용 앱", "기기별 추가 기능")));

    private static IntakeTemplate template(TemplateKey key, String label, List<String> uses, List<Integer> budgets,
                                            List<String> compatibility, List<String> facts) {
        return new IntakeTemplate(key, "intake-v2", label, uses, budgets, compatibility, facts, MinimumRequirements.forCategory(key));
    }

    @GetMapping("/api/v1/intake-templates")
    public List<IntakeTemplate> list() { return TEMPLATES; }

    public IntakeTemplate required(TemplateKey key) {
        return TEMPLATES.stream().filter(item -> item.key() == key).findFirst()
                .orElseThrow(() -> new InvalidDecisionException(List.of("지원하는 제품군을 선택하세요.")));
    }

    public IntakeTemplate required(TemplateKey key, String version) {
        var current = required(key);
        if (current.version().equals(version)) return current;
        if ("intake-v1".equals(version)) return new IntakeTemplate(key, version, current.label(), current.uses(),
                current.budgets(), current.compatibility(),
                key == TemplateKey.PHONE || key == TemplateKey.TABLET
                        ? current.facts().stream().filter(name -> !name.equals("주사율")).toList() : current.facts(), List.of());
        throw new InvalidDecisionException(List.of("지원하는 템플릿 버전을 사용하세요."));
    }
}
