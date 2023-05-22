package com.amyojiakor.nubangeneratorapp.services;

import com.amyojiakor.nubangeneratorapp.models.entities.NubanEntity;
import com.amyojiakor.nubangeneratorapp.models.payloads.BankDataDto;
import com.amyojiakor.nubangeneratorapp.models.payloads.NubanGeneratorPayload;
import com.amyojiakor.nubangeneratorapp.models.payloads.NubanGeneratorResponse;
import com.amyojiakor.nubangeneratorapp.repositories.NubanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class NubanGeneratorServiceImpl implements NubanGeneratorService{
    private static final long [] NUBAN_MULTIPLIERS = {3,7,3,3,7,3,3,7,3,3,7,3};
    private static final int MODULO_VALUE = 10;
    private static final long SERIAL_NUMBER_LENGTH = 9L;
    @Autowired
    private final NubanRepository nubanRepository;


    @Override
    public NubanGeneratorResponse generateNuban(NubanGeneratorPayload payload) throws Exception{
        
        String serialNum = payload.serialNum();
        String bankCode = payload.bankCode();
        
        if(!isBankCodeValid(bankCode)){
            throw new Exception("Invalid Bank Code. Please enter a valid bank code");
        }
        
        if(serialNum.length() > SERIAL_NUMBER_LENGTH ){
            throw new Exception("Serial Number should not be more than 9 digits.");
        }

        serialNum = serialNum.length()<SERIAL_NUMBER_LENGTH ? padSerialNumber(serialNum) : serialNum;

        long checkDigit = calculateNuban(bankCode, serialNum);

        String generatedNuban = serialNum + checkDigit;

        return setNubanEntityAndResponse(payload, generatedNuban);
    }

    private boolean isBankCodeValid(String bankCode) throws IOException {

        List<BankDataDto> bankCodesList = getBankAndCbnCodes();

        BankDataDto bank = bankCodesList.stream().filter(b -> b.uniqueCbnBankCode()
                        .equals(bankCode))
                .findFirst()
                .orElseThrow(() -> new IOException("Invalid Bank Code"));

        return bank != null;
    }


    private List<BankDataDto> getBankAndCbnCodes() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.readValue(
                new File("src/main/resources/bank_list.json"),
                new TypeReference<>() {
                }
        );
    }

    private String padSerialNumber(String serialNumber) {

        StringBuilder paddedSerialNumber = new StringBuilder();
        paddedSerialNumber.append(serialNumber);

        while (paddedSerialNumber.length() < SERIAL_NUMBER_LENGTH) {
            paddedSerialNumber.insert(0, 0);
        }

        return paddedSerialNumber.toString();
    }

    private long calculateNuban(String bankCode, String serialNumber){

        String bankCodeAndSerialNumber =  bankCode + serialNumber;

        var splitBankCodeAndSerialNumber  = bankCodeAndSerialNumber.toCharArray();

        long sum = IntStream.range(0, splitBankCodeAndSerialNumber.length)
                .mapToLong(i -> (splitBankCodeAndSerialNumber[i] - '0') * NUBAN_MULTIPLIERS[i])
                .sum();

        long result = sum % MODULO_VALUE;

        sum = 10 - result == 10 ? 0 : 10 - result;

        return sum;
    }


    private NubanGeneratorResponse setNubanEntityAndResponse (NubanGeneratorPayload payload, String generatedNuban){

        LocalDateTime now = LocalDateTime.now();

        NubanEntity nubanEntity = new NubanEntity();
        BeanUtils.copyProperties(payload, nubanEntity);
        nubanEntity.setGeneratedNuban(generatedNuban);
        nubanEntity.setDateTime(now);
        nubanRepository.save(nubanEntity);

        return new NubanGeneratorResponse(payload.bankCode(), payload.serialNum(), generatedNuban, now);
    }

}