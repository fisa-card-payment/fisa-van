package com.fisa.van.batch;

import com.fisa.van.domain.VanTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AcquisitionItemProcessor implements ItemProcessor<VanTransaction, String> {

    @Override
    public String process(VanTransaction tx) {
        // 카드번호 마스킹: 앞 6자리 + ****** + 뒤 4자리
        String cardNumber = tx.getCardNumber().replaceAll("-", "");
        String maskedCard = cardNumber.substring(0, 6)
                + "******"
                + cardNumber.substring(cardNumber.length() - 4);

        // CSV 한 줄 생성
        return String.join(",",
                tx.getRrn(),
                tx.getStan(),
                maskedCard,
                String.valueOf(tx.getAmount()),
                tx.getMerchantId(),
                tx.getCardCompany() != null ? tx.getCardCompany() : "UNKNOWN",
                tx.getApprovalCode() != null ? tx.getApprovalCode() : "",
                tx.getCreatedAt().toString()
        );
    }
}