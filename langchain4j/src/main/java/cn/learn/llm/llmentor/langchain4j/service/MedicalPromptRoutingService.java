package cn.learn.llm.llmentor.langchain4j.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 12:19
 */
@AiService
public interface MedicalPromptRoutingService {

    /**
     * 医生咨询服务，提供专业的医疗建议
     *
     * @param userMessage
     * @return
     */
    @SystemMessage("你是一个专业的医生，可以从专业的医疗角度给出患者建议。")
    Flux<String> doctorConsultation(String userMessage);

    /**
     * 药师咨询服务，提供专业的用药建议
     *
     * @param userMessage
     * @return
     */
    @SystemMessage("你是一个专业的药学专家，掌握丰富的药品知识，能够在用药方面给出更好的建议。")
    Flux<String> pharmacistConsultation(String userMessage);

    /**
     * 确定咨询类型，判断用户是需要医生咨询还是药师咨询
     *
     * @param userMessage
     * @return
     */
    @SystemMessage("你需要判断用户的询问是关于病情咨询还是用药建议。如果是询问病情、症状、诊断相关的问题，回答'DOCTOR'。如果是询问药物、用药方法、药物副作用相关的问题，回答'PHARMACIST'。只回答DOCTOR或PHARMACIST，不要其他内容。")
    String determineConsultationType(String userMessage);
}
