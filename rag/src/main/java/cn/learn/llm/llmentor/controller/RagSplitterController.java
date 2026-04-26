package cn.learn.llm.llmentor.controller;

import cn.learn.llm.llmentor.cleaner.DocumentCleaner;
import cn.learn.llm.llmentor.reader.DocumentReaderFactory;
import cn.learn.llm.llmentor.splitter.OverlapParagraphTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 文档分块控制器
 *
 * @author lianglei
 * @version 1.0
 * @date 2026/4/22 20:20
 */
@RestController
@RequestMapping("/rag")
public class RagSplitterController {

    @Autowired
    private DocumentReaderFactory documentReaderFactory;


    @GetMapping("/split")
    public List<Document> splitDocument(@RequestParam("path") String path) {
        List<Document> documents = null;
        try {
            documents = documentReaderFactory.read(new File(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Document document : documents) {
            System.out.println("before chunk : " + document.getText());
            System.out.println("");
            OverlapParagraphTextSplitter tokenTextSplitter = new OverlapParagraphTextSplitter(
                    100,
                    5);

            List<Document> chunkedDocuments = tokenTextSplitter.split(document);
            for (Document chunkedDocument : chunkedDocuments) {
                System.out.println("after chunk : " + chunkedDocument.getText());
                System.out.println("");
            }
            System.out.println("==============");
        }
        return documents;
    }


    @GetMapping("/readAndSplit")
    public List<Document> readAndSplit(@RequestParam("path") String path) {
        File files = new File(path);
        if (!files.exists() || !files.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是有效文件: " + path);
        }

        try {
            //1、文档读取
            List<Document> documents = documentReaderFactory.read(files);

            //2、文档清洗
            documents = DocumentCleaner.cleanDocuments(documents);

            //3、文档分块
//            documents = split(documents); //token分割
            OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(
                    // 每块最大字符数
                    400,
                    // 块之间重叠 100 字符
                    100
            );
            documents = splitter.split(documents);
            return documents;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 文档分割
     *
     * @param documents
     * @return
     */
    private List<Document> split(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }

        TokenTextSplitter splitter = new TokenTextSplitter(
                // 每块最多 600 tokens
                600,
                // 每块至少 400 字符再考虑断点
                300,
                // 太短的不做嵌入
                5,
                // 最多拆分8000块
                8000,
                // 保留句号、换行符
                true
        );

        return splitter.apply(documents);
    }

//    public static void main(String[] args) {
    //Spring AI Alibaba的递归分片
//        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(100);
//        List<String> chunks = splitter.splitText("""
//        《斗破苍穹》是中国网络作家天蚕土豆创作的玄幻小说，2009年4月14日起在起点中文网连载，2011年7月20日完结，首版由湖北少年儿童出版社出版。2010年7月，该作品部分章节被编为《废材当自强》由湖北少年儿童出版社出版 [22]。
//        小说以斗气大陆为背景，讲述天才少年萧炎从斗气尽失逐步成长为斗帝的历程，期间通过收集异火、修炼丹药突破困境，最终解开斗帝失踪之谜并前往大千世界 [23]。作品构建了炼药师体系、异火榜及天鼎榜等设定，其中炼药师需具备火木双属性斗气与灵魂感知力 [6]。
//        该小说全网点击量近100亿次，实体书累计销量超300万册，2017年7月荣登“2017猫片胡润原创文学IP价值榜”榜首 [13-14]。2020年8月被国家图书馆永久典藏并位列中国文化产业IP价值综合榜TOP50前五 [6]，其改编动画在腾讯视频创下2.6万热度值纪录，并推出盲盒、游戏等衍生品 [25]。幻维数码制作的动画年番《斗破苍穹》重现佛怒火莲等经典场景，多次入围华语剧集口碑榜前十 [24]。2025年1月入选“2024网络文学神作榜”，同年2月28日荣获2024阅文IP盛典20大荣耀IP [15-16]。2025年11月，上海金山区人民法院宣判国内首例AI著作权侵权案，用户擅自使用《斗破苍穹》角色“美杜莎”形象训练AI模型被判赔偿5万元 [26-29]。
//        """);
//        for (String chunk : chunks) {
//            System.out.println(chunk);
//            System.out.println("================");
//        }
//        chunks.forEach(System.out::println);

    //LangChain4J的语义分段
//        DocumentBySentenceSplitter splitter = new DocumentBySentenceSplitter(100, 10);
//
//        for (String textSegment : splitter.split("""
//                《斗破苍穹》是中国网络作家天蚕土豆创作的玄幻小说，2009年4月14日起在起点中文网连载，2011年7月20日完结，首版由湖北少年儿童出版社出版。2010年7月，该作品部分章节被编为《废材当自强》由湖北少年儿童出版社出版 [22]。
//                小说以斗气大陆为背景，讲述天才少年萧炎从斗气尽失逐步成长为斗帝的历程，期间通过收集异火、修炼丹药突破困境，最终解开斗帝失踪之谜并前往大千世界 [23]。作品构建了炼药师体系、异火榜及天鼎榜等设定，其中炼药师需具备火木双属性斗气与灵魂感知力 [6]。
//                该小说全网点击量近100亿次，实体书累计销量超300万册，2017年7月荣登“2017猫片胡润原创文学IP价值榜”榜首 [13-14]。2020年8月被国家图书馆永久典藏并位列中国文化产业IP价值综合榜TOP50前五 [6]，其改编动画在腾讯视频创下2.6万热度值纪录，并推出盲盒、游戏等衍生品 [25]。幻维数码制作的动画年番《斗破苍穹》重现佛怒火莲等经典场景，多次入围华语剧集口碑榜前十 [24]。2025年1月入选“2024网络文学神作榜”，同年2月28日荣获2024阅文IP盛典20大荣耀IP [15-16]。2025年11月，上海金山区人民法院宣判国内首例AI著作权侵权案，用户擅自使用《斗破苍穹》角色“美杜莎”形象训练AI模型被判赔偿5万元 [26-29]。
//                """))
//            System.out.println(textSegment);
//    }
}
