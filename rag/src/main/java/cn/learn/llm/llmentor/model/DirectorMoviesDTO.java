package cn.learn.llm.llmentor.model;

import java.util.List;

/**
 * @author lianglei
 * @version 1.0
 * @date 2026/4/27 11:09
 */
public class DirectorMoviesDTO {

    private String director;

    private List<String> otherMovies;

    public DirectorMoviesDTO() {
    }

    public DirectorMoviesDTO(String director, List<String> otherMovies) {
        this.director = director;
        this.otherMovies = otherMovies;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public List<String> getOtherMovies() {
        return otherMovies;
    }

    public void setOtherMovies(List<String> otherMovies) {
        this.otherMovies = otherMovies;
    }
}
