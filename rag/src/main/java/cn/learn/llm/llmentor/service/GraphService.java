package cn.learn.llm.llmentor.service;

import cn.learn.llm.llmentor.model.DirectorMoviesDTO;
import cn.learn.llm.llmentor.repo.MovieGraphRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/27 11:18
 */
@Service
public class GraphService {

    @Autowired
    private MovieGraphRepository graphRepository;

    /**
     * 查询与给定电影同导演的其他电影
     *
     * @param movieName
     * @return
     */
    public String retrieveContext(String movieName) {
        List<DirectorMoviesDTO> results = graphRepository.findOtherMoviesBySameDirector(movieName);

        if (results.isEmpty()) {
            return "未找到导演过《" + movieName + "》的导演的其他作品。";
        }

        StringBuilder sb = new StringBuilder();
        for (DirectorMoviesDTO dto : results) {
            String director = dto.getDirector();
            List<String> movies = dto.getOtherMovies();
            sb.append(String.format("- 导演 %s 还执导了：%s\n", director, String.join("、", movies)));
        }
        return sb.toString().trim();
    }
}
