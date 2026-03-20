package com.fisa.van.batch;

import com.fisa.van.domain.VanTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class AcquisitionJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AcquisitionItemReader reader;
    private final AcquisitionItemProcessor processor;
    private final AcquisitionItemWriter writer;
    private final JobLauncher jobLauncher;

    @Bean
    public Job acquisitionJob() {
        return new JobBuilder("acquisitionJob", jobRepository)
                .start(acquisitionStep())
                .build();
    }

    @Bean
    public Step acquisitionStep() {
        return new StepBuilder("acquisitionStep", jobRepository)
                .<VanTransaction, String>chunk(100, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    // 매일 자정 실행
    @Scheduled(cron = "0 0 0 * * *")
    public void runBatch() throws Exception {
        log.info("[BATCH] 매입 배치 시작");
        reader.reset();

        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(acquisitionJob(), params);
    }
}