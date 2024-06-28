package kr.sparta.movieperonalize.recommand;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kr.sparta.movieperonalize.recommand.dto.MovieDto;
import kr.sparta.movieperonalize.recommand.enumtype.MovieCountry;
import kr.sparta.movieperonalize.recommand.enumtype.MovieGenre;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Service
public class RecommendService {
    @Value("${api.movie-info-api}")
    private String movieInfoApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final RedisTemplate<String,List<MovieDto>> redisTemplate;

    private WebClient webClient;
    private UriComponents movieInfoApiUriComponent;

    public RecommendService(WebClient.Builder webClientBuilder, RedisTemplate<String,List<MovieDto>> redisTemplate) {
        this.webClientBuilder = webClientBuilder;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    private void init() {
        this.webClient = webClientBuilder.build();
        this.movieInfoApiUriComponent = UriComponentsBuilder
                .fromUriString(movieInfoApiUrl)
                .path("/movies")
                .build();
    }

    public List<MovieDto> getMoviesByGenre(MovieGenre movieGenre) {

        final StopWatch stopWatch = new StopWatch();
        stopWatch.start("API - Redis 속도 비교");

        // 1. Redis에서 캐시 조회
        final String prefix = "movies:";
        final String key = prefix;

        List<MovieDto> movieDtos = redisTemplate.opsForValue().get(key);

        // 2. 캐시에 데이터가 없으면
        if(null == movieDtos) {
            //2-1. API 호출
            final String movieInfoByGenreUri = movieInfoApiUriComponent.expand(movieGenre).toUriString();

            movieDtos = webClient.get()
                .uri(movieInfoByGenreUri)
                .retrieve()
                .bodyToFlux(MovieDto.class)
                .retryWhen(Retry.backoff(3, Duration.ofMinutes(500)))
                .timeout(Duration.ofSeconds(3))
                .collectList()
                .block();

            //2-2. 응답 값 레디스에 저장
            if(null != movieDtos) {
                redisTemplate.opsForValue().setIfAbsent(key, movieDtos, 10, TimeUnit.SECONDS);
            }
        }

        movieDtos = movieDtos.stream().filter(movieDto -> movieDto.getGenre().contains(movieGenre.getKorean())).toList();

        stopWatch.stop();
        log.info(stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
        return movieDtos;
    }

    public Flux<MovieDto> getMoviesByCountry(MovieCountry country) {
        final String movieInfoByCountryUri = movieInfoApiUriComponent.expand(country).toUriString();

        return webClient.get()
                .uri(movieInfoByCountryUri)
                .retrieve()
                .bodyToFlux(MovieDto.class)
                .filter(movieDto -> movieDto.getCountry().contains(country.getCountry()))
                .retryWhen(Retry.backoff(3,Duration.ofMinutes(500)))
                .timeout(Duration.ofSeconds(3));
    }


}