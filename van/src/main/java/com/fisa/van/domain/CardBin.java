package com.fisa.van.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity // 이 클래스가 DB 테이블과 매핑된다는 선언
@Table(name = "card_bins") // 매핑할 테이블 이름
@Getter
@NoArgsConstructor
public class CardBin {

    @Id // Primary Key
    @Column(length = 6) // 컬럼 세부 설정
    private String binPrefix;

    @Column(length = 20)
    private String companyName;

    @Column(length = 100)
    private String apiEndpoint;
}