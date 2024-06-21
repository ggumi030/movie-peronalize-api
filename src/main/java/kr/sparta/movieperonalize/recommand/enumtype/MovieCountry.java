package kr.sparta.movieperonalize.recommand.enumtype;

import lombok.Getter;

@Getter
public enum MovieCountry {
    AMERICA("미국"),
    KOREA("대한민국"),
    JAPAN("일본");

    private final String country;

    MovieCountry(String country){
        this.country = country;
    }
}
