package com.fisa.van.batch;

import com.fisa.van.domain.AcquisitionBatch;
import com.fisa.van.repository.AcquisitionBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcquisitionItemWriter implements ItemWriter<String> {

    private final AcquisitionBatchRepository acquisitionBatchRepository;
    private final RestTemplate restTemplate;

    @Value("${batch.csv.path:./csv/}")
    private String csvPath;

    @Value("${batch.gateway.url:http://localhost:8080/api/batch/acquire}")
    private String gatewayUrl;

    @Override
    public void write(Chunk<? extends String> chunk) throws IOException {
        String fileName = "acquisition_"
                + LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ".csv";

        File dir = new File(csvPath);
        if (!dir.exists()) dir.mkdirs();

        File csvFile = new File(csvPath + fileName);

        // CSV 파일 생성
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile, true))) {
            // 헤더 (파일 새로 만들 때만)
            if (csvFile.length() == 0) {
                writer.write("RRN,STAN,CARD_NUMBER,AMOUNT,MERCHANT_ID,CARD_COMPANY,APPROVAL_CODE,CREATED_AT");
                writer.newLine();
            }
            for (String line : chunk.getItems()) {
                writer.write(line);
                writer.newLine();
            }
        }

        log.info("[BATCH] CSV 작성 완료: {}", fileName);

        // 배치 기록 저장
        AcquisitionBatch batch = AcquisitionBatch.builder()
                .batchDate(LocalDate.now().minusDays(1))
                .fileName(fileName)
                .totalCount(chunk.getItems().size())
                .status("SENT")
                .build();

        acquisitionBatchRepository.save(batch);

        sendCsvToCardCompany(fileName);
    }

    // CSV 파일 카드사로 전송
    public void sendCsvToCardCompany(String fileName) {
        File csvFile = new File(csvPath + fileName);
        String batchDate = LocalDate.now().minusDays(1).toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(csvFile));
        // batchDate body에서 제거

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // batchDate를 QueryParam으로
        String url = gatewayUrl + "?batchDate=" + batchDate;

        try {
            restTemplate.postForObject(url, requestEntity, String.class);
            log.info("[BATCH] CSV 전송 완료: {}", fileName);
        } catch (Exception e) {
            log.error("[BATCH] CSV 전송 실패: {}", e.getMessage());
        }
    }
}