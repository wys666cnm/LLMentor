package cn.learn.llm.llmentor.repo;

import cn.learn.llm.llmentor.model.DirectorMoviesDTO;
import cn.learn.llm.llmentor.model.Movie;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieGraphRepository extends Neo4jRepository<Movie, String> {

    //    @Query("""
//            MATCH (m:Movie {title: $title}) <-[:DIRECTED]- (d:Director) -[:DIRECTED]-> (other:Movie)
//            WHERE other.title <> $title
//            RETURN d.name AS director, collect(other.title + ' (' + other.year + ')') AS otherMovies
//            """)
    @Query("""
            MATCH (m:Movie {title: $title})<-[:DIRECTED]-(d:Director)
            MATCH (d)-[:DIRECTED]->(allMovie:Movie)
            RETURN
                d.name AS director,
                collect(allMovie.title + ' (' + allMovie.year + ')') AS otherMovies
            """)
    List<DirectorMoviesDTO> findOtherMoviesBySameDirector(String title);

}
