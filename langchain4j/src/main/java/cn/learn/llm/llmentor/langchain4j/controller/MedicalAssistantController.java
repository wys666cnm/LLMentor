package cn.learn.llm.llmentor.langchain4j.controller;

import cn.learn.llm.llmentor.langchain4j.service.MedicalPromptRoutingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/26 12:39
 */
@RestController
@RequestMapping("/medical")
public class MedicalAssistantController {

    @Autowired
    private MedicalPromptRoutingService medicalRoutingService;

    /**
     * 路由方法，根据问题类型将问题路由到相应的处理方法（通过调用大模型根据用户问题，进行提示词的选择）
     *
     * @param response
     * @param question
     * @return
     */
    @RequestMapping("/consultation")
    public Flux<String> medicalConsultation(HttpServletResponse response, @RequestParam String question) {
        // 设置编码格式
        response.setCharacterEncoding("UTF-8");

        String consultationType = medicalRoutingService.determineConsultationType(question);

        Flux<String> result;
        switch (consultationType.trim()) {
            case "DOCTOR":
                //医生提示词
                result = medicalRoutingService.doctorConsultation(question);
                break;
            case "PHARMACIST":
                //药品提示词
                result = medicalRoutingService.pharmacistConsultation(question);
                break;
            default:
                //默认会选择医生这个提示词
                result = medicalRoutingService.doctorConsultation(question);
        }
        return result;
    }
}
